package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.*;
import com.gi3.matchingcv.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implémentation du Coach CV s'appuyant sur MatchingService.
 * Calcule des conseils concrets et chiffrés par règles déterministes (sans IA).
 * Ne modifie ni n'enregistre rien en base lors des simulations.
 */
@Service
@Transactional(readOnly = true)
public class CoachServiceImpl implements CoachService {

    private final MatchingService matchingService;
    private final EtudiantRepository etudiantRepository;
    private final OffreRepository offreRepository;
    private final CompetenceRepository competenceRepository;
    private final ProjetRepository projetRepository;
    private final ExperienceProfessionnelleRepository experienceRepository;

    public CoachServiceImpl(
            MatchingService matchingService,
            EtudiantRepository etudiantRepository,
            OffreRepository offreRepository,
            CompetenceRepository competenceRepository,
            ProjetRepository projetRepository,
            ExperienceProfessionnelleRepository experienceRepository
    ) {
        this.matchingService = matchingService;
        this.etudiantRepository = etudiantRepository;
        this.offreRepository = offreRepository;
        this.competenceRepository = competenceRepository;
        this.projetRepository = projetRepository;
        this.experienceRepository = experienceRepository;
    }

    @Override
    public MatchingDetailDto simulerScore(Long etudiantId, Long offreId, Map<Long, Double> hypotheses) {
        if (etudiantId == null) throw new IllegalArgumentException("L'identifiant de l'étudiant est obligatoire.");
        if (offreId == null) throw new IllegalArgumentException("L'identifiant de l'offre est obligatoire.");

        MatchingDetailDto actuel = matchingService.calculerScore(etudiantId, offreId);
        if (!actuel.scoreDisponible() || hypotheses == null || hypotheses.isEmpty()) {
            return actuel;
        }

        // Reconstruire la liste des compétences avec les hypothèses appliquées en mémoire
        List<CompetenceDetailScoreDto> simulees = new ArrayList<>();
        for (CompetenceDetailScoreDto c : actuel.competences()) {
            if (c.statut() != StatutCompetence.VALIDEE) {
                simulees.add(c);
                continue;
            }

            Double niveauHypothese = hypotheses.get(c.competenceId());
            if (niveauHypothese == null) {
                simulees.add(c);
                continue;
            }

            double actuelN = c.niveau() != null ? c.niveau() : 0.0;
            double nouveauN = Math.max(actuelN, niveauHypothese);
            boolean comptee = (c.typeExigence() == TypeExigence.REQUISE) || (nouveauN > 0.0);
            boolean possedee = nouveauN > 0.0;
            String provLabel = String.format(Locale.ROOT, "Simulation (%.2f)", nouveauN);
            String raison = String.format(Locale.ROOT, "Simulation 'et si' : niveau valorisé à %.2f", nouveauN);

            simulees.add(new CompetenceDetailScoreDto(
                    c.competenceId(),
                    c.nom(),
                    c.typeExigence(),
                    c.statut(),
                    "SIMULATION",
                    provLabel,
                    arrondir(nouveauN, 2),
                    comptee,
                    raison,
                    possedee
            ));
        }

        // Recalcul strict selon les règles de MatchingService
        List<CompetenceDetailScoreDto> requisesValidees = simulees.stream()
                .filter(c -> c.typeExigence() == TypeExigence.REQUISE && c.statut() == StatutCompetence.VALIDEE)
                .toList();

        List<CompetenceDetailScoreDto> atoutsValides = simulees.stream()
                .filter(c -> c.typeExigence() == TypeExigence.ATOUT && c.statut() == StatutCompetence.VALIDEE)
                .toList();

        if (requisesValidees.isEmpty()) {
            return actuel;
        }

        double coefRequises = atoutsValides.isEmpty() ? 100.0 : 80.0;
        double coefAtouts = atoutsValides.isEmpty() ? 0.0 : 20.0;

        double sommeNRequises = requisesValidees.stream()
                .mapToDouble(CompetenceDetailScoreDto::niveau)
                .sum();
        double scoreRequises = (sommeNRequises / requisesValidees.size()) * coefRequises;

        List<CompetenceDetailScoreDto> atoutsPossedes = atoutsValides.stream()
                .filter(a -> a.niveau() != null && a.niveau() > 0.0)
                .toList();

        double scoreAtouts = 0.0;
        if (!atoutsPossedes.isEmpty() && coefAtouts > 0.0) {
            double sommeNAtouts = atoutsPossedes.stream()
                    .mapToDouble(CompetenceDetailScoreDto::niveau)
                    .sum();
            scoreAtouts = (sommeNAtouts / atoutsValides.size()) * coefAtouts;
        }

        double scoreFinal = Math.min(100.0, scoreRequises + scoreAtouts);

        return new MatchingDetailDto(
                etudiantId,
                offreId,
                actuel.offreTitre(),
                actuel.entreprise(),
                arrondir(scoreFinal, 1),
                true,
                "Score simulé sans persistance en base.",
                arrondir(scoreRequises, 1),
                arrondir(scoreAtouts, 1),
                coefRequises,
                coefAtouts,
                requisesValidees.size(),
                atoutsValides.size(),
                simulees
        );
    }

