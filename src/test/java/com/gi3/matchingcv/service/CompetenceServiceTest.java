package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.repository.CompetenceRepository;
import com.gi3.matchingcv.repository.OffreCompetenceRepository;
import com.gi3.matchingcv.repository.ProfilCompetenceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompetenceServiceTest {

    @Mock
    private CompetenceRepository competenceRepository;

    @Mock
    private ProfilCompetenceRepository profilCompetenceRepository;

    @Mock
    private OffreCompetenceRepository offreCompetenceRepository;

    @InjectMocks
    private CompetenceServiceImpl competenceService;

    private Competence competence1;
    private Competence competence2;

    @BeforeEach
    void setUp() {
        competence1 = new Competence();
        competence1.setId(1L);
        competence1.setNom("Spring Boot");
        competence1.setCategorie("Backend");
        competence1.setStatut(StatutCompetence.VALIDEE);

        competence2 = new Competence();
        competence2.setId(2L);
        competence2.setNom("React");
        competence2.setCategorie("Frontend");
        competence2.setStatut(StatutCompetence.VALIDEE);
    }

    @Test
    @DisplayName("listerToutes() doit retourner toutes les compétences")
    void testListerToutes() {
        when(competenceRepository.findAll()).thenReturn(Arrays.asList(competence1, competence2));

        List<Competence> result = competenceService.listerToutes();

        assertThat(result).hasSize(2).contains(competence1, competence2);
        verify(competenceRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("creer() doit enregistrer et retourner la compétence")
    void testCreer() {
        Competence aCreer = new Competence();
        aCreer.setNom("Docker");
        aCreer.setCategorie("DevOps");

        when(competenceRepository.save(any(Competence.class))).thenAnswer(invocation -> {
            Competence c = invocation.getArgument(0);
            c.setId(3L);
            return c;
        });

        Competence saved = competenceService.creer(aCreer);

        assertThat(saved.getId()).isEqualTo(3L);
        assertThat(saved.getNom()).isEqualTo("Docker");
        assertThat(saved.getStatut()).isEqualTo(StatutCompetence.VALIDEE);
        verify(competenceRepository, times(1)).save(aCreer);
    }

    @Test
    @DisplayName("trouverParId() doit retourner la compétence lorsqu'elle existe")
    void testTrouverParIdExistant() {
        when(competenceRepository.findById(1L)).thenReturn(Optional.of(competence1));

        Competence found = competenceService.trouverParId(1L);

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(1L);
        assertThat(found.getNom()).isEqualTo("Spring Boot");
        verify(competenceRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("trouverParId() doit lever une EntityNotFoundException si la compétence est absente")
    void testTrouverParIdInexistant() {
        when(competenceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> competenceService.trouverParId(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");

        verify(competenceRepository, times(1)).findById(99L);
    }

    @Test
    @DisplayName("supprimer() doit supprimer la compétence existante")
    void testSupprimerExistant() {
        when(competenceRepository.findById(1L)).thenReturn(Optional.of(competence1));

        competenceService.supprimer(1L);

        verify(competenceRepository, times(1)).findById(1L);
        verify(profilCompetenceRepository, times(1)).deleteByCompetenceId(1L);
        verify(offreCompetenceRepository, times(1)).deleteByCompetenceId(1L);
        verify(competenceRepository, times(1)).delete(competence1);
    }

    @Test
    @DisplayName("supprimer() doit lever EntityNotFoundException si compétence absente")
    void testSupprimerInexistant() {
        when(competenceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> competenceService.supprimer(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");

        verify(competenceRepository, times(1)).findById(99L);
        verify(competenceRepository, never()).delete(any());
    }
}

