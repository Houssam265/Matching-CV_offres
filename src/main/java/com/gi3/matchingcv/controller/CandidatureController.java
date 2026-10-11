package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.CandidatureDto;
import com.gi3.matchingcv.service.CandidatureService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Endpoints REST pour les candidatures.
 *
 * TODO sécurité :
 *   - POST   /api/candidatures                    → étudiant connecté seulement
 *   - GET    /api/etudiants/{id}/candidatures      → étudiant connecté ou Admin
 *   - GET    /api/offres/{id}/candidatures         → recruteur propriétaire ou Admin
 *   - PATCH  /api/candidatures/{id}/statut         → recruteur propriétaire de l'offre
 *   - GET    /api/etudiants/{eId}/candidatures/{oId} → étudiant connecté
 */
@RestController
public class CandidatureController {

    private final CandidatureService candidatureService;

    public CandidatureController(CandidatureService candidatureService) {
        this.candidatureService = candidatureService;
    }

    /**
     * Postuler à une offre.
     * POST /api/candidatures?etudiantId={eId}&offreId={oId}
     */
    @PostMapping("/api/candidatures")
    public ResponseEntity<?> postuler(
            @RequestParam Long etudiantId,
            @RequestParam Long offreId) {
        try {
            CandidatureDto dto = candidatureService.postuler(etudiantId, offreId);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Erreur lors de la candidature : " + e.getMessage()));
        }
    }

    /**
     * Candidatures d'un étudiant (triées par date décroissante).
     * GET /api/etudiants/{id}/candidatures
     */
    @GetMapping("/api/etudiants/{id}/candidatures")
    public ResponseEntity<?> listerParEtudiant(@PathVariable Long id) {
        try {
            List<CandidatureDto> dtos = candidatureService.listerParEtudiant(id);
            return ResponseEntity.ok(dtos);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Nombre de candidatures d'un étudiant (pour le compteur du tableau de bord).
     * GET /api/etudiants/{id}/candidatures/count
     */
    @GetMapping("/api/etudiants/{id}/candidatures/count")
    public ResponseEntity<?> compterParEtudiant(@PathVariable Long id) {
        try {
            long count = candidatureService.compterParEtudiant(id);
            return ResponseEntity.ok(Map.of("count", count));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("count", 0));
        }
    }

    /**
     * Candidature d'un étudiant pour une offre précise (pour savoir s'il a déjà postulé).
     * GET /api/etudiants/{eId}/candidatures/offre/{oId}
     */
    @GetMapping("/api/etudiants/{eId}/candidatures/offre/{oId}")
    public ResponseEntity<?> trouverParEtudiantEtOffre(
            @PathVariable Long eId,
            @PathVariable Long oId) {
        Optional<CandidatureDto> dto = candidatureService.trouverParEtudiantEtOffre(eId, oId);
        return dto.<ResponseEntity<?>>map(ResponseEntity::ok)
                  .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Aucune candidature trouvée.")));
    }

    /**
     * Candidatures reçues pour une offre (usage recruteur — triées par score décroissant).
     * GET /api/offres/{id}/candidatures
     */
    @GetMapping("/api/offres/{id}/candidatures")
    public ResponseEntity<?> listerParOffre(@PathVariable Long id) {
        try {
            List<CandidatureDto> dtos = candidatureService.listerParOffre(id);
            return ResponseEntity.ok(dtos);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Changer le statut d'une candidature.
     * PATCH /api/candidatures/{id}/statut   body: { "statut": "VUE" }
     */
    @PatchMapping("/api/candidatures/{id}/statut")
    public ResponseEntity<?> changerStatut(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String statutStr = body.get("statut");
        if (statutStr == null || statutStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Le champ 'statut' est obligatoire."));
        }
        try {
            com.gi3.matchingcv.model.enums.StatutCandidature statut =
                    com.gi3.matchingcv.model.enums.StatutCandidature.valueOf(statutStr.trim().toUpperCase());
            CandidatureDto dto = candidatureService.changerStatut(id, statut);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Statut inconnu : " + statutStr));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }
}