    @Override
    public MatchingDetailDto simulerScore(Long etudiantId, Long offreId, Long competenceId, double niveauSimule) {
        if (competenceId == null) {
            return matchingService.calculerScore(etudiantId, offreId);
        }
        return simulerScore(etudiantId, offreId, Map.of(competenceId, niveauSimule));
    }

    @Override
    public SimulationResultDto simulerGain(Long etudiantId, Long offreId, Long competenceId, double niveauSimule) {
        MatchingDetailDto actuel = matchingService.calculerScore(etudiantId, offreId);
        MatchingDetailDto simule = simulerScore(etudiantId, offreId, competenceId, niveauSimule);

        double scoreActuel = (actuel.score() != null) ? actuel.score() : 0.0;
        double scoreSimule = (simule.score() != null) ? simule.score() : 0.0;
        double gain = Math.max(0.0, scoreSimule - scoreActuel);

        return new SimulationResultDto(
                etudiantId,
                offreId,
                arrondir(scoreActuel, 1),
                arrondir(scoreSimule, 1),
                arrondir(gain, 1),
                simule
        );
    }

    @Override
    public List<ConseilCoachDto> genererConseilsPourOffre(Long etudiantId, Long offreId) {
        if (etudiantId == null || offreId == null) return Collections.emptyList();

        MatchingDetailDto detail = matchingService.calculerScore(etudiantId, offreId);
        if (!detail.scoreDisponible() || detail.competences() == null) {
            return Collections.emptyList();
        }

        List<ConseilCoachDto> conseils = new ArrayList<>();

        for (CompetenceDetailScoreDto c : detail.competences()) {
            if (c.statut() != StatutCompetence.VALIDEE) continue;

            double niveauActuel = c.niveau() != null ? c.niveau() : 0.0;

            // Règle : aucun conseil pour une compétence déjà au niveau maximal (>= 0.8 ou expérience)
            if (niveauActuel >= 0.8) continue;

            if (niveauActuel == 0.0) {
                // Compétence manquante -> suggérer de l'ajouter et la relier à un projet (0.7)
                SimulationResultDto sim = simulerGain(etudiantId, offreId, c.competenceId(), 0.7);
                if (sim.gain() > 0.0) {
                    boolean isReq = c.typeExigence() == TypeExigence.REQUISE;
                    String role = isReq ? "requise" : "un atout";
                    conseils.add(new ConseilCoachDto(
                            "offre-" + offreId + "-comp-" + c.competenceId() + "-ajouter",
                            TypeConseilCoach.OFFRE_COMPETENCE,
                            "Ajouter " + c.nom() + " et la lier à un projet",
                            String.format(Locale.ROOT,
                                    "%s est %s pour l'offre \"%s\". En l'ajoutant à un projet (niveau 0.7), vous gagnerez +%.1f pts.",
                                    c.nom(), role, detail.offreTitre(), sim.gain()),
                            sim.gain(),
                            c.competenceId(),
                            c.nom(),
                            offreId,
                            detail.offreTitre(),
                            detail.entreprise(),
                            "AJOUTER_COMPETENCE",
                            "Ajouter la compétence",
                            "/competences.html",
                            0.0,
                            0.7,
                            isReq ? 1 : 2
                    ));
                }
            } else if (niveauActuel == 0.4) {
                // Compétence présente seulement en déclaratif -> valoriser dans un projet (0.7)
                SimulationResultDto sim = simulerGain(etudiantId, offreId, c.competenceId(), 0.7);
                if (sim.gain() > 0.0) {
                    conseils.add(new ConseilCoachDto(
                            "offre-" + offreId + "-comp-" + c.competenceId() + "-valoriser",
                            TypeConseilCoach.VALORISATION,
                            "Valoriser " + c.nom() + " dans un projet",
                            String.format(Locale.ROOT,
                                    "Vous possédez %s en liste déclarative (0.4). La relier à un projet vous fera passer à 0.7 (+%.1f pts sur %s).",
                                    c.nom(), sim.gain(), detail.offreTitre()),
                            sim.gain(),
                            c.competenceId(),
                            c.nom(),
                            offreId,
                            detail.offreTitre(),
                            detail.entreprise(),
                            "LIER_PROJET",
                            "Lier à un projet",
                            "/dashboard-etudiant.html?open=profil",
                            0.4,
                            0.7,
                            2
                    ));
                }
            }
        }

        return filtrerEtPlafonnerConseils(conseils, 8);
    }

