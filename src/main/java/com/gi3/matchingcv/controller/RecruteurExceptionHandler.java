package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.exception.CompetenceDejaExistanteException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/** Gestion limitée au module recruteur : les contrôleurs étudiants restent inchangés. */
@RestControllerAdvice(assignableTypes = {OffreController.class, RecruteurController.class})
public class RecruteurExceptionHandler {
    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<?> introuvable(EntityNotFoundException e) { return erreur(404, e.getMessage()); }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalide(IllegalArgumentException e) { return erreur(400, e.getMessage()); }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> statut(ResponseStatusException e) { return erreur(e.getStatusCode().value(), e.getReason()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        var field = e.getBindingResult().getFieldErrors().get(0);
        return erreur(400, field.getField() + " : " + field.getDefaultMessage());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, CompetenceDejaExistanteException.class})
    ResponseEntity<?> conflit(RuntimeException e) {
        return erreur(409, "Une donnée existe déjà ou a changé. Actualisez puis réessayez.");
    }

    private ResponseEntity<?> erreur(int statut, String message) {
        return ResponseEntity.status(statut).body(Map.of("message", message == null ? "Requête impossible." : message));
    }
}
