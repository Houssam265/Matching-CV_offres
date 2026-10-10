package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.ExperienceProfessionnelle;
import com.gi3.matchingcv.repository.ExperienceProfessionnelleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExperienceProfessionnelleServiceTest {

    @Mock
    private ExperienceProfessionnelleRepository experienceRepository;

    @InjectMocks
    private ExperienceProfessionnelleServiceImpl experienceService;

    private Etudiant etudiant;

    @BeforeEach
    void setUp() {
        etudiant = new Etudiant();
        etudiant.setId(1L);
        etudiant.setNom("Hariss");
        etudiant.setPrenom("Houssam");
    }

    @Test
    @DisplayName("Créer une expérience avec des dates valides doit réussir")
    void creer_AvecDatesValides_DoitReussir() {
        ExperienceProfessionnelle exp = new ExperienceProfessionnelle();
        exp.setPoste("Développeur Full-Stack");
        exp.setEntreprise("Capgemini");
        exp.setDateDebut(LocalDate.of(2026, 8, 1));
        exp.setDateFin(LocalDate.of(2026, 10, 1));
        exp.setEtudiant(etudiant);

        when(experienceRepository.save(any(ExperienceProfessionnelle.class))).thenReturn(exp);

        ExperienceProfessionnelle res = experienceService.creer(exp);

        assertThat(res).isNotNull();
        assertThat(res.getDateDebut()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(res.getDateFin()).isEqualTo(LocalDate.of(2026, 10, 1));
        verify(experienceRepository, times(1)).save(exp);
    }

    @Test
    @DisplayName("Créer une expérience en cours (dateFin = null) doit réussir")
    void creer_SansDateFin_DoitReussir() {
        ExperienceProfessionnelle exp = new ExperienceProfessionnelle();
        exp.setPoste("Stagiaire");
        exp.setEntreprise("OCP");
        exp.setDateDebut(LocalDate.of(2026, 9, 1));
        exp.setDateFin(null);
        exp.setEtudiant(etudiant);

        when(experienceRepository.save(any(ExperienceProfessionnelle.class))).thenReturn(exp);

        ExperienceProfessionnelle res = experienceService.creer(exp);

        assertThat(res).isNotNull();
        assertThat(res.getDateFin()).isNull();
        verify(experienceRepository, times(1)).save(exp);
    }

    @Test
    @DisplayName("Créer une expérience sans dateDebut doit lever IllegalArgumentException")
    void creer_SansDateDebut_DoitLeverException() {
        ExperienceProfessionnelle exp = new ExperienceProfessionnelle();
        exp.setPoste("Stagiaire");
        exp.setEntreprise("Capgemini");
        exp.setDateDebut(null);

        assertThatThrownBy(() -> experienceService.creer(exp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La date de début est obligatoire");

        verify(experienceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Créer une expérience avec dateFin avant dateDebut doit lever IllegalArgumentException")
    void creer_DateFinAvantDateDebut_DoitLeverException() {
        ExperienceProfessionnelle exp = new ExperienceProfessionnelle();
        exp.setPoste("TEST");
        exp.setEntreprise("CAPGEMINIE");
        exp.setDateDebut(LocalDate.of(2026, 10, 7));
        exp.setDateFin(LocalDate.of(2026, 8, 11)); // Antérieure au 07/10/2026 !

        assertThatThrownBy(() -> experienceService.creer(exp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La date de fin ne peut pas être antérieure à la date de début");

        verify(experienceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lister par étudiant doit corriger automatiquement les dates inversées historiques")
    void listerParEtudiant_AvecDatesInversesHistoriques_DoitLesCorriger() {
        ExperienceProfessionnelle exp = new ExperienceProfessionnelle();
        exp.setId(2L);
        exp.setPoste("TEST");
        exp.setEntreprise("CAPGEMINIE");
        exp.setDateDebut(LocalDate.of(2026, 10, 7));
        exp.setDateFin(LocalDate.of(2026, 8, 11));
        exp.setEtudiant(etudiant);

        List<ExperienceProfessionnelle> list = new ArrayList<>();
        list.add(exp);

        when(experienceRepository.findByEtudiantId(1L)).thenReturn(list);

        List<ExperienceProfessionnelle> res = experienceService.listerParEtudiant(1L);

        assertThat(res).hasSize(1);
        ExperienceProfessionnelle corrigee = res.get(0);
        assertThat(corrigee.getDateDebut()).isEqualTo(LocalDate.of(2026, 8, 11));
        assertThat(corrigee.getDateFin()).isEqualTo(LocalDate.of(2026, 10, 7));
        verify(experienceRepository, times(1)).save(exp);
    }
}
