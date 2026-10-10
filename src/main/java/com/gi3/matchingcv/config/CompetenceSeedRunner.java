package com.gi3.matchingcv.config;

import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.repository.CompetenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Runner de pré-remplissage du dictionnaire de compétences à partir du fichier CSV.
 * Exécuté après CompetenceDataMigrationRunner (Order 1).
 * Idempotent : ignore toute compétence dont le nomNormalise existe déjà sans modifier statut ni catégorie.
 * Désactivable via app.seed.competences=false.
 */
@Component
@Order(2)
public class CompetenceSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CompetenceSeedRunner.class);

    private final CompetenceRepository competenceRepository;

    @Value("${app.seed.competences:true}")
    private boolean enabled = true;

    public CompetenceSeedRunner(CompetenceRepository competenceRepository) {
        this.competenceRepository = competenceRepository;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!enabled) {
            log.info("Pré-remplissage des compétences désactivé via app.seed.competences=false");
            return;
        }

        ClassPathResource resource = new ClassPathResource("data/competences_seed.csv");
        if (!resource.exists()) {
            log.warn("Fichier data/competences_seed.csv introuvable dans le classpath.");
            return;
        }

        int creees = 0;
        int ignorees = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Ligne d'en-tête : nom;categorie;synonymes
                }

                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(";", -1);
                if (parts.length < 1) {
                    continue;
                }

                String nom = parts[0].trim();
                if (nom.isEmpty()) {
                    continue;
                }

                String categorie = parts.length > 1 && !parts[1].trim().isEmpty() ? parts[1].trim() : null;
                List<String> synonymes = new ArrayList<>();
                if (parts.length > 2 && !parts[2].trim().isEmpty()) {
                    synonymes = Arrays.stream(parts[2].split("\\|"))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                }

                String nomNormalise = Competence.normaliserNom(nom);
                if (nomNormalise == null || nomNormalise.isBlank()) {
                    continue;
                }

                Optional<Competence> existante = competenceRepository.findByNomNormalise(nomNormalise);
                if (existante.isPresent()) {
                    // Ignore sans rien modifier (ni statut, ni catégorie)
                    ignorees++;
                } else {
                    Competence nouvelle = new Competence();
                    nouvelle.setNom(nom);
                    nouvelle.setNomNormalise(nomNormalise);
                    nouvelle.setCategorie(categorie);
                    nouvelle.setStatut(StatutCompetence.VALIDEE);
                    nouvelle.setProposeePar(null);
                    nouvelle.setSynonymes(synonymes);
                    competenceRepository.save(nouvelle);
                    creees++;
                }
            }
        }

        log.info("{} créées, {} ignorées", creees, ignorees);
    }
}
