package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.CompetenceAModererDto;
import com.gi3.matchingcv.exception.CompetenceDejaExistanteException;
import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.service.AdminCompetenceService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST de l'espace administrateur.
 * // TODO sécurité : tous les endpoints sont réservés ADMIN (vérification côté serveur à ajouter).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminCompetenceService adminCompetenceService;

    public AdminController(AdminCompetenceService adminCompetenceService) {
        this.adminCompetenceService = adminCompetenceService;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LOT 1 — MODÉRATION DES COMPÉTENCES
    // ──────────────────────────────────────────────────────────────────────────

    /** File EN_ATTENTE avec usage et doublons détectés. */
    // TODO sécurité : réservé ADMIN
    @GetMapping("/competences/a-moderer")
    public ResponseEntity<List<CompetenceAModererDto>> listerAModerer() {
        return ResponseEntity.ok(adminCompetenceService.listerAModerer());
    }

    /**
     * Valide une compétence EN_ATTENTE, avec modifications optionnelles du nom,
     * catégorie et synonymes.
     *
     * @param id       identifiant de la compétence
     * @param adminId  identifiant de l'administrateur (passé en header X-Admin-Id)
     * @param body     {nom?, categorie?, synonymes?[]}
     */
    // TODO sécurité : réservé ADMIN
    @PatchMapping("/competences/{id}/valider")
    public ResponseEntity<?> valider(
            @PathVariable Long id,
            @RequestHeader(value = "X-Admin-Id", required = false) Long adminId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            String nom      = body != null ? (String) body.get("nom")      : null;
            String cat      = body != null ? (String) body.get("categorie"): null;
            @SuppressWarnings("unchecked")
            List<String> syn = body != null ? (List<String>) body.get("synonymes") : null;
            Competence c = adminCompetenceService.validerAvecModifications(id, nom, cat, syn, adminId);
            return ResponseEntity.ok(c);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Rejette une compétence EN_ATTENTE avec un motif optionnel.
     *
     * @param id       identifiant de la compétence
     * @param adminId  identifiant de l'administrateur
     * @param body     {motif?}
     */
    // TODO sécurité : réservé ADMIN
    @PatchMapping("/competences/{id}/rejeter")
    public ResponseEntity<?> rejeter(
            @PathVariable Long id,
            @RequestHeader(value = "X-Admin-Id", required = false) Long adminId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String motif = body != null ? body.get("motif") : null;
            Competence c = adminCompetenceService.rejeterAvecMotif(id, motif, adminId);
            return ResponseEntity.ok(c);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Fusionne la compétence source dans la cible (transaction unique).
     *
     * @param body    {sourceId, cibleId}
     * @param adminId identifiant de l'administrateur
     */
    // TODO sécurité : réservé ADMIN
    @PostMapping("/competences/fusionner")
    public ResponseEntity<?> fusionner(
            @RequestBody Map<String, Long> body,
            @RequestHeader(value = "X-Admin-Id", required = false) Long adminId) {
        try {
            Long sourceId = body.get("sourceId");
            Long cibleId  = body.get("cibleId");
            if (sourceId == null || cibleId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "sourceId et cibleId sont requis."));
            }
            adminCompetenceService.fusionner(sourceId, cibleId, adminId);
            return ResponseEntity.ok(Map.of("message", "Fusion effectuée avec succès."));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", e.getMessage()));
        }
    }

    /** Validation groupée de plusieurs compétences EN_ATTENTE. */
    // TODO sécurité : réservé ADMIN
    @PostMapping("/competences/valider-groupes")
    public ResponseEntity<?> validerGroupes(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Admin-Id", required = false) Long adminId) {
        @SuppressWarnings("unchecked")
        List<Integer> idsRaw = (List<Integer>) body.get("ids");
        if (idsRaw == null || idsRaw.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "La liste d'identifiants est vide."));
        }
        List<Long> ids = idsRaw.stream().map(Integer::longValue).toList();
        adminCompetenceService.validerGroupes(ids, adminId);
        return ResponseEntity.ok(Map.of("message", ids.size() + " compétences traitées."));
    }

    /** Rejet groupé avec motif commun. */
    // TODO sécurité : réservé ADMIN
    @PostMapping("/competences/rejeter-groupes")
    public ResponseEntity<?> rejeterGroupes(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Admin-Id", required = false) Long adminId) {
        @SuppressWarnings("unchecked")
        List<Integer> idsRaw = (List<Integer>) body.get("ids");
        if (idsRaw == null || idsRaw.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "La liste d'identifiants est vide."));
        }
        List<Long> ids = idsRaw.stream().map(Integer::longValue).toList();
        String motif = (String) body.get("motif");
        adminCompetenceService.rejeterGroupes(ids, motif, adminId);
        return ResponseEntity.ok(Map.of("message", ids.size() + " compétences traitées."));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LOT 2 — RÉFÉRENTIEL ENRICHI
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Modifie nom, catégorie et synonymes d'une compétence existante.
     *
     * @param body {nom?, categorie?, synonymes?[]}
     */
    // TODO sécurité : réservé ADMIN
    @PutMapping("/competences/{id}")
    public ResponseEntity<?> modifier(
            @PathVariable Long id,
            @RequestHeader(value = "X-Admin-Id", required = false) Long adminId,
            @RequestBody Map<String, Object> body) {
        try {
            String nom      = (String) body.get("nom");
            String cat      = (String) body.get("categorie");
            @SuppressWarnings("unchecked")
            List<String> syn = (List<String>) body.get("synonymes");
            Competence c = adminCompetenceService.modifier(id, nom, cat, syn, adminId);
            return ResponseEntity.ok(c);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (CompetenceDejaExistanteException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Import idempotent de compétences depuis un fichier CSV (nom;categorie;synonymes). */
    // TODO sécurité : réservé ADMIN
    @PostMapping(value = "/competences/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importerCsv(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Admin-Id", required = false) Long adminId) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Le fichier est vide."));
        }
        try {
            Map<String, Integer> rapport = adminCompetenceService.importerCsv(file.getInputStream(), adminId);
            return ResponseEntity.ok(rapport);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur lors de l'import : " + e.getMessage()));
        }
    }

    /** Export CSV de toutes les compétences VALIDEE. */
    // TODO sécurité : réservé ADMIN
    @GetMapping("/competences/export-csv")
    public ResponseEntity<String> exporterCsv() {
        String csv = adminCompetenceService.exporterCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"competences.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }
}
