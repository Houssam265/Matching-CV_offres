package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.NotificationDto;
import com.gi3.matchingcv.model.Candidature;
import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.Offre;
import com.gi3.matchingcv.model.enums.TypeNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    /**
     * Enregistre une notification puis la pousse en temps réel via WebSocket STOMP
     * après le commit de la transaction courante.
     *
     * @param destinataireId  identifiant de l'utilisateur destinataire
     * @param type            type de notification
     * @param titre           titre court
     * @param message         message en français
     * @param lien            lien relatif de redirection (nullable)
     * @param offreId         identifiant de l'offre associée (nullable)
     * @param candidatureId   identifiant de la candidature associée (nullable)
     * @param competenceId    identifiant de la compétence associée (nullable)
     * @param score           score éventuel (nullable)
     * @param auteurId        identifiant de l'auteur de l'action (nullable)
     * @return la notification créée sous forme de DTO, ou null si ignorée (auto-notification ou doublon)
     */
    NotificationDto notifier(
            Long destinataireId,
            TypeNotification type,
            String titre,
            String message,
            String lien,
            Long offreId,
            Long candidatureId,
            Long competenceId,
            Double score,
            Long auteurId
    );

    /**
     * Surcharge acceptant directement des entités pour les références.
     */
    default NotificationDto notifierAvecEntites(
            Long destinataireId,
            TypeNotification type,
            String titre,
            String message,
            String lien,
            Offre offre,
            Candidature candidature,
            Competence competence,
            Double score,
            Long auteurId
    ) {
        return notifier(
                destinataireId,
                type,
                titre,
                message,
                lien,
                offre != null ? offre.getId() : null,
                candidature != null ? candidature.getId() : null,
                competence != null ? competence.getId() : null,
                score,
                auteurId
        );
    }

    /**
     * Récupère la liste paginée des notifications d'un utilisateur.
     */
    Page<NotificationDto> listerParUtilisateur(Long utilisateurId, boolean nonLuesSeulement, Pageable pageable);

    /**
     * Compte le nombre de notifications non lues d'un utilisateur.
     */
    long compterNonLues(Long utilisateurId);

    /**
     * Marque une notification spécifique comme lue.
     */
    NotificationDto marquerLue(Long notificationId);

    /**
     * Marque toutes les notifications d'un utilisateur comme lues.
     */
    void toutMarquerLu(Long utilisateurId);
}
