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
     * Si recruteurId est fourni, applique le contrôle propriétaire et renvoie CandidatOffreDto.
     */
    @GetMapping("/api/offres/{id}/candidatures")
    public ResponseEntity<?> listerParOffre(
            @PathVariable Long id,
            @RequestParam(required = false) Long recruteurId,
            @RequestParam(required = false) com.gi3.matchingcv.model.enums.StatutCandidature statut) {
        try {
            if (recruteurId != null) {
                // Usage recruteur propriétaire avec classement par score et masquage d'email
                List<com.gi3.matchingcv.dto.CandidatOffreDto> dtos = candidatureService.listerCandidatsPourRecruteur(id, recruteurId, statut);
                return ResponseEntity.ok(dtos);
            }
            List<CandidatureDto> dtos = candidatureService.listerParOffre(id);
            return ResponseEntity.ok(dtos);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        }
    }

    /**
     * Candidats d'une offre pour son recruteur propriétaire (classés par score décroissant).
     * GET /api/offres/{id}/candidats?recruteurId={recruteurId}
     */
    @GetMapping("/api/offres/{id}/candidats")
    public ResponseEntity<?> listerCandidats(
            @PathVariable Long id,
            @RequestParam Long recruteurId,
            @RequestParam(required = false) com.gi3.matchingcv.model.enums.StatutCandidature statut) {
        try {
            List<com.gi3.matchingcv.dto.CandidatOffreDto> dtos = candidatureService.listerCandidatsPourRecruteur(id, recruteurId, statut);
            return ResponseEntity.ok(dtos);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        }
    }

    /**
     * Détail complet d'un candidat pour une candidature et son explication de score.
     * GET /api/candidatures/{id}?recruteurId={recruteurId}
     * Passage automatique à VUE si la candidature était ENVOYEE.
     */
    @GetMapping({"/api/candidatures/{id}", "/api/candidatures/{id}/detail"})
    public ResponseEntity<?> obtenirDetailCandidat(
            @PathVariable Long id,
            @RequestParam Long recruteurId) {
        try {
            com.gi3.matchingcv.dto.CandidatDetailDto detail = candidatureService.obtenirDetailCandidat(id, recruteurId);
            return ResponseEntity.ok(detail);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        }
    }

    /**
     * Changer le statut d'une candidature.
     * PATCH /api/candidatures/{id}/statut   body: { "statut": "ACCEPTEE" }
     * Si recruteurId est fourni, vérifie que le recruteur est propriétaire de l'offre.
     */
    @PatchMapping("/api/candidatures/{id}/statut")
    public ResponseEntity<?> changerStatut(
            @PathVariable Long id,
            @RequestParam(required = false) Long recruteurId,
            @RequestBody Map<String, String> body) {
        String statutStr = body.get("statut");
        if (statutStr == null || statutStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Le champ 'statut' est obligatoire."));
        }
        Long rId = recruteurId;
        if (rId == null && body.containsKey("recruteurId")) {
            try {
                rId = Long.valueOf(body.get("recruteurId"));
            } catch (Exception ignored) {}
        }
        try {
            com.gi3.matchingcv.model.enums.StatutCandidature statut =
                    com.gi3.matchingcv.model.enums.StatutCandidature.valueOf(statutStr.trim().toUpperCase());
            CandidatureDto dto = (rId != null)
                    ? candidatureService.changerStatutPourRecruteur(id, statut, rId)
                    : candidatureService.changerStatut(id, statut);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Statut inconnu : " + statutStr));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Tableau de bord recruteur (offres, candidatures, répartition et récentes).
     * GET /api/recruteurs/{id}/dashboard
     */
    @GetMapping("/api/recruteurs/{id}/dashboard")
    public ResponseEntity<?> dashboard(@PathVariable Long id) {
        try {
            com.gi3.matchingcv.dto.RecruteurDashboardDto dashboard = candidatureService.obtenirDashboardRecruteur(id);
            return ResponseEntity.ok(dashboard);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        }
    }
}
