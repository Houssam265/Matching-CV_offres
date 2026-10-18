package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.NotificationDto;
import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.Notification;
import com.gi3.matchingcv.model.Offre;
import com.gi3.matchingcv.model.Recruteur;
import com.gi3.matchingcv.model.enums.TypeNotification;
import com.gi3.matchingcv.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private OffreRepository offreRepository;
    @Mock
    private CandidatureRepository candidatureRepository;
    @Mock
    private CompetenceRepository competenceRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Etudiant etudiant1;
    private Etudiant etudiant2;
    private Recruteur recruteur;
    private Offre offre;

    @BeforeEach
    void setUp() {
        etudiant1 = new Etudiant();
        etudiant1.setId(1L);
        etudiant1.setNom("Martin");
        etudiant1.setPrenom("Alice");
        etudiant1.setEmail("alice@test.com");

        etudiant2 = new Etudiant();
        etudiant2.setId(2L);
        etudiant2.setNom("Dupont");
        etudiant2.setPrenom("Bob");
        etudiant2.setEmail("bob@test.com");

        recruteur = new Recruteur();
        recruteur.setId(10L);
        recruteur.setNomEntreprise("TechCorp");

        offre = new Offre();
        offre.setId(100L);
        offre.setTitre("Dev Java");
        offre.setRecruteur(recruteur);
    }

    @Test
    @DisplayName("notifier : enregistre la notification et tente le push WebSocket vers /user/{id}/queue/notifications")
    void notifier_succesEtPush() {
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(etudiant1));

        Notification saved = new Notification();
        saved.setId(50L);
        saved.setDestinataire(etudiant1);
        saved.setType(TypeNotification.OFFRE_CORRESPONDANTE);
        saved.setTitre("Nouvelle offre");
        saved.setMessage("Offre correspondante");
        saved.setLue(false);
        saved.setDateCreation(LocalDateTime.now());
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        NotificationDto dto = notificationService.notifier(
                1L,
                TypeNotification.OFFRE_CORRESPONDANTE,
                "Nouvelle offre",
                "Offre correspondante",
                "/offres.html",
                100L,
                null,
                null,
                85.0,
                10L // auteur = recruteur
        );

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(50L);
        assertThat(dto.destinataireId()).isEqualTo(1L);
        verify(notificationRepository).save(any(Notification.class));
        verify(messagingTemplate).convertAndSendToUser(eq("1"), eq("/queue/notifications"), any(NotificationDto.class));
    }

    @Test
    @DisplayName("notifier : jamais de notification à l'auteur de sa propre action")
    void notifier_auteurEgaleDestinataire_ignore() {
        NotificationDto dto = notificationService.notifier(
                1L,
                TypeNotification.CANDIDATURE_VUE,
                "Action personnelle",
                "Message",
                "/lien",
                null,
                null,
                null,
                null,
                1L // auteur == destinataire
        );

        assertThat(dto).isNull();
        verifyNoInteractions(utilisateurRepository, notificationRepository, messagingTemplate);
    }

    @Test
    @DisplayName("notifier : anti-doublon strict sur OFFRE_CORRESPONDANTE pour une même paire (étudiant, offre)")
    void notifier_antiDoublon_offreCorrespondante() {
        when(notificationRepository.existsByDestinataireIdAndTypeAndOffreId(1L, TypeNotification.OFFRE_CORRESPONDANTE, 100L))
                .thenReturn(true);

        NotificationDto dto = notificationService.notifier(
                1L,
                TypeNotification.OFFRE_CORRESPONDANTE,
                "Offre Dev",
                "Correspondance 80%",
                "/offres",
                100L,
                null,
                null,
                80.0,
                10L
        );

        assertThat(dto).isNull();
        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("listerParUtilisateur : respecte l'isolation des utilisateurs (utilisateur 1 ne voit que ses notifications)")
    void listerParUtilisateur_isolation() {
        when(utilisateurRepository.existsById(1L)).thenReturn(true);

        Notification n1 = new Notification();
        n1.setId(101L);
        n1.setDestinataire(etudiant1);
        n1.setType(TypeNotification.OFFRE_CORRESPONDANTE);
        n1.setTitre("Offre pour Alice");
        n1.setMessage("Msg");
        n1.setDateCreation(LocalDateTime.now());

        Pageable pageable = PageRequest.of(0, 10);
        when(notificationRepository.findByDestinataireIdOrderByDateCreationDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(n1), pageable, 1));

        Page<NotificationDto> result = notificationService.listerParUtilisateur(1L, false, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).destinataireId()).isEqualTo(1L);
        verify(notificationRepository).findByDestinataireIdOrderByDateCreationDesc(1L, pageable);
        verify(notificationRepository, never()).findByDestinataireIdOrderByDateCreationDesc(eq(2L), any());
    }

    @Test
    @DisplayName("compterNonLues : renvoie le compte exact des notifications non lues")
    void compterNonLues_ok() {
        when(notificationRepository.countByDestinataireIdAndLueFalse(1L)).thenReturn(5L);

        long count = notificationService.compterNonLues(1L);
        assertThat(count).isEqualTo(5L);
        verify(notificationRepository).countByDestinataireIdAndLueFalse(1L);
    }

    @Test
    @DisplayName("marquerLue : passe le flag lue à true et persiste")
    void marquerLue_ok() {
        Notification n = new Notification();
        n.setId(77L);
        n.setDestinataire(etudiant1);
        n.setLue(false);
        n.setDateCreation(LocalDateTime.now());
        when(notificationRepository.findById(77L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationDto updated = notificationService.marquerLue(77L);

        assertThat(updated.lue()).isTrue();
        assertThat(n.isLue()).isTrue();
        verify(notificationRepository).save(n);
    }

    @Test
    @DisplayName("toutMarquerLu : déclenche la mise à jour globale en base pour l'utilisateur")
    void toutMarquerLu_ok() {
        when(utilisateurRepository.existsById(1L)).thenReturn(true);

        notificationService.toutMarquerLu(1L);

        verify(notificationRepository).toutMarquerLu(1L);
    }
}
