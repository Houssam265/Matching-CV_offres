package com.gi3.matchingcv.config;

import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.Utilisateur;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.repository.CompetenceRepository;
import com.gi3.matchingcv.service.CompetenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompetenceSeedRunnerTest {

    @Mock
    private CompetenceRepository competenceRepository;

    private CompetenceSeedRunner seedRunner;

    private Map<String, Competence> inMemoryDb;

    @BeforeEach
    void setUp() {
        inMemoryDb = new HashMap<>();
        seedRunner = new CompetenceSeedRunner(competenceRepository);
    }

    @Test
    @DisplayName("Le seed lancé deux fois ne crée aucun doublon (idempotent)")
    void testSeedLanceDeuxFoisNeCreeAucunDoublon() throws Exception {
        when(competenceRepository.findByNomNormalise(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(inMemoryDb.get(invocation.getArgument(0))));

        when(competenceRepository.save(any(Competence.class)))
                .thenAnswer(invocation -> {
                    Competence c = invocation.getArgument(0);
                    inMemoryDb.put(c.getNomNormalise(), c);
                    return c;
                });

        // 1er lancement
        seedRunner.run();
        int nbApresPremierRun = inMemoryDb.size();
        assertThat(nbApresPremierRun).isGreaterThan(50);

        // 2ème lancement
        seedRunner.run();
        int nbApresDeuxiemeRun = inMemoryDb.size();
        assertThat(nbApresDeuxiemeRun).isEqualTo(nbApresPremierRun);

        // Le nombre total de save doit être exactement égal au nombre d'éléments insérés au premier tour
        verify(competenceRepository, times(nbApresPremierRun)).save(any(Competence.class));
    }

    @Test
    @DisplayName("Une compétence existante (ex. React) n'est pas modifiée (ni statut, ni catégorie)")
    void testCompetenceExistanteReactNonModifiee() throws Exception {
        Competence reactExistante = new Competence();
        reactExistante.setId(42L);
        reactExistante.setNom("React");
        reactExistante.setNomNormalise("react");
        reactExistante.setCategorie("CategoriePersonnalisee");
        reactExistante.setStatut(StatutCompetence.EN_ATTENTE);
        com.gi3.matchingcv.model.Etudiant auteur = new com.gi3.matchingcv.model.Etudiant();
        auteur.setId(99L);
        reactExistante.setProposeePar(auteur);

        inMemoryDb.put("react", reactExistante);

        when(competenceRepository.findByNomNormalise(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(inMemoryDb.get(invocation.getArgument(0))));

        when(competenceRepository.save(any(Competence.class)))
                .thenAnswer(invocation -> {
                    Competence c = invocation.getArgument(0);
                    inMemoryDb.put(c.getNomNormalise(), c);
                    return c;
                });

        seedRunner.run();

        // React ne doit pas avoir été modifiée
        Competence reactApresSeed = inMemoryDb.get("react");
        assertThat(reactApresSeed).isSameAs(reactExistante);
        assertThat(reactApresSeed.getCategorie()).isEqualTo("CategoriePersonnalisee");
        assertThat(reactApresSeed.getStatut()).isEqualTo(StatutCompetence.EN_ATTENTE);
        assertThat(reactApresSeed.getProposeePar()).isSameAs(auteur);
        verify(competenceRepository, never()).save(reactExistante);
    }

    @Test
    @DisplayName("Le seed est désactivable via app.seed.competences=false")
    void testSeedDesactivableViaPropriete() throws Exception {
        seedRunner.setEnabled(false);

        seedRunner.run();

        verify(competenceRepository, never()).findByNomNormalise(anyString());
        verify(competenceRepository, never()).save(any(Competence.class));
    }

    @Test
    @DisplayName("La recherche d'autocomplétion suggère JavaScript pour 'JS', Go pour 'Golang', et PostgreSQL pour 'Postgres'")
    void testAutocompletionSynonymes() {
        Competence js = new Competence();
        js.setId(1L);
        js.setNom("JavaScript");
        js.setNomNormalise("javascript");
        js.setStatut(StatutCompetence.VALIDEE);
        js.setSynonymes(List.of("JS", "ECMAScript"));

        Competence go = new Competence();
        go.setId(2L);
        go.setNom("Go");
        go.setNomNormalise("go");
        go.setStatut(StatutCompetence.VALIDEE);
        go.setSynonymes(List.of("Golang"));

        Competence postgresql = new Competence();
        postgresql.setId(3L);
        postgresql.setNom("PostgreSQL");
        postgresql.setNomNormalise("postgresql");
        postgresql.setStatut(StatutCompetence.VALIDEE);
        postgresql.setSynonymes(List.of("Postgres"));

        Competence python = new Competence();
        python.setId(4L);
        python.setNom("Python");
        python.setNomNormalise("python");
        python.setStatut(StatutCompetence.VALIDEE);
        python.setSynonymes(Collections.emptyList());

        when(competenceRepository.findAll()).thenReturn(List.of(js, go, postgresql, python));

        CompetenceServiceImpl competenceService = new CompetenceServiceImpl(competenceRepository, null, null, null);

        // 'JS' suggère JavaScript
        List<Competence> suggestionsJS = competenceService.suggererCompetences("JS");
        assertThat(suggestionsJS).extracting(Competence::getNom).contains("JavaScript");

        // 'Golang' suggère Go
        List<Competence> suggestionsGolang = competenceService.suggererCompetences("Golang");
        assertThat(suggestionsGolang).extracting(Competence::getNom).contains("Go");

        // 'Postgres' suggère PostgreSQL
        List<Competence> suggestionsPostgres = competenceService.suggererCompetences("Postgres");
        assertThat(suggestionsPostgres).extracting(Competence::getNom).contains("PostgreSQL");
    }
}
