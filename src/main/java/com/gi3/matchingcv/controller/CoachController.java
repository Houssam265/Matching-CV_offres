package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.CoachResumeDto;
import com.gi3.matchingcv.dto.ConseilCoachDto;
import com.gi3.matchingcv.dto.SimulationHypotheseDto;
import com.gi3.matchingcv.dto.SimulationResultDto;
import com.gi3.matchingcv.service.CoachService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Endpoints REST pour le module Coach CV.
 * Fournit des conseils chiffrés et actionnables ainsi que des simulations 'et si'.
 */
@RestController
@RequestMapping("/api/etudiants/{id}/coach")
public class CoachController {

    private final CoachService coachService;

    public CoachController(CoachService coachService) {
        this.coachService = coachService;
    }

    /**
     * Récupère la synthèse complète du Coach CV pour l'étudiant (complétion, top conseils, liste complète).
     *
     * @param id identifiant de l'étudiant
     * @return 200 OK avec CoachResumeDto
     */
    @GetMapping
    public ResponseEntity<?> obtenirResumeCoach(@PathVariable Long id) {
        try {
            CoachResumeDto resume = coachService.obtenirResumeCoach(id);
            return ResponseEntity.ok(resume);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Erreur lors de la génération des conseils Coach : " + e.getMessage()));
        }
    }

    /**
     * Récupère uniquement la liste des conseils d'amélioration pour l'étudiant.
     */
    @GetMapping("/conseils")
    public ResponseEntity<?> listerConseilsEtudiant(@PathVariable Long id) {
        try {
            List<ConseilCoachDto> conseils = coachService.genererConseilsEtudiant(id);
            return ResponseEntity.ok(conseils);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Erreur : " + e.getMessage()));
        }
    }

    /**
     * Récupère les conseils spécifiques pour améliorer le score sur une offre donnée.
     *
     * @param id      identifiant de l'étudiant
     * @param offreId identifiant de l'offre cible
     * @return 200 OK avec la liste des conseils chiffrés
     */
    @GetMapping({"/offres/{offreId}", "/offre/{offreId}"})
    public ResponseEntity<?> obtenirConseilsPourOffre(
            @PathVariable Long id,
            @PathVariable Long offreId
    ) {
        try {
            List<ConseilCoachDto> conseils = coachService.genererConseilsPourOffre(id, offreId);
            return ResponseEntity.ok(conseils);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Erreur : " + e.getMessage()));
        }
    }

    /**
     * Simule l'impact d'une compétence ajoutée ou valorisée sur le score d'une offre.
     */
    @GetMapping({"/offres/{offreId}/simuler", "/offre/{offreId}/simuler"})
    public ResponseEntity<?> simulerGain(
            @PathVariable Long id,
            @PathVariable Long offreId,
            @RequestParam Long competenceId,
            @RequestParam(defaultValue = "0.7") Double niveau
    ) {
        try {
            SimulationResultDto resultat = coachService.simulerGain(id, offreId, competenceId, niveau);
            return ResponseEntity.ok(resultat);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Erreur simulation : " + e.getMessage()));
        }
    }

    /**
     * Simulation par batch d'hypothèses en POST (sans persistance).
     */
    @PostMapping({"/offres/{offreId}/simuler", "/offre/{offreId}/simuler"})
    public ResponseEntity<?> simulerScoreBatch(
            @PathVariable Long id,
            @PathVariable Long offreId,
            @RequestBody(required = false) List<SimulationHypotheseDto> hypotheses
    ) {
        try {
            Map<Long, Double> map = hypotheses != null
                    ? hypotheses.stream()
                        .filter(h -> h.competenceId() != null && h.niveauSimule() != null)
                        .collect(java.util.stream.Collectors.toMap(
                                SimulationHypotheseDto::competenceId,
                                SimulationHypotheseDto::niveauSimule,
                                (a, b) -> Math.max(a, b)
                        ))
                    : Collections.emptyMap();
            return ResponseEntity.ok(coachService.simulerScore(id, offreId, map));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Erreur simulation : " + e.getMessage()));
        }
    }
}
