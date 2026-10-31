package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.CandidatDetailDto;
import com.gi3.matchingcv.dto.CandidatOffreDto;
import com.gi3.matchingcv.dto.CandidatureDto;
import com.gi3.matchingcv.dto.RecruteurDashboardDto;
import com.gi3.matchingcv.model.enums.StatutCandidature;
import com.gi3.matchingcv.service.CandidatureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CandidatureController.class)
class RecruteurCandidatControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CandidatureService candidatureService;

    @Test
    @DisplayName("GET /api/offres/{id}/candidats : 200, liste classée, aucun mot de passe, email masqué si non acceptée")
    void listerCandidats_okEtSecurise() throws Exception {
        CandidatOffreDto c1 = new CandidatOffreDto(
                1L, 10L, 5L, "Dev Fullstack", "Martin", "Alice",
                "Génie Info", "ENSIAS", 88.0, StatutCandidature.ENVOYEE,
                LocalDateTime.of(2026, 10, 11, 10, 0), null
        );
        CandidatOffreDto c2 = new CandidatOffreDto(
                2L, 11L, 5L, "Dev Fullstack", "Dupont", "Bob",
                "Génie Info", "EMI", 92.5, StatutCandidature.ACCEPTEE,
                LocalDateTime.of(2026, 10, 11, 11, 0), "bob@example.com"
        );

        when(candidatureService.listerCandidatsPourRecruteur(5L, 1L, null))
                .thenReturn(List.of(c2, c1));

        mvc.perform(get("/api/offres/5/candidats").param("recruteurId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nom").value("Dupont"))
                .andExpect(jsonPath("$[0].score").value(92.5))
                .andExpect(jsonPath("$[0].statut").value("ACCEPTEE"))
                .andExpect(jsonPath("$[0].email").value("bob@example.com"))
                .andExpect(jsonPath("$[0].motDePasse").doesNotExist())
                .andExpect(jsonPath("$[1].nom").value("Martin"))
                .andExpect(jsonPath("$[1].statut").value("ENVOYEE"))
                .andExpect(jsonPath("$[1].email").doesNotExist())
                .andExpect(jsonPath("$[1].motDePasse").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/offres/{id}/candidats : 403 si recruteur non propriétaire")
    void listerCandidats_autreRecruteur_403() throws Exception {
        when(candidatureService.listerCandidatsPourRecruteur(5L, 999L, null))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Cette offre appartient à un autre recruteur."));

        mvc.perform(get("/api/offres/5/candidats").param("recruteurId", "999"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cette offre appartient à un autre recruteur."));
    }

    @Test
    @DisplayName("GET /api/candidatures/{id} : 200, détail profil, aucun mot de passe, email masqué si non ACCEPTEE")
    void obtenirDetail_securiseSansMotDePasse() throws Exception {
        CandidatDetailDto detail = new CandidatDetailDto(
                10L, 20L, 5L, "Dev Fullstack", "Martin", "Alice",
                "Génie Info", "ENSIAS", null,
                StatutCandidature.VUE, LocalDateTime.of(2026, 10, 11, 10, 0),
                85.0, 85.0, false, null, List.of(), List.of(), List.of()
        );

        when(candidatureService.obtenirDetailCandidat(10L, 1L)).thenReturn(detail);

        mvc.perform(get("/api/candidatures/10").param("recruteurId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Martin"))
                .andExpect(jsonPath("$.statut").value("VUE"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.motDePasse").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/candidatures/{id} : 200, email exposé quand candidature ACCEPTEE")
    void obtenirDetail_emailPresentSiAcceptee() throws Exception {
        CandidatDetailDto detail = new CandidatDetailDto(
                10L, 20L, 5L, "Dev Fullstack", "Dupont", "Bob",
                "Génie Info", "EMI", "bob@example.com",
                StatutCandidature.ACCEPTEE, LocalDateTime.of(2026, 10, 11, 10, 0),
                92.0, 92.0, false, null, List.of(), List.of(), List.of()
        );

        when(candidatureService.obtenirDetailCandidat(10L, 1L)).thenReturn(detail);

        mvc.perform(get("/api/candidatures/10").param("recruteurId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"))
                .andExpect(jsonPath("$.email").value("bob@example.com"))
                .andExpect(jsonPath("$.motDePasse").doesNotExist());
    }

    @Test
    @DisplayName("PATCH /api/candidatures/{id}/statut : 200 transition avec recruteurId")
    void changerStatut_recruteur_ok() throws Exception {
        CandidatureDto updated = new CandidatureDto(
                10L, 20L, 5L, "Dev Fullstack", "TechCorp",
                "IT", "Paris", com.gi3.matchingcv.model.enums.TypeContrat.STAGE,
                com.gi3.matchingcv.model.enums.StatutOffre.ACTIVE,
                88.0, StatutCandidature.ACCEPTEE, LocalDateTime.now()
        );

        when(candidatureService.changerStatutPourRecruteur(eq(10L), eq(StatutCandidature.ACCEPTEE), eq(1L)))
                .thenReturn(updated);

        mvc.perform(patch("/api/candidatures/10/statut")
                        .param("recruteurId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\": \"ACCEPTEE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"));
    }

    @Test
    @DisplayName("GET /api/recruteurs/{id}/dashboard : 200 OK avec KPIs et répartition")
    void dashboardRecruteur_200() throws Exception {
        RecruteurDashboardDto dash = new RecruteurDashboardDto(
                3L, 1L, 4L, 3L, 1L,
                Map.of(StatutCandidature.ENVOYEE, 2L, StatutCandidature.VUE, 1L, StatutCandidature.ACCEPTEE, 1L),
                List.of()
        );

        when(candidatureService.obtenirDashboardRecruteur(1L)).thenReturn(dash);

        mvc.perform(get("/api/recruteurs/1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOffres").value(4))
                .andExpect(jsonPath("$.candidaturesRecues").value(3))
                .andExpect(jsonPath("$.candidaturesATraiter").value(1))
                .andExpect(jsonPath("$.repartitionStatuts.ENVOYEE").value(2))
                .andExpect(jsonPath("$.motDePasse").doesNotExist());
    }

    @Test
    @DisplayName("PATCH /api/candidatures/{id}/favori : 200 bascule l'étoile favori")
    void basculerFavori_200() throws Exception {
        CandidatureDto dto = new CandidatureDto(
                10L, 20L, 5L, "Dev Fullstack", "TechCorp",
                "IT", "Paris", com.gi3.matchingcv.model.enums.TypeContrat.STAGE,
                com.gi3.matchingcv.model.enums.StatutOffre.ACTIVE,
                88.0, StatutCandidature.ENVOYEE, LocalDateTime.now()
        );

        when(candidatureService.basculerFavori(10L, 1L)).thenReturn(dto);

        mvc.perform(patch("/api/candidatures/10/favori").param("recruteurId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.etudiantId").value(20));
    }

    @Test
    @DisplayName("PATCH /api/candidatures/{id}/note : 200 enregistre la note interne")
    void definirNoteInterne_200() throws Exception {
        CandidatureDto dto = new CandidatureDto(
                10L, 20L, 5L, "Dev Fullstack", "TechCorp",
                "IT", "Paris", com.gi3.matchingcv.model.enums.TypeContrat.STAGE,
                com.gi3.matchingcv.model.enums.StatutOffre.ACTIVE,
                88.0, StatutCandidature.ENVOYEE, LocalDateTime.now()
        );

        when(candidatureService.definirNoteInterne(10L, 1L, "Top profil"))
                .thenReturn(dto);

        mvc.perform(patch("/api/candidatures/10/note")
                        .param("recruteurId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Top profil\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @DisplayName("GET /api/offres/{id}/statistiques : 200 avec entonnoir et tranches")
    void statistiquesOffre_200() throws Exception {
        com.gi3.matchingcv.dto.OffreStatistiquesDto stats = new com.gi3.matchingcv.dto.OffreStatistiquesDto(
                5L, "Dev Fullstack", 3L,
                new com.gi3.matchingcv.dto.OffreStatistiquesDto.EntonnoirDto(3L, 2L, 1L, 0L),
                85.5,
                Map.of("moinsDe40", 0L, "entre40Et69", 1L, "soixanteDixEtPlus", 2L),
                12.0
        );

        when(candidatureService.obtenirStatistiquesOffre(5L, 1L)).thenReturn(stats);

        mvc.perform(get("/api/offres/5/statistiques").param("recruteurId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offreId").value(5))
                .andExpect(jsonPath("$.offreTitre").value("Dev Fullstack"))
                .andExpect(jsonPath("$.totalCandidatures").value(3))
                .andExpect(jsonPath("$.scoreMoyen").value(85.5))
                .andExpect(jsonPath("$.entonnoir.envoyees").value(3))
                .andExpect(jsonPath("$.delaiMoyenTraitementHeures").value(12.0));
    }

    @Test
    @DisplayName("GET /api/offres/{id}/candidats/export-csv : 200 avec en-têtes et email masqué sauf ACCEPTEE")
    void exporterCsv_200() throws Exception {
        CandidatOffreDto c1 = new CandidatOffreDto(
                1L, 10L, 5L, "Dev Fullstack", "Martin", "Alice",
                "Génie Info", "ENSIAS", 88.0, StatutCandidature.ENVOYEE,
                LocalDateTime.of(2026, 10, 11, 10, 0), null
        );
        CandidatOffreDto c2 = new CandidatOffreDto(
                2L, 11L, 5L, "Dev Fullstack", "Dupont", "Bob",
                "Génie Info", "EMI", 92.5, StatutCandidature.ACCEPTEE,
                LocalDateTime.of(2026, 10, 11, 11, 0), "bob@example.com"
        );

        when(candidatureService.listerCandidatsPourRecruteur(5L, 1L, null, null, null, "score"))
                .thenReturn(List.of(c1, c2));

        mvc.perform(get("/api/offres/5/candidats/export-csv").param("recruteurId", "1"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"candidats-offre-5.csv\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString("Nom,Prenom,Filiere,Etablissement,Score,Statut,Date,Email")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString("Martin,Alice,Génie Info,ENSIAS,88.0,ENVOYEE,2026-10-11T10:00,")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString("Dupont,Bob,Génie Info,EMI,92.5,ACCEPTEE,2026-10-11T11:00,bob@example.com")));
    }
}

