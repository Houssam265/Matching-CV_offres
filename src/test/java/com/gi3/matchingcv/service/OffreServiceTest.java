package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.*;
import com.gi3.matchingcv.repository.*;
import jakarta.persistence.EntityNotFoundException;
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
class OffreServiceTest {
    @Mock OffreRepository offres;
    @Mock RecruteurRepository recruteurs;
    @Mock CandidatureRepository candidatures;
    @Mock CompetenceService competences;
    @InjectMocks OffreServiceImpl service;
    Recruteur recruteur;
    Competence java;
    Offre offre;

    @BeforeEach void setUp() {
        recruteur = new Recruteur(); recruteur.setId(1L); recruteur.setNomEntreprise("Entreprise");
        java = new Competence(); java.setId(10L); java.setNom("Java"); java.setStatut(StatutCompetence.VALIDEE);
        offre = new Offre(); offre.setId(5L); offre.setRecruteur(recruteur); offre.setTitre("Développeur");
        offre.setDomaine("Informatique"); offre.setLocalisation("Rabat"); offre.setTypeContrat(TypeContrat.STAGE);
    }
    OffreRequest request(OffreRequest.Exigence... exigences) {
        return new OffreRequest(1L, " Développeur ", "Description", "Informatique", "Rabat", TypeContrat.STAGE, List.of(exigences));
    }
    OffreRequest.Exigence requise() { return new OffreRequest.Exigence(10L, TypeExigence.REQUISE); }
    void creation() { when(recruteurs.findById(1L)).thenReturn(Optional.of(recruteur)); }
    void proprietaire() { creation(); when(offres.findById(5L)).thenReturn(Optional.of(offre)); }
    void sauvegarde() { when(offres.save(any())).thenAnswer(i -> i.getArgument(0)); }