    @Override
    public List<ConseilCoachDto> genererConseilsEtudiant(Long etudiantId) {
        if (etudiantId == null) return Collections.emptyList();

        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new EntityNotFoundException("Étudiant introuvable : " + etudiantId));

        List<Projet> projets = projetRepository.findByEtudiantId(etudiantId);
        List<ExperienceProfessionnelle> experiences = experienceRepository.findByEtudiantId(etudiantId);
        List<Offre> offresActives = offreRepository.findByStatut(StatutOffre.ACTIVE);

        List<ConseilCoachDto> tousConseils = new ArrayList<>();

        // -------------------------------------------------------------
        // 1. & 2. Conseils ciblés sur les meilleures offres actives
        // -------------------------------------------------------------
        List<OffreRecommandeeDto> topOffres = matchingService.recommanderOffres(etudiantId, null);
        for (OffreRecommandeeDto rec : topOffres.stream().limit(4).toList()) {
            if (!rec.scoreDisponible() || rec.detailScore() == null) continue;
            List<ConseilCoachDto> conseilsOffre = genererConseilsPourOffre(etudiantId, rec.id());
            tousConseils.addAll(conseilsOffre);
        }

        // -------------------------------------------------------------
        // 2. Valorisation des compétences déclaratives de l'étudiant
        // -------------------------------------------------------------
        Set<Long> compDansProjetsOuExp = new HashSet<>();
        for (Projet p : projets) {
            for (Competence c : p.getCompetences()) {
                if (c.getId() != null) compDansProjetsOuExp.add(c.getId());
            }
        }
        for (ExperienceProfessionnelle exp : experiences) {
            for (Competence c : exp.getCompetences()) {
                if (c.getId() != null) compDansProjetsOuExp.add(c.getId());
            }
        }

        List<Competence> declarativesSeules = etudiant.getCompetences().stream()
                .filter(c -> c.getStatut() == StatutCompetence.VALIDEE)
                .filter(c -> !compDansProjetsOuExp.contains(c.getId()))
                .toList();

        for (Competence compDec : declarativesSeules) {
            // Trouver la meilleure offre active qui bénéficie de cette compétence
            double maxGain = 0.0;
            Offre meilleureOffre = null;
            for (Offre offre : offresActives) {
                boolean demandee = offre.getOffreCompetences().stream()
                        .anyMatch(oc -> oc.getCompetence() != null &&
                                oc.getCompetence().getId().equals(compDec.getId()) &&
                                oc.getCompetence().getStatut() == StatutCompetence.VALIDEE);
                if (demandee) {
                    SimulationResultDto sim = simulerGain(etudiantId, offre.getId(), compDec.getId(), 0.7);
                    if (sim.gain() > maxGain) {
                        maxGain = sim.gain();
                        meilleureOffre = offre;
                    }
                }
            }

            if (maxGain > 0.0 && meilleureOffre != null) {
                tousConseils.add(new ConseilCoachDto(
                        "val-dec-" + compDec.getId(),
                        TypeConseilCoach.VALORISATION,
                        "Lier " + compDec.getNom() + " à un projet",
                        String.format(Locale.ROOT,
                                "Compétence présente uniquement en déclaratif (0.4). En la reliant à un projet, vous montez à 0.7 (+%.1f pts sur \"%s\").",
                                maxGain, meilleureOffre.getTitre()),
                        maxGain,
                        compDec.getId(),
                        compDec.getNom(),
                        meilleureOffre.getId(),
                        meilleureOffre.getTitre(),
                        meilleureOffre.getRecruteur() != null ? meilleureOffre.getRecruteur().getNomEntreprise() : "",
                        "LIER_PROJET",
                        "Lier à un projet",
                        "/dashboard-etudiant.html?open=profil",
                        0.4,
                        0.7,
                        2
                ));
            }
        }

