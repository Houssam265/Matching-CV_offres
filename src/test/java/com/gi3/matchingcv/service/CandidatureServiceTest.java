package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.CandidatureDto;
import com.gi3.matchingcv.dto.MatchingDetailDto;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.*;
import com.gi3.matchingcv.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidatureServiceTest {

    @Mock private CandidatureRepository candidatureRepository;
    @Mock private EtudiantRepository    etudiantRepository;
    @Mock private OffreRepository       offreRepository;
    @Mock private MatchingService       matchingService;

    @InjectMocks private CandidatureServiceImpl service;

    private Etudiant etudiant;
    private Offre offre;
    private Recruteur recruteur;

    @BeforeEach
    void setUp() {
        recruteur = new Recruteur();
        recruteur.setId(10L);
        recruteur.setNomEntreprise("TechCorp");

        etudiant = new Etudiant();
        etudiant.setId(1L);
        etudiant.setPrenom("Alice");
        etudiant.setNom("Martin");
        etudiant.setCompetences(new ArrayList<>());

        offre = new Offre();
        offre.setId(100L);
        offre.setTitre("Dev Java");
        offre.setStatut(StatutOffre.ACTIVE);
        offre.setRecruteur(recruteur);
        offre.setOffreCompetences(new ArrayList<>());
        offre.setCandidatures(new ArrayList<>());
        offre.setNotifications(new ArrayList<>());
    }

    // ─── Helper ───────────────────────────────────────────────

    private Candidature candidature(Long id, StatutCandidature statut, Double score) {
        Candidature c = new Candidature();
        c.setId(id);
        c.setEtudiant(etudiant);
        c.setOffre(offre);
        c.setStatut(statut);
        c.setScore(score);
        c.setDateCandidature(LocalDateTime.now());
        return c;
    }

    // ─── postuler ─────────────────────────────────────────────

    @Test
    @DisplayName("postuler : candidature créée avec score snapshot")
    void postuler_ok() {
        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
        when(offreRepository.findById(100L)).thenReturn(Optional.of(offre));
        when(candidatureRepository.existsByEtudiantIdAndOffreId(1L, 100L)).thenReturn(false);

        MatchingDetailDto detail = mock(MatchingDetailDto.class);
        when(detail.scoreDisponible()).thenReturn(true);
        when(detail.score()).thenReturn(76.0);
        when(matchingService.calculerScore(1L, 100L)).thenReturn(detail);

        Candidature saved = candidature(200L, StatutCandidature.ENVOYEE, 76.0);
        when(candidatureRepository.save(any())).thenReturn(saved);

        CandidatureDto dto = service.postuler(1L, 100L);

        assertThat(dto.statut()).isEqualTo(StatutCandidature.ENVOYEE);
        assertThat(dto.score()).isEqualTo(76.0);
        verify(candidatureRepository).save(any());
    }

    @Test
    @DisplayName("postuler : score indisponible → candidature créée avec score null")
    void postuler_scoreIndisponible() {
        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
        when(offreRepository.findById(100L)).thenReturn(Optional.of(offre));
        when(candidatureRepository.existsByEtudiantIdAndOffreId(1L, 100L)).thenReturn(false);

        MatchingDetailDto detail = mock(MatchingDetailDto.class);
        when(detail.scoreDisponible()).thenReturn(false);
        when(matchingService.calculerScore(1L, 100L)).thenReturn(detail);

        Candidature saved = candidature(201L, StatutCandidature.ENVOYEE, null);
        when(candidatureRepository.save(any())).thenReturn(saved);

        CandidatureDto dto = service.postuler(1L, 100L);
        assertThat(dto.score()).isNull();
    }

    @Test
    @DisplayName("postuler : doublon → IllegalStateException")
    void postuler_doublon() {
        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
        when(offreRepository.findById(100L)).thenReturn(Optional.of(offre));
        when(candidatureRepository.existsByEtudiantIdAndOffreId(1L, 100L)).thenReturn(true);

        assertThatThrownBy(() -> service.postuler(1L, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà postulé");
    }

    @Test
    @DisplayName("postuler : offre CLOTUREE → IllegalStateException")
    void postuler_offreCloturee() {
        offre.setStatut(StatutOffre.CLOTUREE);
        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
        when(offreRepository.findById(100L)).thenReturn(Optional.of(offre));

        assertThatThrownBy(() -> service.postuler(1L, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plus active");
    }

    // ─── changerStatut ────────────────────────────────────────

    @Test
    @DisplayName("changerStatut : ENVOYEE → VUE autorisé")
    void changerStatut_envoyeeVersVue() {
        Candidature cand = candidature(1L, StatutCandidature.ENVOYEE, 80.0);
        when(candidatureRepository.findById(1L)).thenReturn(Optional.of(cand));
        when(candidatureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CandidatureDto dto = service.changerStatut(1L, StatutCandidature.VUE);
        assertThat(dto.statut()).isEqualTo(StatutCandidature.VUE);
    }

    @Test
    @DisplayName("changerStatut : ACCEPTEE → REFUSEE interdit (définitif)")
    void changerStatut_accepteeVersRefusee_interdit() {
        Candidature cand = candidature(1L, StatutCandidature.ACCEPTEE, 90.0);
        when(candidatureRepository.findById(1L)).thenReturn(Optional.of(cand));

        assertThatThrownBy(() -> service.changerStatut(1L, StatutCandidature.REFUSEE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transition refusée");
    }

    @Test
    @DisplayName("changerStatut : REFUSEE → VUE interdit (définitif)")
    void changerStatut_refuseeVersVue_interdit() {
        Candidature cand = candidature(1L, StatutCandidature.REFUSEE, 50.0);
        when(candidatureRepository.findById(1L)).thenReturn(Optional.of(cand));

        assertThatThrownBy(() -> service.changerStatut(1L, StatutCandidature.VUE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("changerStatut : VUE → ACCEPTEE autorisé")
    void changerStatut_vueVersAcceptee() {
        Candidature cand = candidature(1L, StatutCandidature.VUE, 88.0);
        when(candidatureRepository.findById(1L)).thenReturn(Optional.of(cand));
        when(candidatureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CandidatureDto dto = service.changerStatut(1L, StatutCandidature.ACCEPTEE);
        assertThat(dto.statut()).isEqualTo(StatutCandidature.ACCEPTEE);
    }

    // ─── listerParOffre — tri par score ───────────────────────

    @Test
    @DisplayName("listerParOffre : triées par score décroissant (null en dernier)")
    void listerParOffre_triParScore() {
        Candidature c1 = candidature(1L, StatutCandidature.ENVOYEE, 90.0);
        Candidature c2 = candidature(2L, StatutCandidature.ENVOYEE, 60.0);
        Candidature c3 = candidature(3L, StatutCandidature.ENVOYEE, null);

        when(offreRepository.existsById(100L)).thenReturn(true);
        // Le repository renvoie déjà dans l'ordre (ORDER BY score DESC)
        when(candidatureRepository.findByOffreIdOrderByScoreDesc(100L))
                .thenReturn(List.of(c1, c2, c3));

        List<CandidatureDto> result = service.listerParOffre(100L);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).score()).isEqualTo(90.0);
        assertThat(result.get(1).score()).isEqualTo(60.0);
        assertThat(result.get(2).score()).isNull();
    }

    // ─── Espace Recruteur Phase 2 — Tests ─────────────────────

    @Test
    @DisplayName("listerCandidatsPourRecruteur : accès refusé (403) si l'offre appartient à un autre recruteur")
    void listerCandidatsPourRecruteur_autreRecruteur_forbidden() {
        when(offreRepository.findById(100L)).thenReturn(Optional.of(offre)); // recruteurId = 10L

        assertThatThrownBy(() -> service.listerCandidatsPourRecruteur(100L, 999L, null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(ex -> {
                    org.springframework.web.server.ResponseStatusException rse = (org.springframework.web.server.ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
                });
    }

    @Test
    @DisplayName("listerCandidatsPourRecruteur : email masqué si non ACCEPTEE, dévoilé si ACCEPTEE")
    void listerCandidatsPourRecruteur_emailMasquage() {
        etudiant.setEmail("alice@test.com");
        when(offreRepository.findById(100L)).thenReturn(Optional.of(offre));

        Candidature c1 = candidature(1L, StatutCandidature.ENVOYEE, 85.0);
        Candidature c2 = candidature(2L, StatutCandidature.ACCEPTEE, 90.0);
        when(candidatureRepository.findByOffreIdOrderByScoreDesc(100L)).thenReturn(List.of(c2, c1));

        List<com.gi3.matchingcv.dto.CandidatOffreDto> list = service.listerCandidatsPourRecruteur(100L, 10L, null);

        assertThat(list).hasSize(2);
        // c2 est ACCEPTEE -> email visible
        assertThat(list.get(0).statut()).isEqualTo(StatutCandidature.ACCEPTEE);
        assertThat(list.get(0).email()).isEqualTo("alice@test.com");
        // c1 est ENVOYEE -> email masqué (null)
        assertThat(list.get(1).statut()).isEqualTo(StatutCandidature.ENVOYEE);
        assertThat(list.get(1).email()).isNull();
    }

    @Test
    @DisplayName("obtenirDetailCandidat : passage automatique de ENVOYEE à VUE")
    void obtenirDetailCandidat_autoTransitionVue() {
        Candidature c = candidature(50L, StatutCandidature.ENVOYEE, 75.0);
        when(candidatureRepository.findById(50L)).thenReturn(Optional.of(c));
        when(candidatureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MatchingDetailDto matchingDetail = new MatchingDetailDto(
                1L, 100L, "Dev Java", "TechCorp", 75.0, true, "Score OK",
                60.0, 15.0, 80.0, 20.0, 2, 1, List.of()
        );
        when(matchingService.calculerScore(1L, 100L)).thenReturn(matchingDetail);

        com.gi3.matchingcv.dto.CandidatDetailDto detail = service.obtenirDetailCandidat(50L, 10L);

        assertThat(detail.statut()).isEqualTo(StatutCandidature.VUE);
        assertThat(c.getStatut()).isEqualTo(StatutCandidature.VUE);
        verify(candidatureRepository).save(c);
    }

    @Test
    @DisplayName("obtenirDetailCandidat : accès refusé (403) si recruteur non propriétaire")
    void obtenirDetailCandidat_autreRecruteur_forbidden() {
        Candidature c = candidature(50L, StatutCandidature.ENVOYEE, 75.0);
        when(candidatureRepository.findById(50L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.obtenirDetailCandidat(50L, 999L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(ex -> {
                    org.springframework.web.server.ResponseStatusException rse = (org.springframework.web.server.ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
                });
    }

    @Test
    @DisplayName("obtenirDetailCandidat : détection d'écart de score si profil étudiant a évolué")
    void obtenirDetailCandidat_scoreDiffDetecte() {
        Candidature c = candidature(50L, StatutCandidature.VUE, 65.0);
        when(candidatureRepository.findById(50L)).thenReturn(Optional.of(c));

        MatchingDetailDto matchingDetail = new MatchingDetailDto(
                1L, 100L, "Dev Java", "TechCorp", 82.5, true, "Score recalculé",
                65.0, 17.5, 80.0, 20.0, 3, 1, List.of()
        );
        when(matchingService.calculerScore(1L, 100L)).thenReturn(matchingDetail);

        com.gi3.matchingcv.dto.CandidatDetailDto detail = service.obtenirDetailCandidat(50L, 10L);

        assertThat(detail.scoreCandidature()).isEqualTo(65.0);
        assertThat(detail.scoreActuel()).isEqualTo(82.5);
        assertThat(detail.scoreModifie()).isTrue();
    }

    @Test
    @DisplayName("changerStatutPourRecruteur : rejet si recruteur non propriétaire")
    void changerStatutPourRecruteur_autreRecruteur_forbidden() {
        Candidature c = candidature(50L, StatutCandidature.VUE, 80.0);
        when(candidatureRepository.findById(50L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.changerStatutPourRecruteur(50L, StatutCandidature.ACCEPTEE, 999L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(ex -> {
                    org.springframework.web.server.ResponseStatusException rse = (org.springframework.web.server.ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
                });
    }

    @Test
    @DisplayName("changerStatutPourRecruteur : transitions interdites depuis ACCEPTEE/REFUSEE")
    void changerStatutPourRecruteur_terminalForbidden() {
        Candidature c = candidature(50L, StatutCandidature.ACCEPTEE, 80.0);
        when(candidatureRepository.findById(50L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.changerStatutPourRecruteur(50L, StatutCandidature.REFUSEE, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transition refusée");
    }
}
