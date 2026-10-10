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
}
