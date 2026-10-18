package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.CoachResumeDto;
import com.gi3.matchingcv.dto.ConseilCoachDto;
import com.gi3.matchingcv.dto.SimulationResultDto;
import com.gi3.matchingcv.model.enums.TypeConseilCoach;
import com.gi3.matchingcv.service.CoachService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CoachController.class)
class CoachControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CoachService coachService;

    @Test
    @DisplayName("GET /api/etudiants/{id}/coach renvoie la synthèse globale")
    void testObtenirResumeCoach() throws Exception {
        ConseilCoachDto c1 = new ConseilCoachDto(
                "c1", TypeConseilCoach.VALORISATION, "Lier Docker à un projet",
                "Gain de 15 points", 15.0, 101L, "Docker",
                20L, "Développeur Java", "Capgemini",
                "LIER_PROJET", "Lier à un projet", "/dashboard-etudiant.html?open=profil",
                0.4, 0.7, 1
        );

        CoachResumeDto resume = new CoachResumeDto(
                1L, 75, 15.0, 1, List.of(c1), List.of(c1), 0, 0, 0, 0
        );

        when(coachService.obtenirResumeCoach(1L)).thenReturn(resume);

        mockMvc.perform(get("/api/etudiants/1/coach")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etudiantId", is(1)))
                .andExpect(jsonPath("$.scoreCompletionProfil", is(75)))
                .andExpect(jsonPath("$.gainPotentielMax", is(15.0)))
                .andExpect(jsonPath("$.topConseils", hasSize(1)))
                .andExpect(jsonPath("$.topConseils[0].titre", is("Lier Docker à un projet")));
    }

    @Test
    @DisplayName("GET /api/etudiants/{id}/coach/offres/{offreId} renvoie les conseils pour une offre")
    void testObtenirConseilsPourOffre() throws Exception {
        ConseilCoachDto c1 = new ConseilCoachDto(
                "c1", TypeConseilCoach.OFFRE_COMPETENCE, "Ajouter Kubernetes",
                "Kubernetes est requise", 20.0, 102L, "Kubernetes",
                20L, "DevOps Engineer", "Cloud SA",
                "AJOUTER_COMPETENCE", "Ajouter la compétence", "/competences.html",
                0.0, 0.7, 1
        );

        when(coachService.genererConseilsPourOffre(1L, 20L)).thenReturn(List.of(c1));

        mockMvc.perform(get("/api/etudiants/1/coach/offres/20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].competenceNom", is("Kubernetes")))
                .andExpect(jsonPath("$[0].gainPoints", is(20.0)));
    }

    @Test
    @DisplayName("GET /api/etudiants/{id}/coach/offres/{offreId}/simuler renvoie le gain simulé")
    void testSimulerGain() throws Exception {
        SimulationResultDto sim = new SimulationResultDto(
                1L, 20L, 40.0, 60.0, 20.0, null
        );

        when(coachService.simulerGain(1L, 20L, 102L, 0.7)).thenReturn(sim);

        mockMvc.perform(get("/api/etudiants/1/coach/offres/20/simuler?competenceId=102&niveau=0.7")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreActuel", is(40.0)))
                .andExpect(jsonPath("$.scoreSimule", is(60.0)))
                .andExpect(jsonPath("$.gain", is(20.0)));
    }
}
