package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.CompetenceAttachRequest;
import com.gi3.matchingcv.dto.EtudiantCompetenceDTO;
import com.gi3.matchingcv.exception.CompetenceDejaDansListeException;
import com.gi3.matchingcv.exception.CompetenceDejaExistanteException;
import com.gi3.matchingcv.exception.EmailDejaUtiliseException;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.service.CompetenceService;
import com.gi3.matchingcv.service.EtudiantService;
import com.gi3.matchingcv.service.ExperienceProfessionnelleService;
import com.gi3.matchingcv.service.ProjetService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contrôleur REST exposant les points d'accès de gestion des étudiants et de leur profil.
 * Respecte l'Inversion de Contrôle (IoC) en s'appuyant sur les abstractions injectées via le constructeur.
 *
 * TODO sécurité : restreindre GET /api/etudiants à un rôle ADMIN une fois Spring Security en place.
 * TODO sécurité : pour chaque endpoint /{id}/..., vérifier que {id} correspond à l'étudiant connecté
 *                 (ou à un Admin) avant d'autoriser la modification — non implémentable sans auth.
 */
@RestController
@RequestMapping("/api/etudiants")
public class EtudiantController {

    private final EtudiantService etudiantService;
    private final CompetenceService competenceService;
    private final ProjetService projetService;
    private final ExperienceProfessionnelleService experienceService;

    /**
     * Injection par constructeur de l'interface EtudiantService (IoC / Découplage).
     *
     * @param etudiantService   l'interface de service des étudiants injectée par le conteneur Spring
     * @param competenceService l'interface de service des compétences injectée par le conteneur Spring
     * @param projetService     l'interface de service des projets
     * @param experienceService l'interface de service des expériences
     */
    public EtudiantController(EtudiantService etudiantService, 
                              CompetenceService competenceService,
                              ProjetService projetService,
                              ExperienceProfessionnelleService experienceService) {
        this.etudiantService = etudiantService;
        this.competenceService = competenceService;
        this.projetService = projetService;
        this.experienceService = experienceService;
    }

    // -------------------------------------------------------------------------
    // CRUD Etudiant
    // -------------------------------------------------------------------------

    /**
     * Récupère la liste de tous les étudiants.
     * TODO sécurité : restreindre à un rôle ADMIN une fois Spring Security en place.
     *
     * @return 200 OK avec la liste des étudiants
     */
    @GetMapping
    public ResponseEntity<List<Etudiant>> listerTous() {
        return ResponseEntity.ok(etudiantService.listerTous());
    }

    /**
     * Crée un nouvel étudiant (email, nom, prénom obligatoires).
     * TODO sécurité : le hachage BCrypt du mot de passe sera ajouté avec Spring Security.
     *
     * @param etudiant l'entité étudiant reçue dans le corps de la requête (validée)
     * @return 201 CREATED, ou 409 CONFLICT si l'e-mail est déjà utilisé
     */
    @PostMapping
    public ResponseEntity<?> creer(@Valid @RequestBody Etudiant etudiant) {
        try {
            Etudiant cree = etudiantService.creer(etudiant);
            return ResponseEntity.status(HttpStatus.CREATED).body(cree);
        } catch (EmailDejaUtiliseException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Erreur lors de la création : " + e.getMessage()));
        }
    }

