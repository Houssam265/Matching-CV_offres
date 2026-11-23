package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.exception.CompetenceDejaExistanteException;
import com.gi3.matchingcv.model.Administrateur;
import com.gi3.matchingcv.model.AuditLog;
import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.enums.Role;
import com.gi3.matchingcv.model.enums.StatutOffre;
import com.gi3.matchingcv.service.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST de l'espace administrateur.
 * Accès réservé au rôle ADMIN : contrôlé par {@link com.gi3.matchingcv.security.ResourceAccess}
 * (refus par défaut des chemins /api/admin pour les autres rôles).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminCompetenceService adminCompetenceService;
    private final AdminUtilisateurService adminUtilisateurService;
    private final AdminOffreService adminOffreService;
    private final AdminStatistiquesService adminStatistiquesService;
    private final AuditService auditService;

    public AdminController(
            AdminCompetenceService adminCompetenceService,
            AdminUtilisateurService adminUtilisateurService,
            AdminOffreService adminOffreService,
            AdminStatistiquesService adminStatistiquesService,
            AuditService auditService
    ) {
        this.adminCompetenceService = adminCompetenceService;
        this.adminUtilisateurService = adminUtilisateurService;
        this.adminOffreService = adminOffreService;
        this.adminStatistiquesService = adminStatistiquesService;
        this.auditService = auditService;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LOT 1 — MODÉRATION DES COMPÉTENCES
    // ──────────────────────────────────────────────────────────────────────────

    /** File EN_ATTENTE de la plus ancienne à la plus récente avec filtres rôle et recherche. */
        @GetMapping({"/moderation", "/competences/a-moderer"})
    public ResponseEntity<List<CompetenceAModererDto>> listerModeration(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String recherche) {
        return ResponseEntity.ok(adminCompetenceService.listerAModerer(role, recherche));
    }

    /** Nombre de compétences EN_ATTENTE pour le badge de menu. */
        @GetMapping("/moderation/count")
    public ResponseEntity<Map<String, Object>> countModeration() {
        long count = adminCompetenceService.countAModerer();
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Valide une compétence EN_ATTENTE, avec modifications optionnelles du nom,
     * catégorie et synonymes.
     */
        @RequestMapping(value = "/competences/{id}/valider", method = {RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<?> valider(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            String nom      = body != null ? (String) body.get("nom")      : null;
            String cat      = body != null ? (String) body.get("categorie"): null;
            @SuppressWarnings("unchecked")
            List<String> syn = body != null ? (List<String>) body.get("synonymes") : null;
            Competence c = adminCompetenceService.validerAvecModifications(id, nom, cat, syn, adminCourant());
            return ResponseEntity.ok(c);
        } catch (com.gi3.matchingcv.exception.CompetenceDejaTraiteeException | com.gi3.matchingcv.exception.CompetenceDejaExistanteException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Rejette une compétence EN_ATTENTE avec un motif optionnel.
     */
        @RequestMapping(value = "/competences/{id}/rejeter", method = {RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<?> rejeter(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String motif = body != null ? body.get("motif") : null;
            Competence c = adminCompetenceService.rejeterAvecMotif(id, motif, adminCourant());
            return ResponseEntity.ok(c);
        } catch (com.gi3.matchingcv.exception.CompetenceDejaTraiteeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Fusionne la compétence source {id} dans la cible (cibleId).
     */
        @PostMapping("/competences/{id}/fusionner")
    public ResponseEntity<?> fusionnerParId(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            Object rawCible = body != null ? body.get("cibleId") : null;
            if (rawCible == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "cibleId est requis."));
            }
            Long cibleId = (rawCible instanceof Number n) ? n.longValue() : Long.parseLong(rawCible.toString());
            adminCompetenceService.fusionner(id, cibleId, adminCourant());
            return ResponseEntity.ok(Map.of("success", true, "message", "Fusion effectuée avec succès.", "cibleId", cibleId));
        } catch (com.gi3.matchingcv.exception.CompetenceDejaTraiteeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Fusionne la compétence source dans la cible (compatibilité avec corps {sourceId, cibleId}).
     */
        @PostMapping("/competences/fusionner")
    public ResponseEntity<?> fusionner(
            @RequestBody Map<String, Object> body) {
        try {
            Object rawSource = body != null ? body.get("sourceId") : null;
            Object rawCible  = body != null ? body.get("cibleId") : null;
            if (rawSource == null || rawCible == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "sourceId et cibleId sont requis."));
            }
            Long sourceId = (rawSource instanceof Number n) ? n.longValue() : Long.parseLong(rawSource.toString());
            Long cibleId  = (rawCible instanceof Number n) ? n.longValue() : Long.parseLong(rawCible.toString());
            adminCompetenceService.fusionner(sourceId, cibleId, adminCourant());
            return ResponseEntity.ok(Map.of("message", "Fusion effectuée avec succès."));
        } catch (com.gi3.matchingcv.exception.CompetenceDejaTraiteeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", e.getMessage()));
        }
    }

    /** Validation groupée de plusieurs compétences EN_ATTENTE. */
        @PostMapping({"/competences/valider-lot", "/competences/valider-groupes"})
    public ResponseEntity<?> validerLot(
            @RequestBody Object body) {
        List<Long> ids = extraireIds(body);
        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "La liste d'identifiants est vide."));
        }
        adminCompetenceService.validerGroupes(ids, adminCourant());
        return ResponseEntity.ok(Map.of("success", true, "message", ids.size() + " compétences traitées.", "count", ids.size()));
    }

    /** Rejet groupé avec motif commun. */
        @PostMapping({"/competences/rejeter-lot", "/competences/rejeter-groupes"})
    public ResponseEntity<?> rejeterLot(
            @RequestBody Object body) {
        List<Long> ids = extraireIds(body);
        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "La liste d'identifiants est vide."));
        }
        String motif = null;
        if (body instanceof Map<?, ?> map && map.get("motif") instanceof String m) {
            motif = m;
        }
        adminCompetenceService.rejeterGroupes(ids, motif, adminCourant());
        return ResponseEntity.ok(Map.of("success", true, "message", ids.size() + " compétences traitées.", "count", ids.size()));
    }

    /**
     * Administrateur à l'origine de l'action, lu depuis la session serveur.
     * Ne jamais faire confiance à un identifiant envoyé par le navigateur (journal d'audit).
     */
    private Long adminCourant() {
        return com.gi3.matchingcv.security.Sessions.currentIdOrNull();
    }

    private List<Long> extraireIds(Object body) {
        if (body instanceof List<?> list) {
            return list.stream()
                    .filter(Number.class::isInstance)
                    .map(n -> ((Number) n).longValue())
                    .toList();
        } else if (body instanceof Map<?, ?> map && map.get("ids") instanceof List<?> list) {
            return list.stream()
                    .filter(Number.class::isInstance)
                    .map(n -> ((Number) n).longValue())
                    .toList();
        }
        return List.of();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LOT 2 — RÉFÉRENTIEL ENRICHI
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Modifie nom, catégorie et synonymes d'une compétence existante.
     */
        @PutMapping("/competences/{id}")
    public ResponseEntity<?> modifier(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            String nom      = (String) body.get("nom");
            String cat      = (String) body.get("categorie");
            @SuppressWarnings("unchecked")
            List<String> syn = (List<String>) body.get("synonymes");
            Competence c = adminCompetenceService.modifier(id, nom, cat, syn, adminCourant());
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
        @PostMapping(value = "/competences/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importerCsv(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Le fichier est vide."));
        }
        try {
            Map<String, Integer> rapport = adminCompetenceService.importerCsv(file.getInputStream(), adminCourant());
            return ResponseEntity.ok(rapport);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur lors de l'import : " + e.getMessage()));
        }
    }

    /** Export CSV de toutes les compétences VALIDEE. */
        @GetMapping("/competences/export-csv")
    public ResponseEntity<String> exporterCsv() {
        String csv = adminCompetenceService.exporterCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"competences.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LOT 3 — UTILISATEURS
    // ──────────────────────────────────────────────────────────────────────────

    /** Liste des utilisateurs avec filtres et recherche. */
        @GetMapping("/utilisateurs")
    public ResponseEntity<List<UtilisateurAdminDto>> listerUtilisateurs(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean actif,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(adminUtilisateurService.listerUtilisateurs(role, actif, q));
    }

    /** Fiche détaillée d'un utilisateur. */
        @GetMapping("/utilisateurs/{id}")
    public ResponseEntity<?> obtenirDetailUtilisateur(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adminUtilisateurService.obtenirDetail(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    /** Suspendre ou réactiver un utilisateur. */
        @PatchMapping("/utilisateurs/{id}/statut")
    public ResponseEntity<?> changerStatutUtilisateur(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        Boolean actif = body != null ? body.get("actif") : null;
        if (actif == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Le champ 'actif' est requis."));
        }
        try {
            adminUtilisateurService.changerStatut(id, actif, adminCourant());
            return ResponseEntity.ok(Map.of("message", "Statut utilisateur mis à jour avec succès.", "actif", actif));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Création d'un compte administrateur. */
        @PostMapping("/utilisateurs/admin")
    public ResponseEntity<?> creerAdmin(
            @Valid @RequestBody AdminCreationRequest request) {
        try {
            Administrateur admin = adminUtilisateurService.creerAdmin(request, adminCourant());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id", admin.getId(),
                    "prenom", admin.getPrenom(),
                    "nom", admin.getNom(),
                    "email", admin.getEmail(),
                    "role", admin.getRole(),
                    "message", "Compte administrateur créé avec succès."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LOT 4 — OFFRES, STATISTIQUES, JOURNAL
    // ──────────────────────────────────────────────────────────────────────────

    /** Liste des offres pour l'administration avec filtres. */
        @GetMapping("/offres")
    public ResponseEntity<List<AdminOffreDto>> listerOffres(
            @RequestParam(required = false) StatutOffre statut,
            @RequestParam(required = false) String domaine,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(adminOffreService.listerOffres(statut, domaine, q));
    }

    /** Clôture d'une offre par l'administrateur avec motif. */
        @PatchMapping("/offres/{id}/cloturer")
    public ResponseEntity<?> cloturerOffre(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String motif = body != null ? body.get("motif") : null;
            adminOffreService.cloturerOffre(id, motif, adminCourant());
            return ResponseEntity.ok(Map.of("message", "Offre clôturée avec succès."));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Statistiques globales de la plateforme. */
        @GetMapping("/statistiques")
    public ResponseEntity<AdminStatsDto> obtenirStatistiques() {
        return ResponseEntity.ok(adminStatistiquesService.obtenirStatistiques());
    }

    /** Journal d'audit avec filtres. */
        @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> listerAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(auditService.listerLogs(action, q));
    }
}
