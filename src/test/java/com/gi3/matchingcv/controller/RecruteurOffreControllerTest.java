package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.model.enums.*;
import com.gi3.matchingcv.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({OffreController.class, RecruteurController.class})
class RecruteurOffreControllerTest {
    @Autowired MockMvc mvc;
    @MockBean OffreService offres;
    @MockBean RecruteurService recruteurs;
    OffreDto dto() {
        return new OffreDto(5L, "Développeur", "Description", "IT", "Rabat", TypeContrat.STAGE, StatutOffre.ACTIVE,
                LocalDateTime.of(2026, 10, 10, 12, 0), "Entreprise",
                List.of(new OffreDto.CompetenceDto(10L, "Java", StatutCompetence.VALIDEE, TypeExigence.REQUISE)));
    }
    @Test void listePublique200EtContratJsonExact() throws Exception {
        when(offres.listerPubliques(null, null, null)).thenReturn(List.of(dto()));
        mvc.perform(get("/api/offres")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0]", aMapWithSize(10)))
                .andExpect(jsonPath("$[0].entreprise").value("Entreprise"))
                .andExpect(jsonPath("$[0].datePublication").value("2026-10-10T12:00:00"))
                .andExpect(jsonPath("$[0].competences[0]", aMapWithSize(4)))
                .andExpect(jsonPath("$[0].competences[0].competenceId").value(10))
                .andExpect(jsonPath("$[0].competences[0].typeExigence").value("REQUISE"))
                .andExpect(jsonPath("$[0].recruteur").doesNotExist())
                .andExpect(jsonPath("$[0].motDePasse").doesNotExist());
    }
    @Test void listeRecruteur200SansBoucleJson() throws Exception {
        when(offres.listerParRecruteur(1L)).thenReturn(List.of(dto()));
        mvc.perform(get("/api/recruteurs/1/offres")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].competences[0].nom").value("Java"))
                .andExpect(jsonPath("$[0].competences[0].offre").doesNotExist());
    }
    @Test void transmetFiltres() throws Exception {
        when(offres.listerPubliques("IT", "Rabat", TypeContrat.STAGE)).thenReturn(List.of(dto()));
        mvc.perform(get("/api/offres").param("domaine", "IT").param("localisation", "Rabat").param("typeContrat", "STAGE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }
    @Test void detailPublicRespecteMemeContrat() throws Exception {
        when(offres.trouver(5L, null)).thenReturn(dto());
        mvc.perform(get("/api/offres/5")).andExpect(status().isOk()).andExpect(jsonPath("$", aMapWithSize(10)));
    }
    @Test void postValide201() throws Exception {
        when(offres.creer(any())).thenReturn(dto());
        mvc.perform(post("/api/offres").contentType(MediaType.APPLICATION_JSON).content("""
                {"recruteurId":1,"titre":"Développeur","description":"Description","domaine":"IT",
                 "localisation":"Rabat","typeContrat":"STAGE","competences":[{"competenceId":10,"typeExigence":"REQUISE"}]}
                """)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(5));
    }
    @Test void champsEtExigencesInvalides400() throws Exception {
        mvc.perform(post("/api/offres").contentType(MediaType.APPLICATION_JSON).content("""
                {"recruteurId":1,"titre":" ","description":"Description","domaine":"IT",
                 "localisation":"Rabat","typeContrat":"STAGE","competences":[{"competenceId":10}]}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verify(offres, never()).creer(any());
    }
    @Test void suppressionAvecCandidatures409() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Clôturez cette offre.")).when(offres).supprimer(5L, 1L);
        mvc.perform(delete("/api/offres/5").param("recruteurId", "1")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Clôturez cette offre."));
    }
    @Test void inscriptionEntrepriseObligatoire() throws Exception {
        mvc.perform(post("/api/recruteurs").contentType(MediaType.APPLICATION_JSON).content("""
                {"nom":"Nom","prenom":"Prénom","email":"test@example.com","motDePasse":"secret"}
                """)).andExpect(status().isBadRequest());
        verifyNoInteractions(recruteurs);
    }
    @Test void inscriptionAccepteAliasEtNeRenvoiePasMotDePasse() throws Exception {
        when(recruteurs.inscrire(any())).thenReturn(new AuthResponse(1L, "Prénom", "Nom", "test@example.com", Role.RECRUTEUR, "Bienvenue"));
        mvc.perform(post("/api/recruteurs").contentType(MediaType.APPLICATION_JSON).content("""
                {"nom":"Nom","prenom":"Prénom","email":"test@example.com","motDePasse":"secret","entreprise":"ACME","secteur":"IT"}
                """)).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("RECRUTEUR"))
                .andExpect(jsonPath("$.motDePasse").doesNotExist());
    }
}