    /**
     * Récupère un étudiant par son identifiant.
     *
     * @param id identifiant de l'étudiant
     * @return 200 OK, ou 404 NOT FOUND si inexistant
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> trouverParId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(etudiantService.trouverParId(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Met à jour les informations du profil de l'étudiant (prénom, nom, filière, établissement).
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> mettreAJour(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            Etudiant etudiant = etudiantService.trouverParId(id);
            if (body.containsKey("prenom") && body.get("prenom") != null) {
                etudiant.setPrenom(((String) body.get("prenom")).trim());
            }
            if (body.containsKey("nom") && body.get("nom") != null) {
                etudiant.setNom(((String) body.get("nom")).trim());
            }
            if (body.containsKey("filiere")) {
                Object f = body.get("filiere");
                etudiant.setFiliere(f != null ? ((String) f).trim() : null);
            }
            if (body.containsKey("etablissement")) {
                Object e = body.get("etablissement");
                etudiant.setEtablissement(e != null ? ((String) e).trim() : null);
            }
            Etudiant sauve = etudiantService.sauvegarder(etudiant);
            return ResponseEntity.ok(sauve);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Erreur lors de la mise à jour : " + e.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // Profil — Compétences déclarées
    // -------------------------------------------------------------------------

    /**
     * Retourne la liste personnelle des compétences de l'étudiant avec statistiques d'utilisation.
     * // TODO sécurité : vérifier que {id} correspond à l'étudiant connecté (ou ADMIN).
     */
    @GetMapping("/{id}/competences")
    public ResponseEntity<?> listerCompetences(@PathVariable Long id) {
        try {
            List<EtudiantCompetenceDTO> dtos = etudiantService.listerCompetencesAvecStats(id);
            return ResponseEntity.ok(dtos);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Ajoute une compétence à la liste personnelle de l'étudiant.
     * Accepte soit {"competenceId": 42} soit {"nouvelleCompetence": "Rust", "categorie": "Backend"}.
     * // TODO sécurité : vérifier que {id} correspond à l'étudiant connecté (ou ADMIN).
     *
     * @param id      identifiant de l'étudiant
     * @param request corps de la requête
     * @return 200 OK avec la compétence ajoutée, 409 CONFLICT si déjà dans sa liste ou doublon
     */
    @PostMapping("/{id}/competences")
    public ResponseEntity<?> ajouterCompetence(@PathVariable Long id,
                                               @RequestBody CompetenceAttachRequest request) {
        try {
            Competence competence = etudiantService.ajouterCompetence(id, request);
            return ResponseEntity.ok(competence);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (CompetenceDejaDansListeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (CompetenceDejaExistanteException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Retire une compétence de la liste personnelle de l'étudiant et la détache de ses projets/expériences.
     * Ne supprime jamais la compétence du dictionnaire.
     * // TODO sécurité : vérifier que {id} correspond à l'étudiant connecté (ou ADMIN).
     */
    @DeleteMapping("/{id}/competences/{competenceId}")
    public ResponseEntity<?> retirerCompetence(@PathVariable Long id,
                                               @PathVariable Long competenceId) {
        try {
            etudiantService.retirerCompetence(id, competenceId);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Suggère des compétences pour l'autocomplétion (8 résultats max).
     * // TODO sécurité : vérifier que {id} correspond à l'étudiant connecté (ou ADMIN).
     */
    @GetMapping("/{id}/competences/suggestions")
    public ResponseEntity<?> suggererCompetences(@PathVariable Long id,
                                                 @RequestParam(value = "q", required = false, defaultValue = "") String q) {
        try {
            List<Competence> suggestions = etudiantService.suggererCompetences(id, q);
            return ResponseEntity.ok(suggestions);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // Profil — Projets
    // -------------------------------------------------------------------------

    /**
     * Retourne la liste des projets de l'étudiant.
     */
    @GetMapping("/{id}/projets")
    public ResponseEntity<?> listerProjets(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(projetService.listerParEtudiant(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Ajoute un projet au profil de l'étudiant.
     * Le corps JSON peut contenir {"competenceId": X} ou {"nouvelleCompetence": "...", "categorie": "..."}
     * dans un champ "competences" pour attacher des compétences au projet créé.
     */
    @PostMapping("/{id}/projets")
    public ResponseEntity<?> ajouterProjet(@PathVariable Long id,
                                           @RequestBody Map<String, Object> body) {
        try {
            Etudiant etudiant = etudiantService.trouverParId(id);

            String titre = (String) body.get("titre");
            if (titre == null || titre.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Le titre du projet est obligatoire."));
            }
            String description = (String) body.get("description");

            Projet projet = new Projet();
            projet.setTitre(titre.trim());
            projet.setDescription(description);
            projet.setEtudiant(etudiant);

            // Compétences optionnelles attachées à la création
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> competencesRaw =
                    body.get("competences") instanceof List ? (List<Map<String, Object>>) body.get("competences") : null;
            if (competencesRaw != null) {
                boolean etudiantModifie = false;
                for (Map<String, Object> compRaw : competencesRaw) {
                    CompetenceAttachRequest req = mapToCompetenceAttachRequest(compRaw);
                    Competence competence = resolveCompetence(req, etudiant);
                    if (!projet.getCompetences().contains(competence)) {
                        projet.getCompetences().add(competence);
                    }
                    if (!etudiant.getCompetences().contains(competence)) {
                        etudiant.getCompetences().add(competence);
                        etudiantModifie = true;
                    }
                }
                if (etudiantModifie) {
                    etudiantService.sauvegarder(etudiant);
                }
            }

            Projet sauve = projetService.creer(projet);
            return ResponseEntity.status(HttpStatus.CREATED).body(sauve);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (CompetenceDejaExistanteException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Supprime un projet du profil de l'étudiant.
     */
    @DeleteMapping("/{id}/projets/{projetId}")
    public ResponseEntity<?> supprimerProjet(@PathVariable Long id,
                                             @PathVariable Long projetId) {
        try {
            projetService.supprimer(id, projetId);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // Profil — Expériences professionnelles
    // -------------------------------------------------------------------------

    /**
     * Retourne la liste des expériences professionnelles de l'étudiant.
     */
    @GetMapping("/{id}/experiences")
    public ResponseEntity<?> listerExperiences(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(experienceService.listerParEtudiant(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/experiences")
    public ResponseEntity<?> ajouterExperience(@PathVariable Long id,
                                               @RequestBody Map<String, Object> body) {
        try {
            Etudiant etudiant = etudiantService.trouverParId(id);

            String poste = (String) body.get("poste");
            String entreprise = (String) body.get("entreprise");
            String dateDebutStr = (String) body.get("dateDebut");
            String dateFinStr = (String) body.get("dateFin");

            if (poste == null || poste.isBlank() || entreprise == null || entreprise.isBlank() || dateDebutStr == null || dateDebutStr.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Poste, entreprise et date de début sont obligatoires."));
            }

            LocalDate dateDebut;
            try {
                dateDebut = LocalDate.parse(dateDebutStr);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Format de date de début invalide."));
            }

            LocalDate dateFin = null;
            if (dateFinStr != null && !dateFinStr.isBlank()) {
                try {
                    dateFin = LocalDate.parse(dateFinStr);
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Format de date de fin invalide."));
                }
                if (dateFin.isBefore(dateDebut)) {
                    return ResponseEntity.badRequest().body(Map.of("message", "La date de fin ne peut pas être antérieure à la date de début."));
                }
            }

            ExperienceProfessionnelle experience = new ExperienceProfessionnelle();
            experience.setPoste(poste.trim());
            experience.setEntreprise(entreprise.trim());
            experience.setDateDebut(dateDebut);
            experience.setDateFin(dateFin);
            experience.setEtudiant(etudiant);

            // Compétences optionnelles attachées à la création
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> competencesRaw =
                    body.get("competences") instanceof List ? (List<Map<String, Object>>) body.get("competences") : null;
            if (competencesRaw != null) {
                boolean etudiantModifie = false;
                for (Map<String, Object> compRaw : competencesRaw) {
                    CompetenceAttachRequest req = mapToCompetenceAttachRequest(compRaw);
                    Competence competence = resolveCompetence(req, etudiant);
                    if (!experience.getCompetences().contains(competence)) {
                        experience.getCompetences().add(competence);
                    }
                    if (!etudiant.getCompetences().contains(competence)) {
                        etudiant.getCompetences().add(competence);
                        etudiantModifie = true;
                    }
                }
                if (etudiantModifie) {
                    etudiantService.sauvegarder(etudiant);
                }
            }

            ExperienceProfessionnelle sauve = experienceService.creer(experience);
            return ResponseEntity.status(HttpStatus.CREATED).body(sauve);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Supprime une expérience professionnelle du profil de l'étudiant.
     */
    @DeleteMapping("/{id}/experiences/{experienceId}")
    public ResponseEntity<?> supprimerExperience(@PathVariable Long id,
                                                 @PathVariable Long experienceId) {
        try {
            experienceService.supprimer(id, experienceId);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // Liaisons Projets / Expériences <-> Compétences
    // -------------------------------------------------------------------------

    /**
     * Associe une compétence à un projet existant de l'étudiant.
     */
    @PostMapping("/{id}/projets/{projetId}/competences")
    public ResponseEntity<?> lierCompetenceProjet(@PathVariable Long id,
                                                   @PathVariable Long projetId,
                                                   @RequestBody CompetenceAttachRequest request) {
        try {
            Etudiant etudiant = etudiantService.trouverParId(id);
            Projet projet = projetService.listerParEtudiant(id).stream()
                    .filter(p -> p.getId().equals(projetId))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Projet " + projetId + " introuvable pour l'étudiant " + id));

            Competence competence = resolveCompetence(request, etudiant);
            if (!projet.getCompetences().contains(competence)) {
                projet.getCompetences().add(competence);
                projetService.creer(projet);
            }
            if (!etudiant.getCompetences().contains(competence)) {
                etudiant.getCompetences().add(competence);
                etudiantService.sauvegarder(etudiant);
            }
            return ResponseEntity.ok(projet);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Dissocie une compétence d'un projet existant de l'étudiant.
     */
    @DeleteMapping("/{id}/projets/{projetId}/competences/{competenceId}")
    public ResponseEntity<?> dissocierCompetenceProjet(@PathVariable Long id,
                                                        @PathVariable Long projetId,
                                                        @PathVariable Long competenceId) {
        try {
            Projet projet = projetService.listerParEtudiant(id).stream()
                    .filter(p -> p.getId().equals(projetId))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Projet " + projetId + " introuvable pour l'étudiant " + id));

            projet.getCompetences().removeIf(c -> c.getId().equals(competenceId));
            projetService.creer(projet);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Associe une compétence à une expérience existante de l'étudiant.
     */
    @PostMapping("/{id}/experiences/{expId}/competences")
    public ResponseEntity<?> lierCompetenceExperience(@PathVariable Long id,
                                                      @PathVariable Long expId,
                                                      @RequestBody CompetenceAttachRequest request) {
        try {
            Etudiant etudiant = etudiantService.trouverParId(id);
            ExperienceProfessionnelle exp = experienceService.listerParEtudiant(id).stream()
                    .filter(e -> e.getId().equals(expId))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Expérience " + expId + " introuvable pour l'étudiant " + id));

            Competence competence = resolveCompetence(request, etudiant);
            if (!exp.getCompetences().contains(competence)) {
                exp.getCompetences().add(competence);
                experienceService.creer(exp);
            }
            if (!etudiant.getCompetences().contains(competence)) {
                etudiant.getCompetences().add(competence);
                etudiantService.sauvegarder(etudiant);
            }
            return ResponseEntity.ok(exp);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Dissocie une compétence d'une expérience existante de l'étudiant.
     */
    @DeleteMapping("/{id}/experiences/{expId}/competences/{competenceId}")
    public ResponseEntity<?> dissocierCompetenceExperience(@PathVariable Long id,
                                                           @PathVariable Long expId,
                                                           @PathVariable Long competenceId) {
        try {
            ExperienceProfessionnelle exp = experienceService.listerParEtudiant(id).stream()
                    .filter(e -> e.getId().equals(expId))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Expérience " + expId + " introuvable pour l'étudiant " + id));

            exp.getCompetences().removeIf(c -> c.getId().equals(competenceId));
            experienceService.creer(exp);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // Méthodes utilitaires privées
    // -------------------------------------------------------------------------

    /**
     * Résout une compétence à partir d'une {@link CompetenceAttachRequest} :
     * - si {@code competenceId} est renseigné : recherche la compétence existante
     * - sinon : recherche par nom normalisé existant ; si trouvée, la retourne ; sinon propose via CompetenceService.proposer()
     */
    private Competence resolveCompetence(CompetenceAttachRequest request, Etudiant etudiant) {
        if (request.getCompetenceId() != null) {
            return competenceService.trouverParId(request.getCompetenceId());
        }
        if (request.getNouvelleCompetence() != null && !request.getNouvelleCompetence().isBlank()) {
            String norm = Competence.normaliserNom(request.getNouvelleCompetence());
            Optional<Competence> existante = competenceService.trouverParNomNormalise(norm);
            if (existante.isPresent()) {
                return existante.get();
            }
            return competenceService.proposer(request.getNouvelleCompetence().trim(), request.getCategorie(), etudiant);
        }
        throw new IllegalArgumentException(
            "Fournissez soit un 'competenceId' existant, soit un 'nouvelleCompetence' à proposer."
        );
    }

    /**
     * Convertit une Map brute (issue du body JSON) en {@link CompetenceAttachRequest}.
     */
    private CompetenceAttachRequest mapToCompetenceAttachRequest(Map<String, Object> raw) {
        CompetenceAttachRequest req = new CompetenceAttachRequest();
        if (raw.get("competenceId") instanceof Number n) {
            req.setCompetenceId(n.longValue());
        }
        if (raw.get("nouvelleCompetence") instanceof String s) {
            req.setNouvelleCompetence(s);
        }
        if (raw.get("categorie") instanceof String s) {
            req.setCategorie(s);
        }
        return req;
    }
}
