package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.NotificationDto;
import com.gi3.matchingcv.model.enums.TypeNotification;
import com.gi3.matchingcv.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    @DisplayName("GET /api/utilisateurs/{id}/notifications : retourne la page de notifications")
    void listerNotifications_ok() throws Exception {
        NotificationDto dto = new NotificationDto(
                1L,
                42L,
                TypeNotification.OFFRE_CORRESPONDANTE,
                "Nouvelle offre",
                "Offre à 80%",
                "/offres-recommandees.html",
                false,
                LocalDateTime.now(),
                80.0,
                100L,
                null,
                null
        );

        when(notificationService.listerParUtilisateur(eq(42L), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/utilisateurs/42/notifications?page=0&size=10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(1)))
                .andExpect(jsonPath("$.content[0].titre", is("Nouvelle offre")))
                .andExpect(jsonPath("$.content[0].type", is("OFFRE_CORRESPONDANTE")))
                .andExpect(jsonPath("$.content[0].lue", is(false)));
    }

    @Test
    @DisplayName("GET /api/utilisateurs/{id}/notifications/non-lues/count : retourne le nombre de non lues")
    void compterNonLues_ok() throws Exception {
        when(notificationService.compterNonLues(42L)).thenReturn(7L);

        mockMvc.perform(get("/api/utilisateurs/42/notifications/non-lues/count")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(7)));
    }

    @Test
    @DisplayName("PATCH /api/notifications/{id}/lue : marque la notification lue et renvoie le DTO")
    void marquerLue_ok() throws Exception {
        NotificationDto dto = new NotificationDto(
                5L,
                42L,
                TypeNotification.CANDIDATURE_ACCEPTEE,
                "Acceptée !",
                "Félicitations",
                "/mes-candidatures.html",
                true,
                LocalDateTime.now(),
                null,
                null,
                10L,
                null
        );

        when(notificationService.marquerLue(5L)).thenReturn(dto);

        mockMvc.perform(patch("/api/notifications/5/lue")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(5)))
                .andExpect(jsonPath("$.lue", is(true)))
                .andExpect(jsonPath("$.type", is("CANDIDATURE_ACCEPTEE")));
    }

    @Test
    @DisplayName("PATCH /api/notifications/{id}/lue : 404 si introuvable")
    void marquerLue_notFound() throws Exception {
        when(notificationService.marquerLue(999L))
                .thenThrow(new EntityNotFoundException("Notification introuvable : 999"));

        mockMvc.perform(patch("/api/notifications/999/lue")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Notification introuvable : 999")));
    }

    @Test
    @DisplayName("PATCH /api/utilisateurs/{id}/notifications/tout-lire : 200 OK avec message")
    void toutMarquerLu_ok() throws Exception {
        doNothing().when(notificationService).toutMarquerLu(42L);

        mockMvc.perform(patch("/api/utilisateurs/42/notifications/tout-lire")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Toutes les notifications ont été marquées comme lues.")));

        verify(notificationService).toutMarquerLu(42L);
    }
}
