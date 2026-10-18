package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.MatchingDetailDto;
import com.gi3.matchingcv.event.*;
import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.enums.StatutCandidature;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.model.enums.TypeNotification;
import com.gi3.matchingcv.repository.EtudiantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private EtudiantRepository etudiantRepository;
    @Mock
    private MatchingService matchingService;

    @InjectMocks
    private NotificationListener listener;

    private Etudiant etudiant69;
    private Etudiant etudiant70;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "seuilOffre", 70.0);

        etudiant69 = new Etudiant();
        etudiant69.setId(1L);
        etudiant69.setNom("Martin");
        etudiant69.setPrenom("Alice");

        etudiant70 = new Etudiant();
        etudiant70.setId(2L);
        etudiant70.setNom("Bernard");
        etudiant70.setPrenom("Lucas");
    }

    @Test
    @DisplayName("onOffrePubliee : seuil respecté (69% non notifié, 70% notifié)")
    void onOffrePubliee_seuilStrict() {
        when(etudiantRepository.findAll()).thenReturn(List.of(etudiant69, etudiant70));

        MatchingDetailDto match69 = mock(MatchingDetailDto.class);
        when(match69.scoreDisponible()).thenReturn(true);
        when(match69.score()).thenReturn(69.0);
        when(matchingService.calculerScore(1L, 100L)).thenReturn(match69);

        MatchingDetailDto match70 = mock(MatchingDetailDto.class);
        when(match70.scoreDisponible()).thenReturn(true);
        when(match70.score()).thenReturn(70.0);
        when(matchingService.calculerScore(2L, 100L)).thenReturn(match70);

        listener.onOffrePubliee(new OffrePublieeEvent(100L, 10L, "Stage Ingénieur"));

        // Alice (69%) ne doit pas recevoir de notification
        verify(notificationService, never()).notifier(
                eq(1L), eq(TypeNotification.OFFRE_CORRESPONDANTE), anyString(), anyString(), anyString(),
                anyLong(), any(), any(), anyDouble(), anyLong()
        );

        // Lucas (70%) doit recevoir la notification avec le pourcentage
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifier(
                eq(2L),
                eq(TypeNotification.OFFRE_CORRESPONDANTE),
                contains("Stage Ingénieur"),
                msgCaptor.capture(),
                eq("/offres-recommandees.html"),
                eq(100L),
                isNull(),
                isNull(),
                eq(70.0),
                eq(10L)
        );

        assertThat(msgCaptor.getValue()).contains("70%");
    }

    @Test
    @DisplayName("onCandidatureCreee : notifie le recruteur avec mention 'très pertinent' si score >= 80, nom et lien sans email")
    void onCandidatureCreee_scoreSuperieur80() {
        CandidatureCreeeEvent event = new CandidatureCreeeEvent(
                50L,
                1L,
                100L,
                10L,
                85.0,
                "Martin",
                "Alice",
                "Dev Fullstack"
        );

        listener.onCandidatureCreee(event);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> lienCaptor = ArgumentCaptor.forClass(String.class);

        verify(notificationService).notifier(
                eq(10L),
                eq(TypeNotification.NOUVELLE_CANDIDATURE),
                eq("Nouvelle candidature reçue"),
                msgCaptor.capture(),
                lienCaptor.capture(),
                eq(100L),
                eq(50L),
                isNull(),
                eq(85.0),
                eq(1L)
        );

        String message = msgCaptor.getValue();
        assertThat(message).contains("Alice Martin");
        assertThat(message).contains("Dev Fullstack");
        assertThat(message).contains("85%");
        assertThat(message).contains("très pertinent");
        assertThat(message).doesNotContain("@");

        assertThat(lienCaptor.getValue()).isEqualTo("/recruteur-candidats.html?offre=100&candidature=50");
    }

    @Test
    @DisplayName("onCandidatureCreee : notifie le recruteur sans 'très pertinent' si score < 80")
    void onCandidatureCreee_scoreInferieur80() {
        CandidatureCreeeEvent event = new CandidatureCreeeEvent(
                51L,
                2L,
                100L,
                10L,
                65.0,
                "Dupont",
                "Bob",
                "Dev Fullstack"
        );

        listener.onCandidatureCreee(event);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifier(
                eq(10L),
                eq(TypeNotification.NOUVELLE_CANDIDATURE),
                eq("Nouvelle candidature reçue"),
                msgCaptor.capture(),
                anyString(),
                anyLong(),
                anyLong(),
                isNull(),
                eq(65.0),
                eq(2L)
        );

        assertThat(msgCaptor.getValue()).doesNotContain("très pertinent");
        assertThat(msgCaptor.getValue()).contains("65%");
    }

    @Test
    @DisplayName("onCandidatureStatutChange : émet CANDIDATURE_VUE, CANDIDATURE_ACCEPTEE et CANDIDATURE_REFUSEE")
    void onCandidatureStatutChange_tousStatuts() {
        // VUE
        listener.onCandidatureStatutChange(new CandidatureStatutChangeEvent(
                10L, 1L, 100L, StatutCandidature.ENVOYEE, StatutCandidature.VUE, "Dev Backend", 10L));
        verify(notificationService).notifier(
                eq(1L), eq(TypeNotification.CANDIDATURE_VUE), contains("consultée"), anyString(),
                eq("/mes-candidatures.html"), eq(100L), eq(10L), isNull(), isNull(), eq(10L));

        // ACCEPTEE
        listener.onCandidatureStatutChange(new CandidatureStatutChangeEvent(
                10L, 1L, 100L, StatutCandidature.VUE, StatutCandidature.ACCEPTEE, "Dev Backend", 10L));
        verify(notificationService).notifier(
                eq(1L), eq(TypeNotification.CANDIDATURE_ACCEPTEE), contains("acceptée"), anyString(),
                eq("/mes-candidatures.html"), eq(100L), eq(10L), isNull(), isNull(), eq(10L));

        // REFUSEE
        listener.onCandidatureStatutChange(new CandidatureStatutChangeEvent(
                10L, 1L, 100L, StatutCandidature.VUE, StatutCandidature.REFUSEE, "Dev Backend", 10L));
        verify(notificationService).notifier(
                eq(1L), eq(TypeNotification.CANDIDATURE_REFUSEE), contains("non retenue"), anyString(),
                eq("/mes-candidatures.html"), eq(100L), eq(10L), isNull(), isNull(), eq(10L));
    }

    @Test
    @DisplayName("onCompetenceStatutChange : notifie l'étudiant lorsque sa compétence est VALIDEE (avec effet score)")
    void onCompetenceStatutChange_validee() {
        CompetenceStatutChangeEvent event = new CompetenceStatutChangeEvent(
                5L, "Docker", StatutCompetence.EN_ATTENTE, StatutCompetence.VALIDEE, 1L);

        listener.onCompetenceStatutChange(event);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifier(
                eq(1L),
                eq(TypeNotification.COMPETENCE_VALIDEE),
                contains("Docker"),
                msgCaptor.capture(),
                eq("/dashboard-etudiant.html"),
                isNull(),
                isNull(),
                eq(5L),
                isNull(),
                isNull()
        );

        assertThat(msgCaptor.getValue()).contains("comptée dans votre score de matching");
    }

    @Test
    @DisplayName("onCompetenceStatutChange : notifie l'étudiant lorsque sa compétence est REJETEE (avec effet ignorée)")
    void onCompetenceStatutChange_rejetee() {
        CompetenceStatutChangeEvent event = new CompetenceStatutChangeEvent(
                6L, "Hacking", StatutCompetence.EN_ATTENTE, StatutCompetence.REJETEE, 1L);

        listener.onCompetenceStatutChange(event);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifier(
                eq(1L),
                eq(TypeNotification.COMPETENCE_REJETEE),
                contains("Hacking"),
                msgCaptor.capture(),
                eq("/dashboard-etudiant.html"),
                isNull(),
                isNull(),
                eq(6L),
                isNull(),
                isNull()
        );

        assertThat(msgCaptor.getValue()).contains("ignorée dans le calcul du score");
    }
}
