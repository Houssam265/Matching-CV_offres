package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.CandidatDetailDto;
import com.gi3.matchingcv.dto.CandidatOffreDto;
import com.gi3.matchingcv.dto.CandidatureDto;
import com.gi3.matchingcv.dto.OffreStatistiquesDto;
import com.gi3.matchingcv.dto.RecruteurDashboardDto;
import com.gi3.matchingcv.model.enums.StatutCandidature;
import com.gi3.matchingcv.service.CandidatureService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
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
            @RequestParam(required = false) StatutCandidature statut,
            @RequestParam(required = false) Boolean favoris,
            @RequestParam(required = false) Double scoreMin,
            @RequestParam(required = false, defaultValue = "score") String tri) {
        try {
            if (recruteurId != null) {
                boolean hasAdvancedFilters = (favoris != null && favoris) || scoreMin != null || (tri != null && !"score".equalsIgnoreCase(tri));
                List<CandidatOffreDto> dtos = hasAdvancedFilters
                        ? candidatureService.listerCandidatsPourRecruteur(id, recruteurId, statut, favoris, scoreMin, tri)
                        : candidatureService.listerCandidatsPourRecruteur(id, recruteurId, statut);
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
     * Candidats d'une offre pour son recruteur propriétaire avec filtres et tri.
     * GET /api/offres/{id}/candidats?recruteurId={recruteurId}
     */
    @GetMapping("/api/offres/{id}/candidats")
    public ResponseEntity<?> listerCandidats(
            @PathVariable Long id,
            @RequestParam Long recruteurId,
            @RequestParam(required = false) StatutCandidature statut,
            @RequestParam(required = false) Boolean favoris,
            @RequestParam(required = false) Double scoreMin,
            @RequestParam(required = false, defaultValue = "score") String tri) {
        try {
            boolean hasAdvancedFilters = (favoris != null && favoris) || scoreMin != null || (tri != null && !"score".equalsIgnoreCase(tri));
            List<CandidatOffreDto> dtos = hasAdvancedFilters
                    ? candidatureService.listerCandidatsPourRecruteur(id, recruteurId, statut, favoris, scoreMin, tri)
                    : candidatureService.listerCandidatsPourRecruteur(id, recruteurId, statut);
            return ResponseEntity.ok(dtos);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        }
    }

    /**
     * Export CSV de la liste des candidats d'une offre pour le recruteur propriétaire.
     * Colonnes : Nom, Prénom, Filière, Établissement, Score, Statut, Date, Email (email uniquement si ACCEPTEE).
     */
    @GetMapping(value = "/api/offres/{id}/candidats/export-csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> exporterCsvCandidats(
            @PathVariable Long id,
            @RequestParam Long recruteurId,
            @RequestParam(required = false) StatutCandidature statut,
            @RequestParam(required = false) Boolean favoris,
            @RequestParam(required = false) Double scoreMin,
            @RequestParam(required = false, defaultValue = "score") String tri) {
        try {
            List<CandidatOffreDto> dtos = candidatureService.listerCandidatsPourRecruteur(id, recruteurId, statut, favoris, scoreMin, tri);

            StringBuilder sb = new StringBuilder();
            sb.append("Nom,Prenom,Filiere,Etablissement,Score,Statut,Date,Email\n");
            for (CandidatOffreDto c : dtos) {
                sb.append(escapeCsv(c.nom())).append(",")
                  .append(escapeCsv(c.prenom())).append(",")
                  .append(escapeCsv(c.filiere())).append(",")
                  .append(escapeCsv(c.etablissement())).append(",")
                  .append(c.score() != null ? String.format(Locale.ROOT, "%.1f", c.score()) : "").append(",")
                  .append(c.statut() != null ? c.statut().name() : "").append(",")
                  .append(c.dateCandidature() != null ? c.dateCandidature().toString() : "").append(",")
                  .append(escapeCsv(c.email())) // Jamais d'email avant acceptation (garanti par c.email() null)
                  .append("\n");
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"candidats-offre-" + id + ".csv\"")
                    .body(sb.toString());
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Offre introuvable");
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    /**
     * Statistiques d'une offre (entonnoir, score moyen, tranches, délai moyen de traitement).
     * GET /api/offres/{id}/statistiques?recruteurId={recruteurId}
     */
    @GetMapping("/api/offres/{id}/statistiques")
    public ResponseEntity<?> obtenirStatistiquesOffre(
            @PathVariable Long id,
            @RequestParam Long recruteurId) {
        try {
            OffreStatistiquesDto stats = candidatureService.obtenirStatistiquesOffre(id, recruteurId);
            return ResponseEntity.ok(stats);
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
            CandidatDetailDto detail = candidatureService.obtenirDetailCandidat(id, recruteurId);
            return ResponseEntity.ok(detail);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        }
    }

    /**
     * Changer le statut d'une candidature avec message optionnel au candidat.
     * PATCH /api/candidatures/{id}/statut   body: { "statut": "ACCEPTEE", "message": "Félicitations..." }
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
        String message = body.get("message");
        try {
            StatutCandidature statut = StatutCandidature.valueOf(statutStr.trim().toUpperCase());
            CandidatureDto dto;
            if (rId != null) {
                dto = (message != null && !message.isBlank())
                        ? candidatureService.changerStatutPourRecruteur(id, statut, rId, message)
                        : candidatureService.changerStatutPourRecruteur(id, statut, rId);
            } else {
                dto = candidatureService.changerStatut(id, statut);
            }
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
     * Basculer l'état favori (étoile) d'une candidature.
     * PATCH /api/candidatures/{id}/favori?recruteurId={recruteurId}
     */
    @PatchMapping("/api/candidatures/{id}/favori")
    public ResponseEntity<?> basculerFavori(
            @PathVariable Long id,
            @RequestParam Long recruteurId) {
        try {
            CandidatureDto dto = candidatureService.basculerFavori(id, recruteurId);
            return ResponseEntity.ok(dto);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        }
    }

    /**
     * Définir ou modifier la note interne privée d'une candidature.
     * PATCH /api/candidatures/{id}/note?recruteurId={recruteurId}   body: { "note": "Profil intéressant..." }
     */
    @PatchMapping("/api/candidatures/{id}/note")
    public ResponseEntity<?> definirNoteInterne(
            @PathVariable Long id,
            @RequestParam Long recruteurId,
            @RequestBody Map<String, String> body) {
        String note = body != null ? (body.get("note") != null ? body.get("note") : body.get("noteInterne")) : null;
        try {
            CandidatureDto dto = candidatureService.definirNoteInterne(id, recruteurId, note);
            return ResponseEntity.ok(dto);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        }
    }

    /**
     * Tableau de bord recruteur (offres, candidatures, répartition, récentes, 7j/30j, compétences demandées, offres inactives).
     * GET /api/recruteurs/{id}/dashboard
     */
    @GetMapping("/api/recruteurs/{id}/dashboard")
    public ResponseEntity<?> dashboard(@PathVariable Long id) {
        try {
            RecruteurDashboardDto dashboard = candidatureService.obtenirDashboardRecruteur(id);
            return ResponseEntity.ok(dashboard);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        }
    }
}
