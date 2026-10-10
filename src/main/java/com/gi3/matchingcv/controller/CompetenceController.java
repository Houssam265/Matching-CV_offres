package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.exception.CompetenceDejaExistanteException;
import com.gi3.matchingcv.exception.CompetenceUtiliseeException;
import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.service.CompetenceService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST exposant les points d'accès de gestion des compétences.
 * Respecte l'Inversion de Contrôle (IoC) en s'appuyant sur l'abstraction (CompetenceService)
 * injectée via son constructeur.
 */
@RestController
@RequestMapping("/api/competences")
public class CompetenceController {

    private final CompetenceService competenceService;

    /**
     * Injection par constructeur de l'interface CompetenceService (IoC / Découplage) :
     * Le contrôleur dépend de l'abstraction (l'interface) et non de l'implémentation concrète,
     * respectant ainsi le principe d'inversion des dépendances (DIP) et l'IoC de Spring.
     *
     * @param competenceService l'interface de service des compétences injectée par le conteneur Spring
     */
    public CompetenceController(CompetenceService competenceService) {
        this.competenceService = competenceService;
    }

    /**
     * Récupère la liste de toutes les compétences.
     *
     * @return 200 OK avec la liste des compétences
     */
    @GetMapping
    public ResponseEntity<List<Competence>> listerToutes() {
        List<Competence> competences = competenceService.listerToutes();
        return ResponseEntity.ok(competences);
    }

    /**
     * Récupère une compétence par son identifiant unique.
     *
     * @param id identifiant de la compétence
     * @return 200 OK avec la compétence si trouvée, ou 404 NOT FOUND si inexistante
     */
    @GetMapping("/{id}")
    public ResponseEntity<Competence> trouverParId(@PathVariable Long id) {
        try {
            Competence competence = competenceService.trouverParId(id);
            return ResponseEntity.ok(competence);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Crée une nouvelle compétence à partir d'un objet JSON {nom, categorie}.
     * Valide la présence obligatoire du nom (@NotBlank), renvoie 400 Bad Request sinon.
     *
     * @param competence l'entité compétence reçue dans le corps de la requête
     * @param bindingResult résultat des validations Bean Validation
     * @return 201 CREATED avec la compétence créée, ou 400 BAD REQUEST en cas d'erreur
     */
    @PostMapping
    public ResponseEntity<?> creer(@Valid @RequestBody Competence competence, BindingResult bindingResult) {
        if (bindingResult.hasErrors() || competence.getNom() == null || competence.getNom().trim().isEmpty()) {
            String message = bindingResult.hasErrors() && bindingResult.getFieldError() != null
                    ? bindingResult.getFieldError().getDefaultMessage()
                    : "Le nom de la compétence est obligatoire.";
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }

        try {
            Competence nouvelleCompetence = competenceService.creer(competence);
            return ResponseEntity.status(HttpStatus.CREATED).body(nouvelleCompetence);
        } catch (CompetenceDejaExistanteException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Erreur lors de la création : " + e.getMessage()));
        }
    }

    /**
     * Supprime une compétence du référentiel par son identifiant unique.
     * // TODO sécurité : réservé ADMIN.
     *
     * @param id identifiant de la compétence à supprimer
     * @return 204 NO CONTENT si succès, 404 NOT FOUND si inexistante, 409 CONFLICT si utilisée, ou 400 BAD REQUEST en cas d'erreur
     */
    // TODO sécurité : réservé ADMIN
    @DeleteMapping("/{id}")
    public ResponseEntity<?> supprimer(@PathVariable Long id) {
        try {
            competenceService.supprimer(id);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (CompetenceUtiliseeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Erreur lors de la suppression : " + e.getMessage()));
        }
    }

    /**
     * Propose une nouvelle compétence avec le statut EN_ATTENTE.
     * Accepte un corps JSON {"nom": "...", "categorie": "..."}.
     *
     * @param body map contenant les clés "nom" et optionnellement "categorie"
     * @return 201 CREATED avec la compétence proposée, ou 409 CONFLICT si doublon
     */
    @PostMapping("/proposer")
    public ResponseEntity<?> proposer(@RequestBody Map<String, String> body) {
        String nom = body.get("nom");
        if (nom == null || nom.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Le nom de la compétence est obligatoire."));
        }
        String categorie = body.get("categorie");
        try {
            Competence proposee = competenceService.proposer(nom, categorie);
            return ResponseEntity.status(HttpStatus.CREATED).body(proposee);
        } catch (CompetenceDejaExistanteException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Erreur lors de la proposition : " + e.getMessage()));
        }
    }
}
