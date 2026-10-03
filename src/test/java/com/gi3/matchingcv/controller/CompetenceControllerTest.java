package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.service.CompetenceService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompetenceController.class)
class CompetenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompetenceService competenceService;

    private Competence competence1;

    @BeforeEach
    void setUp() {
        competence1 = new Competence();
        competence1.setId(1L);
        competence1.setNom("Spring Boot");
        competence1.setCategorie("Backend");
        competence1.setStatut(StatutCompetence.VALIDEE);
    }

    @Test
    @DisplayName("GET /api/competences doit renvoyer le statut 200 et la liste des compétences")
    void testListerToutes() throws Exception {
        when(competenceService.listerToutes()).thenReturn(Arrays.asList(competence1));

        mockMvc.perform(get("/api/competences")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nom", is("Spring Boot")))
                .andExpect(jsonPath("$[0].categorie", is("Backend")));
    }

    @Test
    @DisplayName("GET /api/competences/{id} doit renvoyer 200 lorsque la compétence existe")
    void testTrouverParIdExistant() throws Exception {
        when(competenceService.trouverParId(1L)).thenReturn(competence1);

        mockMvc.perform(get("/api/competences/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.nom", is("Spring Boot")));
    }

    @Test
    @DisplayName("GET /api/competences/{id} doit renvoyer 404 lorsque la compétence est absente")
    void testTrouverParIdInexistant() throws Exception {
        when(competenceService.trouverParId(99L))
                .thenThrow(new EntityNotFoundException("Compétence introuvable avec l'identifiant : 99"));

        mockMvc.perform(get("/api/competences/99")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/competences doit renvoyer 201 lorsque les données sont valides")
    void testCreerValide() throws Exception {
        when(competenceService.creer(any(Competence.class))).thenReturn(competence1);

        String jsonPayload = """
                {
                    "nom": "Spring Boot",
                    "categorie": "Backend"
                }
                """;

        mockMvc.perform(post("/api/competences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.nom", is("Spring Boot")));
    }

    @Test
    @DisplayName("POST /api/competences doit renvoyer 400 lorsque le nom est absent ou vide")
    void testCreerNomInvalide() throws Exception {
        String jsonPayload = """
                {
                    "nom": "",
                    "categorie": "Backend"
                }
                """;

        mockMvc.perform(post("/api/competences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/competences/{id} doit renvoyer 204 lorsque la compétence est supprimée")
    void testSupprimerSucces() throws Exception {
        doNothing().when(competenceService).supprimer(1L);

        mockMvc.perform(delete("/api/competences/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(competenceService, times(1)).supprimer(1L);
    }

    @Test
    @DisplayName("DELETE /api/competences/{id} doit renvoyer 404 lorsque la compétence est introuvable")
    void testSupprimerInexistant() throws Exception {
        doThrow(new EntityNotFoundException("Compétence introuvable")).when(competenceService).supprimer(99L);

        mockMvc.perform(delete("/api/competences/{id}", 99L))
                .andExpect(status().isNotFound());

        verify(competenceService, times(1)).supprimer(99L);
    }
}

