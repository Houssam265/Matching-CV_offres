package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.*;
import com.gi3.matchingcv.repository.RecruteurRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecruteurServiceTest {
    @Mock RecruteurRepository recruteurs;
    @Mock AuthService auth;
    @Mock CompetenceService competences;
    @InjectMocks RecruteurServiceImpl service;
    Recruteur recruteur;
    @BeforeEach void setUp() { recruteur = new Recruteur(); recruteur.setId(1L); }
    void connu() { when(recruteurs.findById(1L)).thenReturn(Optional.of(recruteur)); }
    Competence competence(StatutCompetence statut) {
        Competence c = new Competence(); c.setId(10L); c.setNom("Spring Boot"); c.setStatut(statut); return c;
    }
    @Test void inscriptionReutiliseAuthEtForceRoleRecruteur() {
        service.inscrire(new RecruteurInscriptionRequest("Nom", "Prénom", "test@example.com", "secret", "Entreprise", "IT"));
        ArgumentCaptor<InscriptionRequest> captor = ArgumentCaptor.forClass(InscriptionRequest.class);
        verify(auth).inscrire(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.RECRUTEUR);
        assertThat(captor.getValue().getNomEntreprise()).isEqualTo("Entreprise");
    }
    @Test void emailUniqueRetourneConflit() {
        when(auth.inscrire(any())).thenThrow(new IllegalArgumentException("Email déjà utilisé"));
        assertThatThrownBy(() -> service.inscrire(new RecruteurInscriptionRequest("Nom", "Prénom", "test@example.com", "secret", "Entreprise", "IT")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }
    @Test void rattacheExistanteNormaliseeSansCreation() {
        connu(); Competence c = competence(StatutCompetence.VALIDEE);
        when(competences.trouverParNomNormalise("springboot")).thenReturn(Optional.of(c));
        assertThat(service.proposer(1L, " Spring-Boot ", null)).isSameAs(c);
        verify(competences, never()).proposer(any(), any(), any());
    }
    @Test void propositionReutiliseServiceEtAuteur() {
        connu(); Competence c = competence(StatutCompetence.EN_ATTENTE); c.setProposeePar(recruteur);
        when(competences.trouverParNomNormalise("springboot")).thenReturn(Optional.empty());
        when(competences.proposer("Spring Boot", null, recruteur)).thenReturn(c);
        assertThat(service.proposer(1L, "Spring Boot", null)).isSameAs(c);
    }
    @Test void doublonRejeteNeContournePasVisibilite() {
        connu(); when(competences.trouverParNomNormalise("springboot")).thenReturn(Optional.of(competence(StatutCompetence.REJETEE)));
        assertThatThrownBy(() -> service.proposer(1L, "Spring Boot", null)).isInstanceOf(IllegalArgumentException.class);
        verify(competences, never()).proposer(any(), any(), any());
    }
    @Test void doublonEnAttenteAutruiNeContournePasVisibilite() {
        connu(); Competence c = competence(StatutCompetence.EN_ATTENTE);
        Recruteur autre = new Recruteur(); autre.setId(2L); c.setProposeePar(autre);
        when(competences.trouverParNomNormalise("springboot")).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.proposer(1L, "Spring Boot", null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void suggestionsReutilisentServiceAvecIdentite() {
        connu(); when(competences.suggererCompetences("spring", 1L)).thenReturn(List.of(competence(StatutCompetence.VALIDEE)));
        assertThat(service.suggerer(1L, "spring")).hasSize(1);
        verify(competences).suggererCompetences("spring", 1L);
    }
}
