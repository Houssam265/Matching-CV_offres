package com.gi3.matchingcv.service;

import com.gi3.matchingcv.exception.EmailDejaUtiliseException;
import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.enums.Role;
import com.gi3.matchingcv.repository.EtudiantRepository;
import com.gi3.matchingcv.repository.UtilisateurRepository;
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
class EtudiantServiceTest {

    @Mock
    private EtudiantRepository etudiantRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private EtudiantServiceImpl etudiantService;

    private Etudiant etudiant1;
    private Etudiant etudiant2;

    @BeforeEach
    void setUp() {
        etudiant1 = new Etudiant();
        etudiant1.setId(1L);
        etudiant1.setNom("Martin");
        etudiant1.setPrenom("Alice");
        etudiant1.setEmail("alice@example.com");
        etudiant1.setMotDePasse("secret");
        etudiant1.setRole(Role.ETUDIANT);

        etudiant2 = new Etudiant();
        etudiant2.setId(2L);
        etudiant2.setNom("Dupont");
        etudiant2.setPrenom("Bob");
        etudiant2.setEmail("bob@example.com");
        etudiant2.setMotDePasse("pass");
        etudiant2.setRole(Role.ETUDIANT);
    }

    @Test
    @DisplayName("listerTous() doit retourner tous les étudiants")
    void testListerTous() {
        when(etudiantRepository.findAll()).thenReturn(Arrays.asList(etudiant1, etudiant2));

        List<Etudiant> result = etudiantService.listerTous();

        assertThat(result).hasSize(2).contains(etudiant1, etudiant2);
        verify(etudiantRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("creer() doit enregistrer et retourner l'étudiant quand l'e-mail est libre")
    void testCreerEmailLibre() {
        Etudiant aCreer = new Etudiant();
        aCreer.setNom("Claire");
        aCreer.setPrenom("Leclerc");
        aCreer.setEmail("claire@example.com");
        aCreer.setMotDePasse("mdp123");

        when(utilisateurRepository.existsByEmail("claire@example.com")).thenReturn(false);
        when(etudiantRepository.save(any(Etudiant.class))).thenAnswer(inv -> {
            Etudiant e = inv.getArgument(0);
            e.setId(3L);
            return e;
        });

        Etudiant saved = etudiantService.creer(aCreer);

        assertThat(saved.getId()).isEqualTo(3L);
        assertThat(saved.getRole()).isEqualTo(Role.ETUDIANT);
        assertThat(saved.getEmail()).isEqualTo("claire@example.com");
        verify(utilisateurRepository, times(1)).existsByEmail("claire@example.com");
        verify(etudiantRepository, times(1)).save(aCreer);
    }

    @Test
    @DisplayName("creer() doit lever EmailDejaUtiliseException quand l'e-mail est déjà utilisé")
    void testCreerEmailDejaUtilise() {
        Etudiant aCreer = new Etudiant();
        aCreer.setNom("Alice");
        aCreer.setPrenom("Bis");
        aCreer.setEmail("alice@example.com");
        aCreer.setMotDePasse("autre");

        when(utilisateurRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> etudiantService.creer(aCreer))
                .isInstanceOf(EmailDejaUtiliseException.class)
                .hasMessageContaining("alice@example.com");

        verify(utilisateurRepository, times(1)).existsByEmail("alice@example.com");
        verify(etudiantRepository, never()).save(any());
    }

    @Test
    @DisplayName("trouverParId() doit retourner l'étudiant lorsqu'il existe")
    void testTrouverParIdExistant() {
        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant1));

        Etudiant found = etudiantService.trouverParId(1L);

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(1L);
        assertThat(found.getEmail()).isEqualTo("alice@example.com");
        verify(etudiantRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("trouverParId() doit lever EntityNotFoundException si l'étudiant est absent")
    void testTrouverParIdInexistant() {
        when(etudiantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> etudiantService.trouverParId(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");

        verify(etudiantRepository, times(1)).findById(99L);
    }

    @Test
    @DisplayName("sauvegarder() doit déléguer directement au repository sans vérification d'e-mail")
    void testSauvegarder() {
        when(etudiantRepository.save(etudiant1)).thenReturn(etudiant1);

        Etudiant result = etudiantService.sauvegarder(etudiant1);

        assertThat(result).isEqualTo(etudiant1);
        verify(utilisateurRepository, never()).existsByEmail(any());
        verify(etudiantRepository, times(1)).save(etudiant1);
    }
}
