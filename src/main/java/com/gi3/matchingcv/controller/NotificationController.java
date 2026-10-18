package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.NotificationDto;
import com.gi3.matchingcv.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Contrôleur REST pour la gestion et la consultation des notifications.
 *
 * // TODO sécurité : un utilisateur n'accède qu'aux siennes.
 */
@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Récupère la liste paginée des notifications pour un utilisateur.
     *
     * // TODO sécurité : un utilisateur n'accède qu'aux siennes.
     */
    @GetMapping("/api/utilisateurs/{id}/notifications")
    public ResponseEntity<?> listerNotifications(
            @PathVariable("id") Long id,
            @RequestParam(name = "nonLues", defaultValue = "false") boolean nonLues,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "dateCreation"));
            Page<NotificationDto> result = notificationService.listerParUtilisateur(id, nonLues, pageable);
            return ResponseEntity.ok(result);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Compte le nombre de notifications non lues pour un utilisateur.
     *
     * // TODO sécurité : un utilisateur n'accède qu'aux siennes.
     */
    @GetMapping("/api/utilisateurs/{id}/notifications/non-lues/count")
    public ResponseEntity<?> compterNonLues(@PathVariable("id") Long id) {
        try {
            long count = notificationService.compterNonLues(id);
            return ResponseEntity.ok(Map.of("count", count));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur lors du comptage des notifications : " + e.getMessage()));
        }
    }

    /**
     * Marque une notification spécifique comme lue.
     *
     * // TODO sécurité : un utilisateur n'accède qu'aux siennes.
     */
    @PatchMapping("/api/notifications/{id}/lue")
    public ResponseEntity<?> marquerLue(@PathVariable("id") Long id) {
        try {
            NotificationDto updated = notificationService.marquerLue(id);
            return ResponseEntity.ok(updated);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Marque toutes les notifications non lues d'un utilisateur comme lues.
     *
     * // TODO sécurité : un utilisateur n'accède qu'aux siennes.
     */
    @PatchMapping("/api/utilisateurs/{id}/notifications/tout-lire")
    public ResponseEntity<?> toutMarquerLu(@PathVariable("id") Long id) {
        try {
            notificationService.toutMarquerLu(id);
            return ResponseEntity.ok(Map.of("message", "Toutes les notifications ont été marquées comme lues."));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }
}
