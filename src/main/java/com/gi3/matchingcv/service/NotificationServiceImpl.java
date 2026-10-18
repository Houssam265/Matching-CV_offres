package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.NotificationDto;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.TypeNotification;
import com.gi3.matchingcv.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final OffreRepository offreRepository;
    private final CandidatureRepository candidatureRepository;
    private final CompetenceRepository competenceRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            UtilisateurRepository utilisateurRepository,
            OffreRepository offreRepository,
            CandidatureRepository candidatureRepository,
            CompetenceRepository competenceRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.notificationRepository = notificationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.offreRepository = offreRepository;
        this.candidatureRepository = candidatureRepository;
        this.competenceRepository = competenceRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public NotificationDto notifier(
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
    ) {
        if (destinataireId == null) {
            log.warn("Tentative d'envoi d'une notification sans destinataireId.");
            return null;
        }

        // Règle métier : jamais de notification à l'auteur de sa propre action
        if (auteurId != null && Objects.equals(destinataireId, auteurId)) {
            log.debug("Notification ignorée : le destinataire ({}) est l'auteur de l'action.", destinataireId);
            return null;
        }

        // Règle métier : aucun doublon sur OFFRE_CORRESPONDANTE pour une même paire (étudiant, offre)
        if (type == TypeNotification.OFFRE_CORRESPONDANTE && offreId != null) {
            if (notificationRepository.existsByDestinataireIdAndTypeAndOffreId(destinataireId, type, offreId)) {
                log.debug("Notification OFFRE_CORRESPONDANTE ignorée : doublon détecté pour utilisateur {} et offre {}.",
                        destinataireId, offreId);
                return null;
            }
        }

        Utilisateur destinataire = utilisateurRepository.findById(destinataireId)
                .orElseThrow(() -> new EntityNotFoundException("Destinataire introuvable avec l'id : " + destinataireId));

        Offre offre = (offreId != null) ? offreRepository.findById(offreId).orElse(null) : null;
        Candidature candidature = (candidatureId != null) ? candidatureRepository.findById(candidatureId).orElse(null) : null;
        Competence competence = (competenceId != null) ? competenceRepository.findById(competenceId).orElse(null) : null;

        Notification notification = new Notification();
        notification.setDestinataire(destinataire);
        notification.setType(type);
        notification.setTitre(titre);
        notification.setMessage(message);
        notification.setLien(lien);
        notification.setOffre(offre);
        notification.setCandidature(candidature);
        notification.setCompetence(competence);
        notification.setScore(score);
        notification.setLue(false);
        notification.setDateCreation(LocalDateTime.now());

        Notification saved = notificationRepository.save(notification);
        NotificationDto dto = NotificationDto.from(saved);

        // Envoi temps réel via WebSocket STOMP après le commit de la transaction
        diffuserApresCommit(destinataireId, dto);

        return dto;
    }

    @Override
    public NotificationDto notifierAvecEntites(
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

    private void diffuserApresCommit(Long destinataireId, NotificationDto dto) {
        Runnable diffusion = () -> {
            try {
                // file personnelle : /user/{id}/queue/notifications
                messagingTemplate.convertAndSendToUser(
                        String.valueOf(destinataireId),
                        "/queue/notifications",
                        dto
                );
                log.debug("Notification poussée en temps réel à l'utilisateur {} : [{}] {}", destinataireId, dto.type(), dto.titre());
            } catch (Exception e) {
                log.error("Erreur lors de la diffusion WebSocket de la notification à l'utilisateur {} : {}",
                        destinataireId, e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    diffusion.run();
                }
            });
        } else {
            diffusion.run();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDto> listerParUtilisateur(Long utilisateurId, boolean nonLuesSeulement, Pageable pageable) {
        if (!utilisateurRepository.existsById(utilisateurId)) {
            throw new EntityNotFoundException("Utilisateur introuvable : " + utilisateurId);
        }

        Page<Notification> page = nonLuesSeulement
                ? notificationRepository.findByDestinataireIdAndLueFalseOrderByDateCreationDesc(utilisateurId, pageable)
                : notificationRepository.findByDestinataireIdOrderByDateCreationDesc(utilisateurId, pageable);

        return page.map(NotificationDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public long compterNonLues(Long utilisateurId) {
        return notificationRepository.countByDestinataireIdAndLueFalse(utilisateurId);
    }

    @Override
    public NotificationDto marquerLue(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Notification introuvable : " + notificationId));

        if (!notification.isLue()) {
            notification.setLue(true);
            notification = notificationRepository.save(notification);
        }

        return NotificationDto.from(notification);
    }

    @Override
    public void toutMarquerLu(Long utilisateurId) {
        if (!utilisateurRepository.existsById(utilisateurId)) {
            throw new EntityNotFoundException("Utilisateur introuvable : " + utilisateurId);
        }
        notificationRepository.toutMarquerLu(utilisateurId);
    }
}