        // -------------------------------------------------------------
        // 3. Compétences les plus demandées sur le marché actif
        // -------------------------------------------------------------
        Set<Long> compPossedees = new HashSet<>();
        etudiant.getCompetences().forEach(c -> compPossedees.add(c.getId()));
        compDansProjetsOuExp.forEach(compPossedees::add);

        Map<Competence, Long> freqDemandees = new HashMap<>();
        for (Offre o : offresActives) {
            for (OffreCompetence oc : o.getOffreCompetences()) {
                Competence c = oc.getCompetence();
                if (c != null && c.getStatut() == StatutCompetence.VALIDEE && !compPossedees.contains(c.getId())) {
                    freqDemandees.merge(c, 1L, Long::sum);
                }
            }
        }

        List<Map.Entry<Competence, Long>> topDemandees = freqDemandees.entrySet().stream()
                .sorted(Map.Entry.<Competence, Long>comparingByValue().reversed())
                .limit(3)
                .toList();

        for (Map.Entry<Competence, Long> entry : topDemandees) {
            Competence c = entry.getKey();
            long count = entry.getValue();

            // Trouver le gain potentiel maximal sur une offre
            double maxGain = 0.0;
            Offre topOffre = null;
            for (Offre o : offresActives) {
                boolean match = o.getOffreCompetences().stream()
                        .anyMatch(oc -> oc.getCompetence() != null && oc.getCompetence().getId().equals(c.getId()));
                if (match) {
                    SimulationResultDto sim = simulerGain(etudiantId, o.getId(), c.getId(), 0.7);
                    if (sim.gain() > maxGain) {
                        maxGain = sim.gain();
                        topOffre = o;
                    }
                }
            }

            double gainEstime = maxGain > 0.0 ? maxGain : count * 2.0;
            String desc = (topOffre != null && maxGain > 0.0)
                    ? String.format(Locale.ROOT, "%s est exigée par %d offres actives. L'ajouter à un projet vous rapporterait jusqu'à +%.1f pts sur \"%s\".", c.getNom(), count, maxGain, topOffre.getTitre())
                    : String.format(Locale.ROOT, "%s est exigée par %d offres actives sur la plateforme. Ajoutez-la pour élargir vos opportunités.", c.getNom(), count);

            tousConseils.add(new ConseilCoachDto(
                    "marche-demande-" + c.getId(),
                    TypeConseilCoach.COMPETENCE_DEMANDEE,
                    c.getNom() + " : recherchée par " + count + " offres",
                    desc,
                    arrondir(gainEstime, 1),
                    c.getId(),
                    c.getNom(),
                    topOffre != null ? topOffre.getId() : null,
                    topOffre != null ? topOffre.getTitre() : null,
                    topOffre != null && topOffre.getRecruteur() != null ? topOffre.getRecruteur().getNomEntreprise() : "",
                    "AJOUTER_COMPETENCE",
                    "Déclarer la compétence",
                    "/competences.html",
                    0.0,
                    0.7,
                    3
            ));
        }

        // -------------------------------------------------------------
        // 4. Hygiène du profil
        // -------------------------------------------------------------
        // A. Compétences en attente
        long nbEnAttente = etudiant.getCompetences().stream()
                .filter(c -> c.getStatut() == StatutCompetence.EN_ATTENTE)
                .count();
        if (nbEnAttente > 0) {
            tousConseils.add(new ConseilCoachDto(
                    "hygiene-en-attente",
                    TypeConseilCoach.HYGIENE_PROFIL,
                    nbEnAttente + " compétence(s) en attente de validation",
                    "Vos compétences soumises sont en attente de modération admin. Elles ne rapportent des points qu'une fois approuvées.",
                    arrondir(nbEnAttente * 4.0, 1),
                    null, null, null, null, null,
                    "GERER_COMPETENCES",
                    "Voir mes compétences",
                    "/competences.html",
                    0.0, 0.4,
                    4
            ));
        }

