package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.EtudiantCompetenceDTO;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.service.CompetenceService;
import com.gi3.matchingcv.service.EtudiantService;
import com.gi3.matchingcv.service.ExperienceProfessionnelleService;
import com.gi3.matchingcv.service.ProjetService;
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

@WebMvcTest(EtudiantController.class)
class EtudiantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EtudiantService etudiantService;

    @MockBean
    private CompetenceService competenceService;

    @MockBean
    private ProjetService projetService;

    @MockBean
    private ExperienceProfessionnelleService experienceService;

    @MockBean
    private com.gi3.matchingcv.service.MatchingService matchingService;

    @Test
    @DisplayName("GET /api/etudiants/{id}/competences doit renvoyer 200 et les DTOs sans boucle JSON")
    void testListerCompetencesEtudiant() throws Exception {
        EtudiantCompetenceDTO dto1 = new EtudiantCompetenceDTO(1L, "Spring Boot", "Backend", StatutCompetence.VALIDEE, 2, 1);
        EtudiantCompetenceDTO dto2 = new EtudiantCompetenceDTO(2L, "React", "Frontend", StatutCompetence.VALIDEE, 0, 0);

        when(etudiantService.listerCompetencesAvecStats(1L)).thenReturn(List.of(dto1, dto2));

        mockMvc.perform(get("/api/etudiants/1/competences")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].nom", is("Spring Boot")))
                .andExpect(jsonPath("$[0].categorie", is("Backend")))
                .andExpect(jsonPath("$[0].statut", is("VALIDEE")))
                .andExpect(jsonPath("$[0].nbProjets", is(2)))
                .andExpect(jsonPath("$[0].nbExperiences", is(1)))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].nom", is("React")))
                .andExpect(jsonPath("$[1].nbProjets", is(0)))
                .andExpect(jsonPath("$[1].nbExperiences", is(0)));
    }

    @Test
    @DisplayName("GET /api/etudiants/{id}/offres-recommandees doit renvoyer 200 et la liste des offres recommandées")
    void testRecommanderOffres() throws Exception {
        com.gi3.matchingcv.dto.OffreRecommandeeDto reco = new com.gi3.matchingcv.dto.OffreRecommandeeDto(
                10L, "Dev Java", "Desc", "IT", "Paris",
                com.gi3.matchingcv.model.enums.TypeContrat.CDI,
                com.gi3.matchingcv.model.enums.StatutOffre.ACTIVE,
                null, "Tech Corp", 76.0, true, null, List.of()
        );

        when(matchingService.recommanderOffres(1L, null, null, null)).thenReturn(List.of(reco));

        mockMvc.perform(get("/api/etudiants/1/offres-recommandees")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(10)))
                .andExpect(jsonPath("$[0].titre", is("Dev Java")))
                .andExpect(jsonPath("$[0].score", is(76.0)))
                .andExpect(jsonPath("$[0].scoreDisponible", is(true)));
    }

    @Test
    @DisplayName("GET /api/etudiants/{id}/offres/{offreId}/score doit renvoyer 200 et le détail du score")
    void testCalculerScore() throws Exception {
        com.gi3.matchingcv.dto.MatchingDetailDto detail = new com.gi3.matchingcv.dto.MatchingDetailDto(
                1L, 10L, "Dev Java", "Tech Corp", 76.0, true, "OK",
                68.0, 8.0, 80.0, 20.0, 2, 1, List.of()
        );

        when(matchingService.calculerScore(1L, 10L)).thenReturn(detail);

        mockMvc.perform(get("/api/etudiants/1/offres/10/score")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etudiantId", is(1)))
                .andExpect(jsonPath("$.offreId", is(10)))
                .andExpect(jsonPath("$.score", is(76.0)))
                .andExpect(jsonPath("$.scoreDisponible", is(true)))
                .andExpect(jsonPath("$.scoreRequises", is(68.0)))
                .andExpect(jsonPath("$.scoreAtouts", is(8.0)));
    }
}

