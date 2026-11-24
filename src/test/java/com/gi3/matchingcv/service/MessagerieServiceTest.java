package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.StatutCandidature;
import com.gi3.matchingcv.model.enums.TypeNotification;
import com.gi3.matchingcv.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagerieServiceTest {

    @Mock private MessageRepository messages;
    @Mock private CandidatureRepository candidatures;
    @Mock private UtilisateurRepository utilisateurs;
    @Mock private NotificationRepository notifications;
    @Mock private NotificationService notificationService;
    @InjectMocks private MessagerieService service;

    private Etudiant etudiant;
    private Recruteur recruteur;
    private Candidature candidature;

    @BeforeEach
    void setUp() {
        etudiant = new Etudiant();
        etudiant.setId(1L); etudiant.setPrenom("Alice"); etudiant.setNom("Martin");
        recruteur = new Recruteur();
        recruteur.setId(2L); recruteur.setPrenom("Bob"); recruteur.setNom("Durand"); recruteur.setNomEntreprise("TechCorp");
        Offre offre = new Offre();
        offre.setId(5L); offre.setTitre("Dev Java"); offre.setRecruteur(recruteur);
        candidature = new Candidature();
        candidature.setId(10L); candidature.setEtudiant(etudiant); candidature.setOffre(offre);
        candidature.setStatut(StatutCandidature.VUE);
        lenient().when(candidatures.findById(10L)).thenReturn(Optional.of(candidature));
        lenient().when(messages.save(any())).thenAnswer(inv -> { Message m = inv.getArgument(0); m.setId(99L); return m; });
    }

    private ResponseStatusException erreur(Runnable action) {
        return (ResponseStatusException) org.assertj.core.api.Assertions.catchThrowable(action::run);
    }

    @Test
    @DisplayName("Le recruteur ouvre la conversation : message enregistré et candidat notifié une fois")
    void recruteurEnvoie_notifieLeCandidat() {
        when(utilisateurs.findById(2L)).thenReturn(Optional.of(recruteur));
        when(notifications.existsNonLueParCandidature(1L, TypeNotification.NOUVEAU_MESSAGE, 10L)).thenReturn(false);

        var dto = service.envoyer(10L, 2L, "  Bonjour Alice, disponible pour un échange ?  ");

        assertThat(dto.moi()).isTrue();
        assertThat(candidature.getStatut()).isEqualTo(StatutCandidature.VUE); // déjà VUE : inchangé
        assertThat(dto.contenu()).isEqualTo("Bonjour Alice, disponible pour un échange ?");
        verify(notificationService).notifier(eq(1L), eq(TypeNotification.NOUVEAU_MESSAGE), contains("TechCorp"),
                anyString(), eq("/messages.html?c=10"), eq(5L), eq(10L), isNull(), isNull(), eq(2L));
    }

    @Test
    @DisplayName("Le premier message du recruteur fait passer une candidature ENVOYEE à VUE")
    void messageRecruteurMarqueCandidatureVue() {
        candidature.setStatut(StatutCandidature.ENVOYEE);
        when(utilisateurs.findById(2L)).thenReturn(Optional.of(recruteur));

        service.envoyer(10L, 2L, "Bonjour");

        assertThat(candidature.getStatut()).isEqualTo(StatutCandidature.VUE);
        assertThat(candidature.getDateTraitement()).isNotNull();
        verify(candidatures).save(candidature);
    }

    @Test
    @DisplayName("Pas de nouvelle notification tant que la précédente n'est pas lue")
    void pasDeNotificationEnDouble() {
        when(utilisateurs.findById(2L)).thenReturn(Optional.of(recruteur));
        when(notifications.existsNonLueParCandidature(1L, TypeNotification.NOUVEAU_MESSAGE, 10L)).thenReturn(true);

        service.envoyer(10L, 2L, "Second message");

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Le candidat ne peut pas écrire avant le premier message du recruteur")
    void candidatNePeutPasOuvrirLaConversation() {
        when(messages.existsByCandidatureIdAndExpediteurId(10L, 2L)).thenReturn(false);

        var ex = erreur(() -> service.envoyer(10L, 1L, "Bonjour"));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(messages, never()).save(any());
    }

    @Test
    @DisplayName("Le candidat répond une fois que le recruteur a écrit")
    void candidatRepond() {
        when(messages.existsByCandidatureIdAndExpediteurId(10L, 2L)).thenReturn(true);
        when(utilisateurs.findById(1L)).thenReturn(Optional.of(etudiant));

        var dto = service.envoyer(10L, 1L, "Avec plaisir !");

        assertThat(dto.contenu()).isEqualTo("Avec plaisir !");
        verify(notificationService).notifier(eq(2L), eq(TypeNotification.NOUVEAU_MESSAGE), contains("Alice Martin"),
                anyString(), anyString(), any(), eq(10L), isNull(), isNull(), eq(1L));
    }

    @Test
    @DisplayName("Un tiers n'accède jamais à la conversation")
    void tiersInterdit() {
        assertThat(erreur(() -> service.ouvrir(10L, 77L)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(erreur(() -> service.envoyer(10L, 77L, "Bonjour")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Conversation en lecture seule après un refus")
    void fermeeApresRefus() {
        candidature.setStatut(StatutCandidature.REFUSEE);
        when(messages.existsByCandidatureIdAndExpediteurId(10L, 2L)).thenReturn(true);

        assertThat(erreur(() -> service.envoyer(10L, 2L, "Bonjour")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Message vide ou trop long refusé")
    void validationContenu() {
        assertThat(erreur(() -> service.envoyer(10L, 2L, "   ")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(erreur(() -> service.envoyer(10L, 2L, "x".repeat(2001))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Ouvrir une conversation marque les messages reçus comme lus")
    void ouvrirMarqueLu() {
        Message m = new Message();
        m.setId(1L); m.setCandidature(candidature); m.setExpediteur(recruteur); m.setContenu("Bonjour");
        m.setDateEnvoi(java.time.LocalDateTime.now());
        when(messages.findByCandidatureIdOrderByDateEnvoiAsc(10L)).thenReturn(List.of(m));

        var detail = service.ouvrir(10L, 1L);

        verify(messages).marquerLus(10L, 1L);
        assertThat(detail.messages()).hasSize(1);
        assertThat(detail.messages().get(0).moi()).isFalse();
        assertThat(detail.conversation().peutEcrire()).isTrue();
        assertThat(detail.conversation().interlocuteurNom()).isEqualTo("Bob Durand");
    }
}