        // B. Compétences rejetées
        long nbRejetees = etudiant.getCompetences().stream()
                .filter(c -> c.getStatut() == StatutCompetence.REJETEE)
                .count();
        if (nbRejetees > 0) {
            tousConseils.add(new ConseilCoachDto(
                    "hygiene-rejetees",
                    TypeConseilCoach.HYGIENE_PROFIL,
                    nbRejetees + " compétence(s) rejetée(s)",
                    "Certaines compétences n'ont pas été validées par les modérateurs. Remplacez-les par des compétences officielles du catalogue.",
                    arrondir(nbRejetees * 3.0, 1),
                    null, null, null, null, null,
                    "CATALOGUE",
                    "Consulter le catalogue",
                    "/competences.html",
                    0.0, 0.4,
                    4
            ));
        }

        // C. Projets sans compétence liée
        long nbProjetsSansComp = projets.stream()
                .filter(p -> p.getCompetences() == null || p.getCompetences().isEmpty())
                .count();
        if (nbProjetsSansComp > 0) {
            tousConseils.add(new ConseilCoachDto(
                    "hygiene-projets-vides",
                    TypeConseilCoach.HYGIENE_PROFIL,
                    nbProjetsSansComp + " projet(s) sans compétence liée",
                    "Vous avez des projets sans compétences associées. Les relier permet de valoriser vos compétences à 0.7 au lieu de 0.4.",
                    arrondir(nbProjetsSansComp * 6.5, 1),
                    null, null, null, null, null,
                    "PROFIL_PROJET",
                    "Associer aux projets",
                    "/dashboard-etudiant.html?open=profil",
                    0.4, 0.7,
                    3
            ));
        }

        // D. Expériences sans compétence liée
        long nbExpSansComp = experiences.stream()
                .filter(e -> e.getCompetences() == null || e.getCompetences().isEmpty())
                .count();
        if (nbExpSansComp > 0) {
            tousConseils.add(new ConseilCoachDto(
                    "hygiene-exp-vides",
                    TypeConseilCoach.HYGIENE_PROFIL,
                    nbExpSansComp + " expérience(s) sans compétence liée",
                    "Vos expériences professionnelles sans compétences liées n'apportent aucun bonus de niveau (jusqu'à 1.0). Associez-les !",
                    arrondir(nbExpSansComp * 8.0, 1),
                    null, null, null, null, null,
                    "PROFIL_EXPERIENCE",
                    "Associer aux expériences",
                    "/dashboard-etudiant.html?open=profil",
                    0.4, 0.8,
                    2
            ));
        }

        // E. Complétion globale du profil
        int completion = calculerTauxCompletion(etudiant, projets, experiences);
        if (completion < 100) {
            double gainEstime = Math.max(2.0, (100 - completion) * 0.15);
            tousConseils.add(new ConseilCoachDto(
                    "hygiene-completion-profil",
                    TypeConseilCoach.HYGIENE_PROFIL,
                    "Complétez votre profil (" + completion + "%)",
                    "Un profil exhaustif inspire confiance et améliore le matching global avec les offres d'entreprises.",
                    arrondir(gainEstime, 1),
                    null, null, null, null, null,
                    "PROFIL_COMPLETER",
                    "Compléter mon profil",
                    "/dashboard-etudiant.html?open=profil",
                    (double) completion, 100.0,
                    5
            ));
        }

