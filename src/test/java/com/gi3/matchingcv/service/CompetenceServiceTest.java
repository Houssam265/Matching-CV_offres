package com.gi3.matchingcv.service;

import com.gi3.matchingcv.exception.CompetenceDejaExistanteException;
import com.gi3.matchingcv.exception.CompetenceUtiliseeException;
import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.Utilisateur;
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

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @Mock
    private jakarta.persistence.Query nativeQuery;

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
        assertThat(saved.getNomNormalise()).isEqualTo("docker");
        assertThat(saved.getStatut()).isEqualTo(StatutCompetence.VALIDEE);
        verify(competenceRepository, times(1)).findByNomNormalise("docker");
        verify(competenceRepository, times(1)).save(aCreer);
    }

    @Test
    @DisplayName("creer() doit lever CompetenceDejaExistanteException pour 'SpringBoot' quand 'Spring Boot' existe déjà")
    void testCreerDoublonVariantesEspacementDoitLeverException() {
        Competence existante = new Competence();
        existante.setId(1L);
        existante.setNom("Spring Boot");
        existante.setNomNormalise("springboot");

        when(competenceRepository.findByNomNormalise("springboot")).thenReturn(Optional.of(existante));

        Competence nouvelle = new Competence();
        nouvelle.setNom("SpringBoot");
        nouvelle.setCategorie("Backend");

        assertThatThrownBy(() -> competenceService.creer(nouvelle))
                .isInstanceOf(com.gi3.matchingcv.exception.CompetenceDejaExistanteException.class)
                .hasMessageContaining("Spring Boot");

        verify(competenceRepository, times(1)).findByNomNormalise("springboot");
        verify(competenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("creer() et proposer() doivent refuser 'spring.boot' avec un point lorsque 'Spring Boot' existe déjà")
    void testCreerDoublonVariantePointDoitLeverException() {
        assertThat(Competence.normaliserNom("spring.boot")).isEqualTo("springboot");
        assertThat(Competence.normaliserNom("Spring Boot")).isEqualTo("springboot");

        Competence existante = new Competence();
        existante.setId(1L);
        existante.setNom("Spring Boot");
        existante.setNomNormalise("springboot");

        when(competenceRepository.findByNomNormalise("springboot")).thenReturn(Optional.of(existante));

        Competence nouvelle = new Competence();
        nouvelle.setNom("spring.boot");
        nouvelle.setCategorie("DevOps");

        assertThatThrownBy(() -> competenceService.creer(nouvelle))
                .isInstanceOf(com.gi3.matchingcv.exception.CompetenceDejaExistanteException.class)
                .hasMessageContaining("Spring Boot");

        assertThatThrownBy(() -> competenceService.proposer("spring.boot", "DevOps"))
                .isInstanceOf(com.gi3.matchingcv.exception.CompetenceDejaExistanteException.class)
                .hasMessageContaining("Spring Boot");
    }

    @Test
    @DisplayName("creer() doit accepter 'C++' et 'C#' comme deux compétences distinctes (non-régression)")
    void testCreerCPlusPlusEtCDieseAcceptesCommeDistincts() {
        // Vérification de la normalisation : les caractères '+' et '#' doivent rester intacts
        assertThat(Competence.normaliserNom("C++")).isEqualTo("c++");
        assertThat(Competence.normaliserNom("C#")).isEqualTo("c#");
        assertThat(Competence.normaliserNom("C")).isEqualTo("c");

        // Simulation création de "C++"
        Competence cPlusPlus = new Competence();
        cPlusPlus.setNom("C++");
        when(competenceRepository.findByNomNormalise("c++")).thenReturn(Optional.empty());
        when(competenceRepository.save(any(Competence.class))).thenAnswer(inv -> inv.getArgument(0));

        Competence savedCpp = competenceService.creer(cPlusPlus);
        assertThat(savedCpp.getNom()).isEqualTo("C++");
        assertThat(savedCpp.getNomNormalise()).isEqualTo("c++");

        // Simulation création de "C#"
        Competence cDiese = new Competence();
        cDiese.setNom("C#");
        when(competenceRepository.findByNomNormalise("c#")).thenReturn(Optional.empty());

        Competence savedCSharp = competenceService.creer(cDiese);
        assertThat(savedCSharp.getNom()).isEqualTo("C#");
        assertThat(savedCSharp.getNomNormalise()).isEqualTo("c#");

        verify(competenceRepository, times(1)).findByNomNormalise("c++");
        verify(competenceRepository, times(1)).findByNomNormalise("c#");
        verify(competenceRepository, times(2)).save(any(Competence.class));
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
    @DisplayName("supprimer() doit supprimer la compétence existante lorsqu'elle n'est pas utilisée")
    void testSupprimerExistant() {
        when(competenceRepository.findById(1L)).thenReturn(Optional.of(competence1));
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(0L);

        competenceService.supprimer(1L);

        verify(competenceRepository, times(1)).findById(1L);
        verify(competenceRepository, times(1)).delete(competence1);
    }

    @Test
    @DisplayName("supprimer() doit refuser et lever CompetenceUtiliseeException si la compétence est utilisée")
    void testSupprimerRefuseeSiUtilisee() {
        when(competenceRepository.findById(1L)).thenReturn(Optional.of(competence1));
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn(1L);

        assertThatThrownBy(() -> competenceService.supprimer(1L))
                .isInstanceOf(CompetenceUtiliseeException.class)
                .hasMessageContaining("utilisée");

        verify(competenceRepository, times(1)).findById(1L);
        verify(competenceRepository, never()).delete(any());
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

    // -------------------------------------------------------------------------
    // Tests de proposer()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("proposer() doit créer la compétence avec le statut EN_ATTENTE")
    void testProposerStatutEnAttente() {
        when(competenceRepository.findByNomNormalise("rust")).thenReturn(Optional.empty());
        when(competenceRepository.save(any(Competence.class))).thenAnswer(inv -> {
            Competence c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });

        Competence proposee = competenceService.proposer("Rust", "Backend");

        assertThat(proposee.getId()).isEqualTo(10L);
        assertThat(proposee.getNom()).isEqualTo("Rust");
        assertThat(proposee.getNomNormalise()).isEqualTo("rust");
        assertThat(proposee.getCategorie()).isEqualTo("Backend");
        assertThat(proposee.getStatut()).isEqualTo(StatutCompetence.EN_ATTENTE);
        verify(competenceRepository, times(1)).findByNomNormalise("rust");
        verify(competenceRepository, times(1)).save(any(Competence.class));
    }

    @Test
    @DisplayName("proposer() doit lever CompetenceDejaExistanteException si la compétence existe (même après normalisation)")
    void testProposerDoublonLeverException() {
        Competence existante = new Competence();
        existante.setId(1L);
        existante.setNom("Spring Boot");
        existante.setNomNormalise("springboot");

        when(competenceRepository.findByNomNormalise("springboot")).thenReturn(Optional.of(existante));

        assertThatThrownBy(() -> competenceService.proposer("Spring-Boot", "Backend"))
                .isInstanceOf(CompetenceDejaExistanteException.class)
                .hasMessageContaining("Spring Boot");

        verify(competenceRepository, times(1)).findByNomNormalise("springboot");
        verify(competenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("proposer() avec variante point 'spring.boot' doit lever CompetenceDejaExistanteException si 'Spring Boot' existe déjà")
    void testProposerDoublonPointLeverException() {
        Competence existante = new Competence();
        existante.setId(1L);
        existante.setNom("Spring Boot");
        existante.setNomNormalise("springboot");

        when(competenceRepository.findByNomNormalise("springboot")).thenReturn(Optional.of(existante));

        assertThatThrownBy(() -> competenceService.proposer("spring.boot", "DevOps"))
                .isInstanceOf(CompetenceDejaExistanteException.class)
                .hasMessageContaining("Spring Boot");

        verify(competenceRepository, times(1)).findByNomNormalise("springboot");
        verify(competenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("proposer() doit normaliser le nom avant la vérification de doublon")
    void testProposerNormalisationNom() {
        when(competenceRepository.findByNomNormalise("kubernetes")).thenReturn(Optional.empty());
        when(competenceRepository.save(any(Competence.class))).thenAnswer(inv -> inv.getArgument(0));

        Competence proposee = competenceService.proposer("  Kubernetes  ", "DevOps");

        assertThat(proposee.getNom()).isEqualTo("Kubernetes");
        assertThat(proposee.getNomNormalise()).isEqualTo("kubernetes");
        assertThat(proposee.getStatut()).isEqualTo(StatutCompetence.EN_ATTENTE);
    }

    @Test
    @DisplayName("proposer() avec categorie null doit fonctionner (catégorie optionnelle)")
    void testProposerSansCategorie() {
        when(competenceRepository.findByNomNormalise("lua")).thenReturn(Optional.empty());
        when(competenceRepository.save(any(Competence.class))).thenAnswer(inv -> inv.getArgument(0));

        Competence proposee = competenceService.proposer("Lua", null);

        assertThat(proposee.getNom()).isEqualTo("Lua");
        assertThat(proposee.getCategorie()).isNull();
        assertThat(proposee.getStatut()).isEqualTo(StatutCompetence.EN_ATTENTE);
    }

    @Test
    @DisplayName("suggererCompetences() : 'JS' suggère JavaScript, 'Golang' suggère Go, 'Postgres' suggère PostgreSQL")
    void testSuggererCompetencesAvecSynonymes() {
        Competence js = new Competence();
        js.setId(10L);
        js.setNom("JavaScript");
        js.setNomNormalise("javascript");
        js.setStatut(StatutCompetence.VALIDEE);
        js.setSynonymes(List.of("JS", "ECMAScript"));

        Competence go = new Competence();
        go.setId(11L);
        go.setNom("Go");
        go.setNomNormalise("go");
        go.setStatut(StatutCompetence.VALIDEE);
        go.setSynonymes(List.of("Golang"));

        Competence pg = new Competence();
        pg.setId(12L);
        pg.setNom("PostgreSQL");
        pg.setNomNormalise("postgresql");
        pg.setStatut(StatutCompetence.VALIDEE);
        pg.setSynonymes(List.of("Postgres"));

        when(competenceRepository.findAll()).thenReturn(List.of(js, go, pg));

        List<Competence> suggestionsJS = competenceService.suggererCompetences("JS");
        assertThat(suggestionsJS).extracting(Competence::getNom).containsExactly("JavaScript");

        List<Competence> suggestionsGolang = competenceService.suggererCompetences("Golang");
        assertThat(suggestionsGolang).extracting(Competence::getNom).containsExactly("Go");

        List<Competence> suggestionsPostgres = competenceService.suggererCompetences("Postgres");
        assertThat(suggestionsPostgres).extracting(Competence::getNom).containsExactly("PostgreSQL");
    }

    @Test
    @DisplayName("suggererCompetences() : respecte les règles de visibilité (VALIDEE + ses propres EN_ATTENTE, jamais REJETEE)")
    void testSuggererCompetencesVisibilite() {
        com.gi3.matchingcv.model.Etudiant user1 = new com.gi3.matchingcv.model.Etudiant();
        user1.setId(1L);

        com.gi3.matchingcv.model.Etudiant user2 = new com.gi3.matchingcv.model.Etudiant();
        user2.setId(2L);

        Competence validee = new Competence();
        validee.setId(1L);
        validee.setNom("Docker");
        validee.setNomNormalise("docker");
        validee.setStatut(StatutCompetence.VALIDEE);

        Competence propreEnAttente = new Competence();
        propreEnAttente.setId(2L);
        propreEnAttente.setNom("Docker Compose");
        propreEnAttente.setNomNormalise("dockercompose");
        propreEnAttente.setStatut(StatutCompetence.EN_ATTENTE);
        propreEnAttente.setProposeePar(user1);

        Competence autreEnAttente = new Competence();
        autreEnAttente.setId(3L);
        autreEnAttente.setNom("Docker Swarm");
        autreEnAttente.setNomNormalise("dockerswarm");
        autreEnAttente.setStatut(StatutCompetence.EN_ATTENTE);
        autreEnAttente.setProposeePar(user2);

        Competence rejetee = new Competence();
        rejetee.setId(4L);
        rejetee.setNom("Docker Bad");
        rejetee.setNomNormalise("dockerbad");
        rejetee.setStatut(StatutCompetence.REJETEE);

        when(competenceRepository.findAll()).thenReturn(List.of(validee, propreEnAttente, autreEnAttente, rejetee));

        List<Competence> suggestions = competenceService.suggererCompetences("docker", 1L);
        assertThat(suggestions).extracting(Competence::getId)
                .containsExactly(1L, 2L);
    }
}
