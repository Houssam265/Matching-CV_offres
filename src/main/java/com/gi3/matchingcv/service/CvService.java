package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.CvDto;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Collator;
import java.util.*;

/** Construit un CV structuré (export / impression) à partir du profil de l'étudiant. */
@Service
@Transactional(readOnly = true)
public class CvService {

    private final EtudiantRepository etudiants;
    private final AccountProfileRepository profils;
    private final ProjetRepository projets;
    private final ExperienceProfessionnelleRepository experiences;
    private final CandidatureRepository candidatures;

    public CvService(EtudiantRepository etudiants, AccountProfileRepository profils, ProjetRepository projets,
                     ExperienceProfessionnelleRepository experiences, CandidatureRepository candidatures) {
        this.etudiants = etudiants;
        this.profils = profils;
        this.projets = projets;
        this.experiences = experiences;
        this.candidatures = candidatures;
    }

    /** CV de l'étudiant connecté, avec son email pour pouvoir le réutiliser ailleurs. */
    public CvDto pourEtudiant(Long etudiantId) {
        Etudiant e = etudiants.findById(etudiantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Le CV est réservé aux comptes candidats."));
        return construire(e, true);
    }

    /** CV d'un candidat consulté par le recruteur propriétaire de l'offre (sans email). */
    public CvDto pourRecruteur(Long candidatureId, Long recruteurId) {
        Candidature c = candidatures.findById(candidatureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature introuvable."));
        Recruteur r = c.getOffre().getRecruteur();
        if (r == null || !r.getId().equals(recruteurId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cette candidature concerne une autre entreprise.");
        return construire(c.getEtudiant(), false);
    }

    private CvDto construire(Etudiant e, boolean inclureEmail) {
        AccountProfile p = profils.findById(e.getId()).orElseGet(AccountProfile::new);
        List<Projet> listeProjets = projets.findByEtudiantId(e.getId());
        List<ExperienceProfessionnelle> listeExperiences = new ArrayList<>(experiences.findByEtudiantId(e.getId()));
        // Poste actuel d'abord, puis du plus récent au plus ancien.
        listeExperiences.sort(Comparator
                .comparing((ExperienceProfessionnelle x) -> x.getDateFin() != null)
                .thenComparing(ExperienceProfessionnelle::getDateDebut, Comparator.nullsLast(Comparator.reverseOrder())));

        Collator collator = Collator.getInstance(Locale.FRENCH);
        collator.setStrength(Collator.PRIMARY);
        Map<String, Set<String>> parCategorie = new TreeMap<>(collator);
        java.util.function.Consumer<Competence> ajouter = c -> {
            if (c.getStatut() != null && c.getStatut() != StatutCompetence.VALIDEE) return; // CV : compétences validées uniquement
            String cat = c.getCategorie() == null || c.getCategorie().isBlank() ? "Autres" : c.getCategorie().strip();
            parCategorie.computeIfAbsent(cat, k -> new TreeSet<>(collator)).add(c.getNom());
        };
        listeExperiences.forEach(x -> x.getCompetences().forEach(ajouter));
        listeProjets.forEach(x -> x.getCompetences().forEach(ajouter));
        e.getCompetences().forEach(ajouter);
        List<CvDto.GroupeCompetences> groupes = new ArrayList<>();
        parCategorie.forEach((cat, noms) -> { if (!"Autres".equals(cat)) groupes.add(new CvDto.GroupeCompetences(cat, List.copyOf(noms))); });
        if (parCategorie.containsKey("Autres")) groupes.add(new CvDto.GroupeCompetences("Autres", List.copyOf(parCategorie.get("Autres"))));

        List<CvDto.Lien> liens = new ArrayList<>();
        if (nonVide(p.getProfessionalUrl())) {
            boolean linkedin = p.getProfessionalUrl().toLowerCase(Locale.ROOT).contains("linkedin.");
            liens.add(new CvDto.Lien(linkedin ? "linkedin" : "web", linkedin ? "LinkedIn" : "Profil professionnel", p.getProfessionalUrl()));
        }
        if (nonVide(p.getGithubUrl())) liens.add(new CvDto.Lien("github", "GitHub", p.getGithubUrl()));
        if (nonVide(p.getPortfolioUrl())) liens.add(new CvDto.Lien("portfolio", "Portfolio", p.getPortfolioUrl()));

        String titre = nonVide(p.getHeadline()) ? p.getHeadline() : (nonVide(e.getFiliere()) ? "Étudiant·e en " + e.getFiliere() : null);

        return new CvDto(
                e.getId(), e.getPrenom(), e.getNom(), titre, p.getBiography(), p.getAvailability(),
                inclureEmail ? (nonVide(p.getContactEmail()) ? p.getContactEmail() : e.getEmail()) : null,
                inclureEmail && nonVide(p.getTelephone()) ? p.getTelephone() : null,
                p.getVille(),
                liens,
                new CvDto.Formation(e.getFiliere(), e.getEtablissement(), p.getFormationPeriode()),
                groupes,
                listeExperiences.stream().map(x -> new CvDto.Experience(x.getPoste(), x.getEntreprise(), x.getDateDebut(), x.getDateFin(),
                        noms(x.getCompetences()))).toList(),
                listeProjets.stream().map(x -> new CvDto.Projet(x.getTitre(), x.getDescription(), noms(x.getCompetences()))).toList(),
                lignes(p.getLangues()),
                lignes(p.getInterets())
        );
    }

    private static List<String> noms(List<Competence> competences) {
        return competences.stream()
                .filter(c -> c.getStatut() == null || c.getStatut() == StatutCompetence.VALIDEE)
                .map(Competence::getNom).distinct().toList();
    }

    /** Une entrée par ligne (ou séparée par « ; »), sans doublon ni ligne vide. */
    private static List<String> lignes(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split("[\r\n;]+")).map(String::strip).filter(x -> !x.isEmpty()).distinct().limit(20).toList();
    }

    private static boolean nonVide(String s) {
        return s != null && !s.isBlank();
    }
}
