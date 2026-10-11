package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.CandidatureDto;
import com.gi3.matchingcv.dto.MatchingDetailDto;
import com.gi3.matchingcv.model.Candidature;
import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.Offre;
import com.gi3.matchingcv.model.enums.StatutCandidature;
import com.gi3.matchingcv.model.enums.StatutOffre;
import com.gi3.matchingcv.repository.CandidatureRepository;
import com.gi3.matchingcv.repository.EtudiantRepository;
import com.gi3.matchingcv.repository.OffreRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    public CandidatureServiceImpl(CandidatureRepository candidatureRepository,
                                  EtudiantRepository etudiantRepository,
                                  OffreRepository offreRepository,
                                  MatchingService matchingService) {
        this.candidatureRepository = candidatureRepository;
        this.etudiantRepository    = etudiantRepository;
        this.offreRepository       = offreRepository;
        this.matchingService       = matchingService;
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

        return CandidatureDto.from(candidatureRepository.save(cand));
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
        return CandidatureDto.from(candidatureRepository.save(cand));
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
}