    @Test void requiseObligatoire() {
        creation();
        assertThatThrownBy(() -> service.creer(request(new OffreRequest.Exigence(10L, TypeExigence.ATOUT))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("REQUISE");
        verify(offres, never()).save(any());
    }
    @Test void refuseListeVide() {
        creation();
        assertThatThrownBy(() -> service.creer(request())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void refuseChampObligatoireVide() {
        creation();
        var r = new OffreRequest(1L, " ", "Description", "IT", "Rabat", TypeContrat.STAGE, List.of(requise()));
        assertThatThrownBy(() -> service.creer(r)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(competences);
    }
    @Test void refuseDoublonMemeAvecExigencesDifferentes() {
        creation(); when(competences.trouverParId(10L)).thenReturn(java);
        assertThatThrownBy(() -> service.creer(request(requise(), new OffreRequest.Exigence(10L, TypeExigence.ATOUT))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("deux fois");
        verify(offres, never()).save(any());
    }
    @Test void refuseNouvelleCompetenceRejetee() {
        creation(); java.setStatut(StatutCompetence.REJETEE);
        when(competences.trouverParId(10L)).thenReturn(java);
        assertThatThrownBy(() -> service.creer(request(requise()))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void refuseCompetenceEnAttenteAutrui() {
        creation(); java.setStatut(StatutCompetence.EN_ATTENTE);
        Recruteur autre = new Recruteur(); autre.setId(2L); java.setProposeePar(autre);
        when(competences.trouverParId(10L)).thenReturn(java);
        assertThatThrownBy(() -> service.creer(request(requise()))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void accepteToutesRequisesPersonnellesEnAttente() {
        creation(); sauvegarde(); java.setStatut(StatutCompetence.EN_ATTENTE); java.setProposeePar(recruteur);
        when(competences.trouverParId(10L)).thenReturn(java);
        var result = service.creer(request(requise()));
        assertThat(result.statut()).isEqualTo(StatutOffre.ACTIVE);
        assertThat(result.titre()).isEqualTo("Développeur");
        assertThat(result.datePublication()).isNotNull();
        assertThat(result.competences().get(0).statut()).isEqualTo(StatutCompetence.EN_ATTENTE);
    }
    @Test void conserveLienEtCompetenceRejeteeApresPublication() {
        proprietaire(); sauvegarde(); java.setStatut(StatutCompetence.REJETEE);
        var lien = new OffreCompetence(7L, TypeExigence.REQUISE, offre, java);
        offre.getOffreCompetences().add(lien);
        when(competences.trouverParId(10L)).thenReturn(java);
        var result = service.modifier(5L, request(requise()));
        assertThat(offre.getOffreCompetences()).containsExactly(lien);
        assertThat(result.competences().get(0).statut()).isEqualTo(StatutCompetence.REJETEE);
    }
    @Test void remplaceUneCompetenceRejetee() {
        proprietaire(); sauvegarde();
        Competence rejetee = new Competence(); rejetee.setId(11L); rejetee.setStatut(StatutCompetence.REJETEE);
        offre.getOffreCompetences().add(new OffreCompetence(7L, TypeExigence.REQUISE, offre, rejetee));
        when(competences.trouverParId(10L)).thenReturn(java);
        var result = service.modifier(5L, request(requise()));
        assertThat(result.competences()).extracting(OffreDto.CompetenceDto::competenceId).containsExactly(10L);
    }
    @Test void clotureIdempotente() {
        proprietaire(); sauvegarde();
        assertThat(service.cloturer(5L, 1L).statut()).isEqualTo(StatutOffre.CLOTUREE);
        assertThat(service.cloturer(5L, 1L).statut()).isEqualTo(StatutOffre.CLOTUREE);
    }
    @Test void refuseSuppressionAvecCandidatures() {
        proprietaire(); when(candidatures.existsByOffreId(5L)).thenReturn(true);
        assertThatThrownBy(() -> service.supprimer(5L, 1L)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        verify(offres, never()).delete(any(Offre.class));
    }
    @Test void supprimeSansCandidatures() {
        proprietaire(); service.supprimer(5L, 1L); verify(offres).delete(offre);
    }
    @Test void refuseModificationAutrui() {
        proprietaire(); Recruteur autre = new Recruteur(); autre.setId(2L); offre.setRecruteur(autre);
        assertThatThrownBy(() -> service.modifier(5L, request(requise()))).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
        verify(offres, never()).save(any());
    }
    @Test void refuseClotureEtSuppressionAutrui() {
        proprietaire(); Recruteur autre = new Recruteur(); autre.setId(2L); offre.setRecruteur(autre);
        assertThatThrownBy(() -> service.cloturer(5L, 1L)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.supprimer(5L, 1L)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(candidatures);
    }
    @Test void offreClotureeNonPubliqueMaisAccessibleAuProprietaire() {
        proprietaire(); offre.setStatut(StatutOffre.CLOTUREE);
        assertThatThrownBy(() -> service.trouver(5L, null)).isInstanceOf(EntityNotFoundException.class);
        assertThat(service.trouver(5L, 1L).statut()).isEqualTo(StatutOffre.CLOTUREE);
    }
    @Test void filtreListePubliqueEtDemandeUniquementActives() {
        when(offres.findByStatut(StatutOffre.ACTIVE)).thenReturn(List.of(offre));
        assertThat(service.listerPubliques("FORMAT", "rAB", TypeContrat.STAGE)).hasSize(1);
        assertThat(service.listerPubliques(null, null, TypeContrat.CDI)).isEmpty();
        assertThat(service.listerPubliques("Finance", null, null)).isEmpty();
    }
    @Test void listeProprietaireComprendCloturees() {
        creation(); offre.setStatut(StatutOffre.CLOTUREE);
        when(offres.findByRecruteurIdOrderByDatePublicationDesc(1L)).thenReturn(List.of(offre));
        assertThat(service.listerParRecruteur(1L)).extracting(OffreDto::statut).containsExactly(StatutOffre.CLOTUREE);
    }
}
