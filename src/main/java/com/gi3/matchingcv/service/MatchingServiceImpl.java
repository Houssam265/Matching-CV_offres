package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.*;
import com.gi3.matchingcv.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class MatchingServiceImpl implements MatchingService {

    private final EtudiantRepository etudiantRepository;
    private final OffreRepository offreRepository;
    private final ProjetRepository projetRepository;
    private final ExperienceProfessionnelleRepository experienceRepository;

    public MatchingServiceImpl(
            EtudiantRepository etudiantRepository,
            OffreRepository offreRepository,
            ProjetRepository projetRepository,
            ExperienceProfessionnelleRepository experienceRepository
    ) {
        this.etudiantRepository = etudiantRepository;
        this.offreRepository = offreRepository;
        this.projetRepository = projetRepository;
        this.experienceRepository = experienceRepository;
    }

    @Override
    public MatchingDetailDto calculerScore(Long etudiantId, Long offreId) {
        if (etudiantId == null) {
            throw new IllegalArgumentException("L'identifiant de l'étudiant est obligatoire.");
        }
        if (offreId == null) {
            throw new IllegalArgumentException("L'identifiant de l'offre est obligatoire.");
        }

        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new EntityNotFoundException("Étudiant introuvable avec l'identifiant : " + etudiantId));
        Offre offre = offreRepository.findById(offreId)
                .orElseThrow(() -> new EntityNotFoundException("Offre introuvable avec l'identifiant : " + offreId));

        List<Projet> projets = projetRepository.findByEtudiantId(etudiantId);
        List<ExperienceProfessionnelle> experiences = experienceRepository.findByEtudiantId(etudiantId);

        List<CompetenceDetailScoreDto> competencesDetails = new ArrayList<>();

        for (OffreCompetence oc : offre.getOffreCompetences()) {
            Competence compOffre = oc.getCompetence();
            TypeExigence exigence = oc.getTypeExigence();
            StatutCompetence statutOffreComp = compOffre.getStatut();

            // Règle : Seules les compétences VALIDEE comptent côté offre
            if (statutOffreComp != StatutCompetence.VALIDEE) {
                competencesDetails.add(new CompetenceDetailScoreDto(
                        compOffre.getId(),
                        compOffre.getNom(),
                        exigence,
                        statutOffreComp,
                        "OFFRE_NON_VALIDEE",
                        "Non validée (offre)",
                        0.0,
                        false,
                        statutOffreComp == StatutCompetence.EN_ATTENTE
                                ? "Compétence en attente de validation côté offre (ignorée)"
                                : "Compétence rejetée côté offre (ignorée)",
                        false
                ));
                continue;
            }

            // Chercher la compétence chez l'étudiant selon la meilleure provenance
            // Règle : Seules les compétences VALIDEE côté étudiant comptent
            boolean possedee = false;
            String provenance = "AUCUNE";
            String provenanceLabel = "Absente";
            double niveau = 0.0;

            // 1. Vérifier si présente dans une expérience avec statut VALIDEE
            double maxDureeMois = -1.0;
            boolean dansExperience = false;
            for (ExperienceProfessionnelle exp : experiences) {
                for (Competence c : exp.getCompetences()) {
                    if (correspond(c, compOffre) && c.getStatut() == StatutCompetence.VALIDEE) {
                        dansExperience = true;
                        double duree = calculerDureeMois(exp);
                        if (duree > maxDureeMois) {
                            maxDureeMois = duree;
                        }
                    }
                }
            }

            if (dansExperience) {
                possedee = true;
                provenance = "EXPERIENCE";
                provenanceLabel = String.format(Locale.ROOT, "Expérience (%.1f mois)", maxDureeMois);
                // N = 0.8 + 0.2 * min(1, duree_en_mois / 60) — durée en jours / 30,44
                niveau = 0.8 + 0.2 * Math.min(1.0, maxDureeMois / 60.0);
            } else {
                // 2. Vérifier si présente dans un projet avec statut VALIDEE
                boolean dansProjet = projets.stream()
                        .flatMap(p -> p.getCompetences().stream())
                        .anyMatch(c -> correspond(c, compOffre) && c.getStatut() == StatutCompetence.VALIDEE);

                if (dansProjet) {
                    possedee = true;
                    provenance = "PROJET";
                    provenanceLabel = "Projet";
                    niveau = 0.7;
                } else {
                    // 3. Vérifier si présente dans la liste personnelle avec statut VALIDEE
                    boolean dansListePerso = etudiant.getCompetences().stream()
                            .anyMatch(c -> correspond(c, compOffre) && c.getStatut() == StatutCompetence.VALIDEE);

                    if (dansListePerso) {
                        possedee = true;
                        provenance = "DECLARATIF";
                        provenanceLabel = "Liste personnelle seule";
                        niveau = 0.4;
                    } else {
                        // 4. Vérifier si l'étudiant l'a mais avec statut EN_ATTENTE ou REJETEE
                        boolean enAttenteEtudiant = estEnAttenteOuRejeteeChezEtudiant(etudiant, projets, experiences, compOffre);
                        if (enAttenteEtudiant) {
                            provenance = "EN_ATTENTE";
                            provenanceLabel = "En attente de validation";
                            niveau = 0.0;
                        } else {
                            provenance = "AUCUNE";
                            provenanceLabel = "Absente";
                            niveau = 0.0;
                        }
                    }
                }
            }

            // Explication de la prise en compte
            boolean comptee;
            String raison;
            if (exigence == TypeExigence.REQUISE) {
                comptee = true;
                if (niveau > 0.0) {
                    raison = String.format(Locale.ROOT, "Compétence requise acquise (N = %.2f via %s)", niveau, provenanceLabel);
                } else if ("EN_ATTENTE".equals(provenance)) {
                    raison = "Compétence requise en attente de validation dans votre profil (non comptabilisée)";
                } else {
                    raison = "Compétence requise non renseignée dans votre profil (comptabilisée à 0)";
                }
            } else {
                // ATOUT : une ATOUT absente n'est jamais pénalisante
                if (niveau > 0.0) {
                    comptee = true;
                    raison = String.format(Locale.ROOT, "Atout maîtrisé et valorisé (N = %.2f via %s)", niveau, provenanceLabel);
                } else {
                    comptee = false;
                    if ("EN_ATTENTE".equals(provenance)) {
                        raison = "Atout en attente de validation dans votre profil (ignoré, non pénalisant)";
                    } else {
                        raison = "Atout non possédé (non pénalisant, non comptabilisé)";
                    }
                }
            }

            competencesDetails.add(new CompetenceDetailScoreDto(
                    compOffre.getId(),
                    compOffre.getNom(),
                    exigence,
                    statutOffreComp,
                    provenance,
                    provenanceLabel,
                    arrondir(niveau, 2),
                    comptee,
                    raison,
                    possedee
            ));
        }

        // Calculs des scores REQUISE et ATOUT
        List<CompetenceDetailScoreDto> requisesValidees = competencesDetails.stream()
                .filter(c -> c.typeExigence() == TypeExigence.REQUISE && c.statut() == StatutCompetence.VALIDEE)
                .toList();

        List<CompetenceDetailScoreDto> atoutsValides = competencesDetails.stream()
                .filter(c -> c.typeExigence() == TypeExigence.ATOUT && c.statut() == StatutCompetence.VALIDEE)
                .toList();

        String nomEntreprise = offre.getRecruteur() != null ? offre.getRecruteur().getNomEntreprise() : "";

        // Règle : Si l'offre n'a aucune REQUISE validée : "score indisponible"
        if (requisesValidees.isEmpty()) {
            return new MatchingDetailDto(
                    etudiantId,
                    offreId,
                    offre.getTitre(),
                    nomEntreprise,
                    null,
                    false,
                    "Score indisponible : l'offre ne comporte aucune compétence requise validée.",
                    null,
                    null,
                    null,
                    null,
                    0,
                    atoutsValides.size(),
                    competencesDetails
            );
        }

        // Coefficients : si aucun ATOUT validé, la part REQUISE compte pour 100 au lieu de 80
        double coefRequises = atoutsValides.isEmpty() ? 100.0 : 80.0;
        double coefAtouts = atoutsValides.isEmpty() ? 0.0 : 20.0;

        // Score_requises = moyenne des N sur les compétences REQUISE de l'offre (une absente compte 0) × coefRequises
        double sommeNRequises = requisesValidees.stream()
                .mapToDouble(CompetenceDetailScoreDto::niveau)
                .sum();
        double scoreRequises = (sommeNRequises / requisesValidees.size()) * coefRequises;

        // Score_atouts = (somme N des ATOUT possédés / nombre total d'ATOUTS VALIDEE) × 20
        // Un atout absent n'est jamais pénalisant ; un atout possédé ajoute un bonus proportionnel.
        List<CompetenceDetailScoreDto> atoutsPossedes = atoutsValides.stream()
                .filter(a -> a.niveau() != null && a.niveau() > 0.0)
                .toList();

        double scoreAtouts = 0.0;
        if (!atoutsPossedes.isEmpty() && coefAtouts > 0.0) {
            double sommeNAtouts = atoutsPossedes.stream()
                    .mapToDouble(CompetenceDetailScoreDto::niveau)
                    .sum();
            // Diviseur = nombre total d'Atouts VALIDEE (pas seulement ceux possédés)
            scoreAtouts = (sommeNAtouts / atoutsValides.size()) * coefAtouts;
        }

        // Score = min(100, somme)
        double scoreFinal = Math.min(100.0, scoreRequises + scoreAtouts);

        return new MatchingDetailDto(
                etudiantId,
                offreId,
                offre.getTitre(),
                nomEntreprise,
                arrondir(scoreFinal, 1),
                true,
                "Score calculé en temps réel selon les règles de matching CV.",
                arrondir(scoreRequises, 1),
                arrondir(scoreAtouts, 1),
                coefRequises,
                coefAtouts,
                requisesValidees.size(),
                atoutsValides.size(),
                competencesDetails
        );
    }

    @Override
    public List<OffreRecommandeeDto> recommanderOffres(Long etudiantId, OffreFiltreDto filtres) {
        if (etudiantId == null) {
            throw new IllegalArgumentException("L'identifiant de l'étudiant est obligatoire.");
        }
        if (!etudiantRepository.existsById(etudiantId)) {
            throw new EntityNotFoundException("Étudiant introuvable avec l'identifiant : " + etudiantId);
        }

        String domaine = filtres != null ? filtres.domaine() : null;
        String localisation = filtres != null ? filtres.localisation() : null;
        TypeContrat typeContrat = filtres != null ? filtres.typeContrat() : null;

        // Offres ACTIVES uniquement
        List<Offre> offresActives = offreRepository.findByStatut(StatutOffre.ACTIVE);

        return offresActives.stream()
                .filter(o -> correspondFiltreTexte(o.getDomaine(), domaine))
                .filter(o -> correspondFiltreTexte(o.getLocalisation(), localisation))
                .filter(o -> typeContrat == null || o.getTypeContrat() == typeContrat)
                .map(o -> {
                    MatchingDetailDto detail = calculerScore(etudiantId, o.getId());
                    OffreDto dto = OffreDto.from(o);
                    return new OffreRecommandeeDto(
                            o.getId(),
                            o.getTitre(),
                            o.getDescription(),
                            o.getDomaine(),
                            o.getLocalisation(),
                            o.getTypeContrat(),
                            o.getStatut(),
                            o.getDatePublication(),
                            dto.entreprise(),
                            detail.score(),
                            detail.scoreDisponible(),
                            detail,
                            dto.competences()
                    );
                })
                .sorted(Comparator
                        .comparing(OffreRecommandeeDto::score, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(OffreRecommandeeDto::datePublication, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Override
    public List<OffreRecommandeeDto> recommanderOffres(Long etudiantId, String domaine, String localisation, TypeContrat typeContrat) {
        return recommanderOffres(etudiantId, new OffreFiltreDto(domaine, localisation, typeContrat));
    }

    private boolean correspond(Competence c1, Competence c2) {
        if (c1 == null || c2 == null) return false;
        if (c1.getId() != null && c2.getId() != null && c1.getId().equals(c2.getId())) {
            return true;
        }
        String n1 = c1.getNomNormalise() != null ? c1.getNomNormalise() : Competence.normaliserNom(c1.getNom());
        String n2 = c2.getNomNormalise() != null ? c2.getNomNormalise() : Competence.normaliserNom(c2.getNom());
        return n1 != null && n1.equalsIgnoreCase(n2);
    }

    private double calculerDureeMois(ExperienceProfessionnelle exp) {
        if (exp.getDateDebut() == null) return 0.0;
        LocalDate fin = exp.getDateFin() != null ? exp.getDateFin() : LocalDate.now();
        if (fin.isBefore(exp.getDateDebut())) {
            return 0.0;
        }
        long jours = ChronoUnit.DAYS.between(exp.getDateDebut(), fin);
        return Math.max(0.0, jours / 30.44);
    }

    private boolean estEnAttenteOuRejeteeChezEtudiant(Etudiant etudiant, List<Projet> projets,
                                                      List<ExperienceProfessionnelle> experiences, Competence target) {
        boolean dansExperiences = experiences.stream()
                .flatMap(e -> e.getCompetences().stream())
                .anyMatch(c -> correspond(c, target) && c.getStatut() != StatutCompetence.VALIDEE);
        if (dansExperiences) return true;

        boolean dansProjets = projets.stream()
                .flatMap(p -> p.getCompetences().stream())
                .anyMatch(c -> correspond(c, target) && c.getStatut() != StatutCompetence.VALIDEE);
        if (dansProjets) return true;

        return etudiant.getCompetences().stream()
                .anyMatch(c -> correspond(c, target) && c.getStatut() != StatutCompetence.VALIDEE);
    }

    private boolean correspondFiltreTexte(String valeur, String filtre) {
        return filtre == null || filtre.isBlank() || (valeur != null &&
                valeur.toLowerCase(Locale.ROOT).contains(filtre.trim().toLowerCase(Locale.ROOT)));
    }

    private double arrondir(double valeur, int decimales) {
        double facteur = Math.pow(10, decimales);
        return Math.round(valeur * facteur) / facteur;
    }
}
