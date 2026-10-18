package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.Notification;
import com.gi3.matchingcv.model.enums.TypeNotification;

import java.time.LocalDateTime;

public record NotificationDto(
        Long id,
        Long destinataireId,
        TypeNotification type,
        String titre,
        String message,
        String lien,
        boolean lue,
        LocalDateTime dateCreation,
        Double score,
        Long offreId,
        Long candidatureId,
        Long competenceId
) {
    public static NotificationDto from(Notification n) {
        if (n == null) {
            return null;
        }
        return new NotificationDto(
                n.getId(),
                n.getDestinataire() != null ? n.getDestinataire().getId() : null,
                n.getType(),
                n.getTitre(),
                n.getMessage(),
                n.getLien(),
                n.isLue(),
                n.getDateCreation(),
                n.getScore(),
                n.getOffreId(),
                n.getCandidatureId(),
                n.getCompetenceId()
        );
    }
}
