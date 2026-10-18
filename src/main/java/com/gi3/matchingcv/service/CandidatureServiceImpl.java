package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.StatutCandidature;
import com.gi3.matchingcv.model.enums.StatutOffre;
import com.gi3.matchingcv.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class CandidatureServiceImpl implements CandidatureService {

    /** Transitions autorisées : statut courant → ensemble des statuts cibles valides */
    private static final java.util.Map<StatutCandidature, Set<StatutCandidature>> TRANSITIONS =
            java.util.Map.of(
                    StatutCandidature.ENVOYEE,  Set.of(StatutCandidature.VUE, StatutCandidature.ACCEPTEE, StatutCandidature.REFUSEE),
                    StatutCandidature.VUE,      Set.of(StatutCandidature.ACCEPTEE, StatutCandidature.REFUSEE),
                    StatutCandidature.ACCEPTEE, Set.of(),
                    StatutCandidature.REFUSEE,  Set.of()
            );

    private final CandidatureRepository candidatureRepository;
    private final EtudiantRepository    etudiantRepository;
    private final OffreRepository       offreRepository;
    private final MatchingService       matchingService;
    private final ProjetRepository      projetRepository;
    private final ExperienceProfessionnelleRepository experienceRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public CandidatureServiceImpl(CandidatureRepository candidatureRepository,
                                  EtudiantRepository etudiantRepository,
                                  OffreRepository offreRepository,
                                  MatchingService matchingService) {
        this(candidatureRepository, etudiantRepository, offreRepository, matchingService, null, null, null);
    }

    public CandidatureServiceImpl(CandidatureRepository candidatureRepository,
                                  EtudiantRepository etudiantRepository,
                                  OffreRepository offreRepository,
                                  MatchingService matchingService,
                                  ProjetRepository projetRepository,
                                  ExperienceProfessionnelleRepository experienceRepository) {
        this(candidatureRepository, etudiantRepository, offreRepository, matchingService, projetRepository, experienceRepository, null);
    }

    @Autowired
    public CandidatureServiceImpl(CandidatureRepository candidatureRepository,
                                  EtudiantRepository etudiantRepository,
                                  OffreRepository offreRepository,
                                  MatchingService matchingService,
                                  ProjetRepository projetRepository,
                                  ExperienceProfessionnelleRepository experienceRepository,
                                  org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.candidatureRepository = candidatureRepository;
        this.etudiantRepository    = etudiantRepository;
        this.offreRepository       = offreRepository;
        this.matchingService       = matchingService;
        this.projetRepository      = projetRepository;
        this.experienceRepository  = experienceRepository;
        this.eventPublisher        = eventPublisher;
    }

    // ──────────────────────────────────────────────────────────
    // postuler
    // ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CandidatureDto postuler(Long etudiantId, Long offreId) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new EntityNotFoundException("Étudiant introuvable : " + etudiantId));

        Offre offre = offreRepository.findById(offreId)
                .orElseThrow(() -> new EntityNotFoundException("Offre introuvable : " + offreId));

        if (offre.getStatut() != StatutOffre.ACTIVE) {
            throw new IllegalStateException("Impossible de postuler : l'offre n'est plus active.");
        }

        if (candidatureRepository.existsByEtudiantIdAndOffreId(etudiantId, offreId)) {
            throw new IllegalStateException("Vous avez déjà postulé à cette offre.");
        }

        // Snapshoter le score calculé au moment de la candidature (peut être null si indisponible)
        Double score = null;
        try {
            MatchingDetailDto detail = matchingService.calculerScore(etudiantId, offreId);
            if (detail.scoreDisponible() && detail.score() != null) {
                score = detail.score();
            }
        } catch (Exception ignored) {
            // score reste null si le calcul échoue
        }

        Candidature cand = new Candidature();
        cand.setEtudiant(etudiant);
        cand.setOffre(offre);
        cand.setScore(score);
        cand.setStatut(StatutCandidature.ENVOYEE);

        Candidature saved = candidatureRepository.save(cand);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new com.gi3.matchingcv.event.CandidatureCreeeEvent(
                    saved.getId(),
                    etudiant.getId(),
                    offre.getId(),
                    offre.getRecruteur() != null ? offre.getRecruteur().getId() : null,
                    score,
                    etudiant.getNom(),
                    etudiant.getPrenom(),
                    offre.getTitre()
            ));
        }

        return CandidatureDto.from(saved);
    }

    // ──────────────────────────────────────────────────────────
    // Lister
    // ──────────────────────────────────────────────────────────

    @Override
    public List<CandidatureDto> listerParEtudiant(Long etudiantId) {
        if (!etudiantRepository.existsById(etudiantId)) {
            throw new EntityNotFoundException("Étudiant introuvable : " + etudiantId);
        }
        return candidatureRepository.findByEtudiantIdOrderByDateCandidatureDesc(etudiantId)
                .stream()
                .map(CandidatureDto::from)
                .toList();
    }

    @Override
    public List<CandidatureDto> listerParOffre(Long offreId) {
        if (!offreRepository.existsById(offreId)) {
            throw new EntityNotFoundException("Offre introuvable : " + offreId);
        }
        return candidatureRepository.findByOffreIdOrderByScoreDesc(offreId)
                .stream()
                .map(CandidatureDto::from)
                .toList();
    }

    // ──────────────────────────────────────────────────────────
    // changerStatut
    // ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public CandidatureDto changerStatut(Long candidatureId, StatutCandidature nouveauStatut) {
        Candidature cand = candidatureRepository.findById(candidatureId)
                .orElseThrow(() -> new EntityNotFoundException("Candidature introuvable : " + candidatureId));

        StatutCandidature actuel = cand.getStatut();
        Set<StatutCandidature> cibles = TRANSITIONS.getOrDefault(actuel, Set.of());

        if (!cibles.contains(nouveauStatut)) {
            throw new IllegalStateException(
                    String.format("Transition refusée : %s → %s. Statut actuel : %s, transitions autorisées : %s.",
                            actuel, nouveauStatut, actuel, cibles));
        }

        cand.setStatut(nouveauStatut);
        Candidature saved = candidatureRepository.save(cand);

        if (eventPublisher != null && actuel != nouveauStatut) {
            eventPublisher.publishEvent(new com.gi3.matchingcv.event.CandidatureStatutChangeEvent(
                    saved.getId(),
                    saved.getEtudiant().getId(),
                    saved.getOffre().getId(),
                    actuel,
                    nouveauStatut,
                    saved.getOffre().getTitre(),
                    null
            ));
        }

        return CandidatureDto.from(saved);
    }

    // ──────────────────────────────────────────────────────────
    // Utilitaires
    // ──────────────────────────────────────────────────────────

    @Override
    public Optional<CandidatureDto> trouverParEtudiantEtOffre(Long etudiantId, Long offreId) {
        return candidatureRepository.findByEtudiantIdAndOffreId(etudiantId, offreId)
                .map(CandidatureDto::from);
    }

    @Override
    public long compterParEtudiant(Long etudiantId) {
        return candidatureRepository.countByEtudiantId(etudiantId);
    }

    // ──────────────────────────────────────────────────────────
    // Espace Recruteur - Candidatures & Candidats
    // ──────────────────────────────────────────────────────────

    @Override
    public List<CandidatOffreDto> listerCandidatsPourRecruteur(Long offreId, Long recruteurId, StatutCandidature filtreStatut) {
        Offre offre = offreRepository.findById(offreId)
                .orElseThrow(() -> new EntityNotFoundException("Offre introuvable : " + offreId));

        // TODO sécurité côté serveur : seul le recruteur propriétaire de l'offre peut voir ses candidats et changer un statut.
        if (offre.getRecruteur() == null || !Objects.equals(offre.getRecruteur().getId(), recruteurId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé : cette offre appartient à un autre recruteur.");
        }

        List<Candidature> candidatures = candidatureRepository.findByOffreIdOrderByScoreDesc(offreId);
        return candidatures.stream()
                .filter(c -> filtreStatut == null || c.getStatut() == filtreStatut)
                .sorted(Comparator.comparing(Candidature::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Candidature::getDateCandidature, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(CandidatOffreDto::from)
                .toList();
    }

    @Override
    @Transactional
    public CandidatDetailDto obtenirDetailCandidat(Long candidatureId, Long recruteurId) {
        Candidature cand = candidatureRepository.findById(candidatureId)
                .orElseThrow(() -> new EntityNotFoundException("Candidature introuvable : " + candidatureId));

        Offre offre = cand.getOffre();
        // TODO sécurité côté serveur : seul le recruteur propriétaire de l'offre peut voir ses candidats et changer un statut.
        if (offre.getRecruteur() == null || !Objects.equals(offre.getRecruteur().getId(), recruteurId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé : cette offre appartient à un autre recruteur.");
        }

        // Quand le recruteur ouvre le détail d'une candidature ENVOYEE, elle passe automatiquement à VUE.
        if (cand.getStatut() == StatutCandidature.ENVOYEE) {
            StatutCandidature ancien = cand.getStatut();
            cand.setStatut(StatutCandidature.VUE);
            cand = candidatureRepository.save(cand);
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new com.gi3.matchingcv.event.CandidatureStatutChangeEvent(
                        cand.getId(),
                        cand.getEtudiant().getId(),
                        cand.getOffre().getId(),
                        ancien,
                        StatutCandidature.VUE,
                        cand.getOffre().getTitre(),
                        recruteurId
                ));
            }
        }

        Etudiant etudiant = cand.getEtudiant();

        // Réutilise MatchingService.calculerScore
        MatchingDetailDto matchingDetail;
        try {
            matchingDetail = matchingService.calculerScore(etudiant.getId(), offre.getId());
        } catch (Exception e) {
            matchingDetail = new MatchingDetailDto(
                    etudiant.getId(), offre.getId(), offre.getTitre(),
                    offre.getRecruteur() != null ? offre.getRecruteur().getNomEntreprise() : "",
                    null, false, "Erreur calcul de score : " + e.getMessage(),
                    0.0, 0.0, 0.0, 0.0, 0, 0, List.of()
            );
        }

        Double scoreCandidature = cand.getScore();
        Double scoreActuel = matchingDetail.score();
        boolean scoreModifie = false;
        if (scoreCandidature != null && scoreActuel != null) {
            scoreModifie = Math.abs(scoreCandidature - scoreActuel) > 0.001;
        } else if (scoreCandidature != null || scoreActuel != null) {
            scoreModifie = true;
        }

        // L'email du candidat n'est affiché qu'une fois la candidature ACCEPTEE. Jamais de motDePasse.
        String email = (cand.getStatut() == StatutCandidature.ACCEPTEE) ? etudiant.getEmail() : null;

        List<Projet> projets = (projetRepository != null) ? projetRepository.findByEtudiantId(etudiant.getId()) : List.of();
        List<ExperienceProfessionnelle> experiences = (experienceRepository != null) ? experienceRepository.findByEtudiantId(etudiant.getId()) : List.of();

        List<CandidatDetailDto.ProjetCandidatDto> projetsDto = projets.stream()
                .map(p -> new CandidatDetailDto.ProjetCandidatDto(
                        p.getId(), p.getTitre(), p.getDescription(),
                        p.getCompetences().stream().map(Competence::getNom).toList()
                ))
                .toList();

        List<CandidatDetailDto.ExperienceCandidatDto> expsDto = experiences.stream()
                .map(ep -> new CandidatDetailDto.ExperienceCandidatDto(
                        ep.getId(), ep.getPoste(), ep.getEntreprise(),
                        ep.getDateDebut(), ep.getDateFin(),
                        ep.getCompetences().stream().map(Competence::getNom).toList()
                ))
                .toList();

        List<CandidatDetailDto.CompetenceProfilDto> competencesProfil = extraireCompetencesProfil(etudiant, projets, experiences);

        return new CandidatDetailDto(
                cand.getId(),
                etudiant.getId(),
                offre.getId(),
                offre.getTitre(),
                etudiant.getNom(),
                etudiant.getPrenom(),
                etudiant.getFiliere(),
                etudiant.getEtablissement(),
                email,
                cand.getStatut(),
                cand.getDateCandidature(),
                scoreCandidature,
                scoreActuel,
                scoreModifie,
                matchingDetail,
                competencesProfil,
                projetsDto,
                expsDto
        );
    }

    private List<CandidatDetailDto.CompetenceProfilDto> extraireCompetencesProfil(
            Etudiant etudiant, List<Projet> projets, List<ExperienceProfessionnelle> exps) {
        Map<Long, CandidatDetailDto.CompetenceProfilDto> map = new LinkedHashMap<>();

        // 1. Experiences (priorité forte)
        for (ExperienceProfessionnelle exp : exps) {
            for (Competence c : exp.getCompetences()) {
                map.putIfAbsent(c.getId(), new CandidatDetailDto.CompetenceProfilDto(
                        c.getId(), c.getNom(), c.getCategorie(), c.getStatut(),
                        "EXPERIENCE", "Expérience (" + exp.getPoste() + " - " + exp.getEntreprise() + ")"
                ));
            }
        }

        // 2. Projets
        for (Projet p : projets) {
            for (Competence c : p.getCompetences()) {
                map.putIfAbsent(c.getId(), new CandidatDetailDto.CompetenceProfilDto(
                        c.getId(), c.getNom(), c.getCategorie(), c.getStatut(),
                        "PROJET", "Projet (" + p.getTitre() + ")"
                ));
            }
        }

        // 3. Déclaration personnelle directe
        for (Competence c : etudiant.getCompetences()) {
            map.putIfAbsent(c.getId(), new CandidatDetailDto.CompetenceProfilDto(
                    c.getId(), c.getNom(), c.getCategorie(), c.getStatut(),
                    "DECLARATIF", "Déclaration personnelle"
            ));
        }

        return new ArrayList<>(map.values());
    }

    @Override
    @Transactional
    public CandidatureDto changerStatutPourRecruteur(Long candidatureId, StatutCandidature nouveauStatut, Long recruteurId) {
        Candidature cand = candidatureRepository.findById(candidatureId)
                .orElseThrow(() -> new EntityNotFoundException("Candidature introuvable : " + candidatureId));

        Offre offre = cand.getOffre();
        // TODO sécurité côté serveur : seul le recruteur propriétaire de l'offre peut voir ses candidats et changer un statut.
        if (offre.getRecruteur() == null || !Objects.equals(offre.getRecruteur().getId(), recruteurId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé : cette offre appartient à un autre recruteur.");
        }

        return changerStatut(candidatureId, nouveauStatut);
    }

    @Override
    public RecruteurDashboardDto obtenirDashboardRecruteur(Long recruteurId) {
        List<Offre> mesOffres = offreRepository.findByRecruteurIdOrderByDatePublicationDesc(recruteurId);
        long offresActives = mesOffres.stream().filter(o -> o.getStatut() == StatutOffre.ACTIVE).count();
        long offresCloturees = mesOffres.stream().filter(o -> o.getStatut() == StatutOffre.CLOTUREE).count();
        long totalOffres = mesOffres.size();

        List<Candidature> candidatures = candidatureRepository.findByRecruteurIdOrderByDateDesc(recruteurId);
        long totalCandidatures = candidatures.size();
        long aTraiter = candidatures.stream().filter(c -> c.getStatut() == StatutCandidature.ENVOYEE).count();

        Map<StatutCandidature, Long> repartition = new EnumMap<>(StatutCandidature.class);
        for (StatutCandidature s : StatutCandidature.values()) {
            repartition.put(s, 0L);
        }
        for (Candidature c : candidatures) {
            repartition.merge(c.getStatut(), 1L, Long::sum);
        }

        List<CandidatOffreDto> recentes = candidatures.stream()
                .limit(10)
                .map(CandidatOffreDto::from)
                .toList();

        return new RecruteurDashboardDto(
                offresActives,
                offresCloturees,
                totalOffres,
                totalCandidatures,
                aTraiter,
                repartition,
                recentes
        );
    }
}
