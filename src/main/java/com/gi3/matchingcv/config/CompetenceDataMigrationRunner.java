package com.gi3.matchingcv.config;

import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.repository.CompetenceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Migration des donn\u00e9es existantes au d\u00e9marrage de l'application :
 * Recalcule et renseigne 'nomNormalise' pour toute comp\u00e9tence existante o\u00f9 il est NULL
 * afin d'\u00e9viter tout conflit avec la contrainte d'unicit\u00e9 en base de donn\u00e9es.
 */
@Component
public class CompetenceDataMigrationRunner implements CommandLineRunner {

    private final CompetenceRepository competenceRepository;

    public CompetenceDataMigrationRunner(CompetenceRepository competenceRepository) {
        this.competenceRepository = competenceRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Competence> competences = competenceRepository.findAll();
        for (Competence competence : competences) {
            if (competence.getNomNormalise() == null || competence.getNomNormalise().isBlank()) {
                competence.setNomNormalise(Competence.normaliserNom(competence.getNom()));
                competenceRepository.save(competence);
            }
        }
    }
}
