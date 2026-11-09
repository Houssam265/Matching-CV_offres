package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.model.Candidature;
import com.gi3.matchingcv.model.Offre;
import com.gi3.matchingcv.model.OffreCompetence;
import com.gi3.matchingcv.model.Utilisateur;
import com.gi3.matchingcv.model.enums.StatutCandidature;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.model.enums.StatutOffre;
import com.gi3.matchingcv.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implémentation du service des statistiques pour l'espace administrateur.
 * // TODO sécurité : toutes les méthodes sont réservées ADMIN.
 */
@Service
@Transactional(readOnly = true)
public class AdminStatistiquesServiceImpl implements AdminStatistiquesService {

    private final UtilisateurRepository utilisateurRepository;
    private final OffreRepository offreRepository;
    private final CandidatureRepository candidatureRepository;
    private final CompetenceRepository competenceRepository;
    private final OffreCompetenceRepository offreCompetenceRepository;
    private final EtudiantRepository etudiantRepository;

    public AdminStatistiquesServiceImpl(
            UtilisateurRepository utilisateurRepository,
            OffreRepository offreRepository,
            CandidatureRepository candidatureRepository,
            CompetenceRepository competenceRepository,
            OffreCompetenceRepository offreCompetenceRepository,
            EtudiantRepository etudiantRepository
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.offreRepository = offreRepository;
        this.candidatureRepository = candidatureRepository;
        this.competenceRepository = competenceRepository;
        this.offreCompetenceRepository = offreCompetenceRepository;
        this.etudiantRepository = etudiantRepository;
    }

    @Override
    public AdminStatsDto obtenirStatistiques() {
        // 1. Utilisateurs par rôle
        List<Utilisateur> utilisateurs = utilisateurRepository.findAll();
        Map<String, Long> parRole = new LinkedHashMap<>();
        parRole.put("ETUDIANT", utilisateurs.stream().filter(u -> u.getRole() != null && u.getRole().name().equals("ETUDIANT")).count());
        parRole.put("RECRUTEUR", utilisateurs.stream().filter(u -> u.getRole() != null && u.getRole().name().equals("RECRUTEUR")).count());
        parRole.put("ADMIN", utilisateurs.stream().filter(u -> u.getRole() != null && u.getRole().name().equals("ADMIN")).count());

        // 2. Inscriptions par semaine (sur les dernières semaines disponibles)
        Map<String, Long> inscriptionsParSemaineMap = new TreeMap<>();
        for (Utilisateur u : utilisateurs) {
            LocalDateTime dt = u.getDateInscription();
            if (dt != null) {
                int annee = dt.getYear();
                int semaine = dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                String cle = String.format("%d-S%02d", annee, semaine);
                inscriptionsParSemaineMap.put(cle, inscriptionsParSemaineMap.getOrDefault(cle, 0L) + 1);
            }
        }
        List<SemaineInscriptionDto> inscriptionsSemaine = inscriptionsParSemaineMap.entrySet().stream()
                .map(e -> new SemaineInscriptionDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        // 3. Offres actives et clôturées par domaine
        List<Offre> offres = offreRepository.findAll();
        Map<String, DomaineStatDto> offresDomaine = new TreeMap<>();
        for (Offre o : offres) {
            String dom = (o.getDomaine() != null && !o.getDomaine().isBlank()) ? o.getDomaine().trim() : "Non précisé";
            DomaineStatDto existant = offresDomaine.getOrDefault(dom, new DomaineStatDto(0, 0));
            long actives = existant.actives() + (o.getStatut() == StatutOffre.ACTIVE ? 1 : 0);
            long cloturees = existant.cloturees() + (o.getStatut() == StatutOffre.CLOTUREE ? 1 : 0);
            offresDomaine.put(dom, new DomaineStatDto(actives, cloturees));
        }

        // 4. Entonnoir global des candidatures
        List<Candidature> candidatures = candidatureRepository.findAll();
        long totalCand = candidatures.size();
        long envoyees = candidatures.stream().filter(c -> c.getStatut() == StatutCandidature.ENVOYEE).count();
        long enCours = candidatures.stream().filter(c -> c.getStatut() == StatutCandidature.VUE
                || c.getStatut() == StatutCandidature.ENTRETIEN).count();
        long acceptees = candidatures.stream().filter(c -> c.getStatut() == StatutCandidature.ACCEPTEE).count();
        long refusees = candidatures.stream().filter(c -> c.getStatut() == StatutCandidature.REFUSEE
                || c.getStatut() == StatutCandidature.RETIREE).count();
        EntonnoirCandidaturesDto entonnoir = new EntonnoirCandidaturesDto(totalCand, envoyees, enCours, acceptees, refusees);

        // 5. Score moyen et taux d'acceptation
        DoubleSummaryStatistics scoreStats = candidatures.stream()
                .filter(c -> c.getScore() != null)
                .mapToDouble(Candidature::getScore)
                .summaryStatistics();
        Double scoreMoyen = scoreStats.getCount() > 0 ? Math.round(scoreStats.getAverage() * 10.0) / 10.0 : 0.0;
        Double tauxAcceptation = totalCand > 0 ? Math.round(((double) acceptees / totalCand * 100.0) * 10.0) / 10.0 : 0.0;

        // 6. Écart du marché (compétences demandées vs possédées)
        Map<String, Long> demandeesParNom = new HashMap<>();
        for (OffreCompetence oc : offreCompetenceRepository.findAll()) {
            if (oc.getCompetence() != null && oc.getCompetence().getNom() != null) {
                String nom = oc.getCompetence().getNom();
                demandeesParNom.put(nom, demandeesParNom.getOrDefault(nom, 0L) + 1);
            }
        }

        Map<String, Long> possedeesParNom = new HashMap<>();
        etudiantRepository.findAll().forEach(e -> {
            if (e.getCompetences() != null) {
                e.getCompetences().forEach(c -> {
                    if (c != null && c.getNom() != null) {
                        possedeesParNom.put(c.getNom(), possedeesParNom.getOrDefault(c.getNom(), 0L) + 1);
                    }
                });
            }
        });

        Set<String> tousNoms = new HashSet<>();
        tousNoms.addAll(demandeesParNom.keySet());
        tousNoms.addAll(possedeesParNom.keySet());

        List<CompetenceMarcheDto> ecartMarche = tousNoms.stream()
                .map(nom -> {
                    long dem = demandeesParNom.getOrDefault(nom, 0L);
                    long poss = possedeesParNom.getOrDefault(nom, 0L);
                    return new CompetenceMarcheDto(nom, dem, poss, dem - poss);
                })
                .sorted(Comparator.comparing(CompetenceMarcheDto::demandees).reversed()
                        .thenComparing(CompetenceMarcheDto::ecart).reversed())
                .limit(15)
                .collect(Collectors.toList());

        // 7. Compétences en attente
        long enAttente = competenceRepository.findByStatut(StatutCompetence.EN_ATTENTE).size();

        return new AdminStatsDto(
                parRole,
                inscriptionsSemaine,
                offresDomaine,
                entonnoir,
                scoreMoyen,
                tauxAcceptation,
                ecartMarche,
                enAttente
        );
    }
}