        return filtrerEtPlafonnerConseils(tousConseils, 8);
    }

    @Override
    public CoachResumeDto obtenirResumeCoach(Long etudiantId) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new EntityNotFoundException("Étudiant introuvable : " + etudiantId));

        List<Projet> projets = projetRepository.findByEtudiantId(etudiantId);
        List<ExperienceProfessionnelle> experiences = experienceRepository.findByEtudiantId(etudiantId);

        List<ConseilCoachDto> tous = genererConseilsEtudiant(etudiantId);
        List<ConseilCoachDto> top = tous.stream().limit(3).toList();

        int completion = calculerTauxCompletion(etudiant, projets, experiences);
        double maxGain = tous.stream()
                .mapToDouble(c -> c.gainPoints() != null ? c.gainPoints() : 0.0)
                .max()
                .orElse(0.0);

        int nbAttente = (int) etudiant.getCompetences().stream()
                .filter(c -> c.getStatut() == StatutCompetence.EN_ATTENTE)
                .count();

        int nbRejetee = (int) etudiant.getCompetences().stream()
                .filter(c -> c.getStatut() == StatutCompetence.REJETEE)
                .count();

        int nbProjetsVides = (int) projets.stream()
                .filter(p -> p.getCompetences() == null || p.getCompetences().isEmpty())
                .count();

        int nbExpVides = (int) experiences.stream()
                .filter(e -> e.getCompetences() == null || e.getCompetences().isEmpty())
                .count();

        return new CoachResumeDto(
                etudiantId,
                completion,
                arrondir(maxGain, 1),
                tous.size(),
                top,
                tous,
                nbAttente,
                nbRejetee,
                nbProjetsVides,
                nbExpVides
        );
    }

    /**
     * Dédoublonne les conseils selon leur clé métier, les trie par gain décroissant,
     * et plafonne le résultat à la limite souhaitée (5 à 8).
     */
    private List<ConseilCoachDto> filtrerEtPlafonnerConseils(List<ConseilCoachDto> source, int maxLimit) {
        Map<String, ConseilCoachDto> deduplicated = new LinkedHashMap<>();

        // Tri préparatoire par gain décroissant
        List<ConseilCoachDto> tries = source.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ConseilCoachDto::gainPoints, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ConseilCoachDto::priorite))
                .toList();

        for (ConseilCoachDto c : tries) {
            String cle;
            if (c.competenceId() != null && c.offreId() != null) {
                cle = "comp:" + c.competenceId() + ":offre:" + c.offreId();
            } else if (c.competenceId() != null) {
                cle = "comp:" + c.competenceId() + ":" + c.type();
            } else {
                cle = c.id();
            }

            if (!deduplicated.containsKey(cle)) {
                deduplicated.put(cle, c);
            }
        }

        return deduplicated.values().stream()
                .sorted(Comparator.comparing(ConseilCoachDto::gainPoints, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ConseilCoachDto::priorite))
                .limit(maxLimit)
                .toList();
    }

    /**
     * Calcule le taux de complétion du profil étudiant (0 à 100%).
     */
    private int calculerTauxCompletion(Etudiant e, List<Projet> projets, List<ExperienceProfessionnelle> exp) {
        int score = 0;
        // 1. Informations de base (25%)
        if (e.getNom() != null && !e.getNom().isBlank() && e.getPrenom() != null && !e.getPrenom().isBlank()) {
            score += 10;
        }
        if (e.getFiliere() != null && !e.getFiliere().isBlank()) {
            score += 10;
        }
        if (e.getEtablissement() != null && !e.getEtablissement().isBlank()) {
            score += 5;
        }

        // 2. Compétences déclarées ou liées (25%)
        long nbCompValidees = e.getCompetences().stream()
                .filter(c -> c.getStatut() == StatutCompetence.VALIDEE)
                .count();
        if (nbCompValidees >= 3) {
            score += 25;
        } else if (nbCompValidees > 0) {
            score += (int) (nbCompValidees * 8);
        }

        // 3. Projets renseignés avec compétences (25%)
        boolean hasProjetAvecComp = projets.stream()
                .anyMatch(p -> p.getCompetences() != null && !p.getCompetences().isEmpty());
        if (hasProjetAvecComp) {
            score += 25;
        } else if (!projets.isEmpty()) {
            score += 15;
        }

        // 4. Expériences renseignées avec compétences (25%)
        boolean hasExpAvecComp = exp.stream()
                .anyMatch(x -> x.getCompetences() != null && !x.getCompetences().isEmpty());
        if (hasExpAvecComp) {
            score += 25;
        } else if (!exp.isEmpty()) {
            score += 15;
        }

        return Math.min(100, score);
    }

    private double arrondir(double valeur, int decimales) {
        double facteur = Math.pow(10, decimales);
        return Math.round(valeur * facteur) / facteur;
    }
}
