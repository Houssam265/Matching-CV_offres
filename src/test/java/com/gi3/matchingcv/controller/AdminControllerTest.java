package com.gi3.matchingcv.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gi3.matchingcv.dto.CompetenceAModererDto;
import com.gi3.matchingcv.exception.CompetenceDejaExistanteException;
import com.gi3.matchingcv.exception.CompetenceDejaTraiteeException;
import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.enums.Role;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests MockMvc du contrôleur AdminController (modération des compétences).
 */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminCompetenceService adminCompetenceService;

    @MockBean
    private AdminUtilisateurService adminUtilisateurService;

    @MockBean
    private AdminOffreService adminOffreService;

    @MockBean
    private AdminStatistiquesService adminStatistiquesService;

    @MockBean
    private AuditService auditService;

    // ──────────────────────────────────────────────────────────────────────────
    // FILE DE MODERATION & COMPTEUR
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/admin/moderation : retourne la liste des compétences en attente avec statut 200")
    void testListerModeration() throws Exception {
        CompetenceAModererDto dto = new CompetenceAModererDto(
                1L,                                     // id
                "ReactJS",                              // nom
                "reactjs",                              // nomNormalise
                "Frontend",                             // categorie
                List.of("React.js"),                    // synonymes
                StatutCompetence.EN_ATTENTE,            // statut
                null,                                   // proposeeParId
                "Paul Dupont",                          // proposeeParNom
                Role.ETUDIANT,                          // proposeeParRole
                null,                                   // proposeePar (dto)
                LocalDateTime.of(2026, 1, 1, 10, 0),   // dateProposition
                3L,                                     // usage
                3L,                                     // usages
                List.of("React"),                       // doublonsProbables
                List.of("React")                        // doublonsPotentiels
        );

        when(adminCompetenceService.listerAModerer(eq(Role.ETUDIANT), eq("react")))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/moderation")
                        .param("role", "ETUDIANT")
                        .param("recherche", "react")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].nom", is("ReactJS")))
                .andExpect(jsonPath("$[0].categorie", is("Frontend")))
                .andExpect(jsonPath("$[0].proposeeParNom", is("Paul Dupont")))
                .andExpect(jsonPath("$[0].proposeeParRole", is("ETUDIANT")))
                .andExpect(jsonPath("$[0].usage", is(3)))
                .andExpect(jsonPath("$[0].doublonsProbables[0]", is("React")));
    }

    @Test
    @DisplayName("GET /api/admin/moderation/count : retourne le nombre de compétences en attente")
    void testCountModeration() throws Exception {
        when(adminCompetenceService.countAModerer()).thenReturn(7L);

        mockMvc.perform(get("/api/admin/moderation/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(7)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // VALIDATION
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/admin/competences/{id}/valider : valide tel quel sans corps")
    void testValiderTelQuel() throws Exception {
        Competence c = new Competence();
        c.setId(10L);
        c.setNom("Docker");
        c.setStatut(StatutCompetence.VALIDEE);

        when(adminCompetenceService.validerAvecModifications(eq(10L), isNull(), isNull(), isNull(), any()))
                .thenReturn(c);

        mockMvc.perform(post("/api/admin/competences/10/valider")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.nom", is("Docker")));
    }

    @Test
    @DisplayName("POST /api/admin/competences/{id}/valider : valide avec modifications du nom et catégorie")
    void testValiderAvecModifications() throws Exception {
        Competence c = new Competence();
        c.setId(10L);
        c.setNom("Docker Engine");
        c.setCategorie("DevOps");
        c.setStatut(StatutCompetence.VALIDEE);

        when(adminCompetenceService.validerAvecModifications(eq(10L), eq("Docker Engine"), eq("DevOps"), any(), any()))
                .thenReturn(c);

        Map<String, Object> body = Map.of(
                "nom", "Docker Engine",
                "categorie", "DevOps",
                "synonymes", List.of("Docker CE")
        );

        mockMvc.perform(post("/api/admin/competences/10/valider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom", is("Docker Engine")))
                .andExpect(jsonPath("$.categorie", is("DevOps")));
    }

    @Test
    @DisplayName("POST /api/admin/competences/{id}/valider : collision de nom renvoie 409 Conflict")
    void testValiderCollisionNomRenvoie409() throws Exception {
        when(adminCompetenceService.validerAvecModifications(eq(10L), eq("Docker"), any(), any(), any()))
                .thenThrow(new CompetenceDejaExistanteException("Une compétence existe déjà avec ce nom (« Docker »)."));

        Map<String, Object> body = Map.of("nom", "Docker");

        mockMvc.perform(post("/api/admin/competences/10/valider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/admin/competences/{id}/valider : décision déjà prise renvoie 409 Conflict")
    void testValiderDejaTraiteeRenvoie409() throws Exception {
        when(adminCompetenceService.validerAvecModifications(eq(10L), any(), any(), any(), any()))
                .thenThrow(new CompetenceDejaTraiteeException("Cette compétence a déjà été traitée."));

        mockMvc.perform(post("/api/admin/competences/10/valider")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // REJET
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/admin/competences/{id}/rejeter : rejette avec motif optionnel")
    void testRejeterAvecMotif() throws Exception {
        Competence c = new Competence();
        c.setId(11L);
        c.setNom("Invalide");
        c.setStatut(StatutCompetence.REJETEE);

        when(adminCompetenceService.rejeterAvecMotif(eq(11L), eq("Hors sujet"), any()))
                .thenReturn(c);

        Map<String, String> body = Map.of("motif", "Hors sujet");

        mockMvc.perform(post("/api/admin/competences/11/rejeter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(11)))
                .andExpect(jsonPath("$.statut", is("REJETEE")));
    }

    @Test
    @DisplayName("POST /api/admin/competences/{id}/rejeter : décision déjà prise renvoie 409 Conflict")
    void testRejeterDejaTraiteeRenvoie409() throws Exception {
        when(adminCompetenceService.rejeterAvecMotif(eq(11L), any(), any()))
                .thenThrow(new CompetenceDejaTraiteeException("Cette compétence a déjà été traitée."));

        mockMvc.perform(post("/api/admin/competences/11/rejeter")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FUSION
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/admin/competences/{id}/fusionner : fusionne dans la cible spécifiée")
    void testFusionner() throws Exception {
        doNothing().when(adminCompetenceService).fusionner(eq(15L), eq(20L), any());

        Map<String, Object> body = Map.of("cibleId", 20L);

        mockMvc.perform(post("/api/admin/competences/15/fusionner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Fusion")));

        verify(adminCompetenceService).fusionner(eq(15L), eq(20L), any());
    }

    @Test
    @DisplayName("POST /api/admin/competences/{id}/fusionner : décision déjà prise renvoie 409 Conflict")
    void testFusionnerDejaTraiteeRenvoie409() throws Exception {
        doThrow(new CompetenceDejaTraiteeException("Cette compétence a déjà été traitée."))
                .when(adminCompetenceService).fusionner(eq(15L), eq(20L), any());

        Map<String, Object> body = Map.of("cibleId", 20L);

        mockMvc.perform(post("/api/admin/competences/15/fusionner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ACTIONS GROUPÉES
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/admin/competences/valider-lot : valide une liste d'identifiants")
    void testValiderLot() throws Exception {
        doNothing().when(adminCompetenceService).validerGroupes(eq(List.of(1L, 2L, 3L)), any());

        Map<String, Object> body = Map.of("ids", List.of(1L, 2L, 3L));

        mockMvc.perform(post("/api/admin/competences/valider-lot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("3")));

        verify(adminCompetenceService).validerGroupes(eq(List.of(1L, 2L, 3L)), any());
    }

    @Test
    @DisplayName("POST /api/admin/competences/rejeter-lot : rejette une liste d'identifiants avec motif")
    void testRejeterLot() throws Exception {
        doNothing().when(adminCompetenceService).rejeterGroupes(eq(List.of(4L, 5L)), eq("Doublons"), any());

        Map<String, Object> body = Map.of("ids", List.of(4L, 5L), "motif", "Doublons");

        mockMvc.perform(post("/api/admin/competences/rejeter-lot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("2")));

        verify(adminCompetenceService).rejeterGroupes(eq(List.of(4L, 5L)), eq("Doublons"), any());
    }
}

