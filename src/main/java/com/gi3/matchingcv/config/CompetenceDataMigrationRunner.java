package com.gi3.matchingcv.config;

import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.repository.CompetenceRepository;
import jakarta.persistence.EntityManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Migration et harmonisation des données au démarrage de l'application :
 * 1. Recalcule 'nomNormalise' pour toute compétence selon les règles actuelles (espaces, tirets, underscores, points).
 * 2. Si des doublons existent déjà en base (ex: "Spring Boot" et "spring.boot"), fusionne les références
 *    vers la compétence principale (VALIDEE ou id le plus ancien) et supprime le doublon.
 */
@Component
@Order(1)
public class CompetenceDataMigrationRunner implements CommandLineRunner {

    private final CompetenceRepository competenceRepository;
    private final EntityManager entityManager;

    public CompetenceDataMigrationRunner(CompetenceRepository competenceRepository, EntityManager entityManager) {
        this.competenceRepository = competenceRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Competence> competences = competenceRepository.findAll();
        Map<String, List<Competence>> parNomNormalise = new LinkedHashMap<>();

        for (Competence competence : competences) {
            String norm = Competence.normaliserNom(competence.getNom());
            parNomNormalise.computeIfAbsent(norm, k -> new ArrayList<>()).add(competence);
        }

        for (Map.Entry<String, List<Competence>> entry : parNomNormalise.entrySet()) {
            String norm = entry.getKey();
            List<Competence> groupe = entry.getValue();

            // Trier pour choisir la compétence principale :
            // Priorité aux VALIDEE, puis à l'ID le plus petit (la plus ancienne)
            groupe.sort((a, b) -> {
                boolean aVal = a.getStatut() == StatutCompetence.VALIDEE;
                boolean bVal = b.getStatut() == StatutCompetence.VALIDEE;
                if (aVal != bVal) {
                    return aVal ? -1 : 1;
                }
                return Long.compare(a.getId() != null ? a.getId() : 0, b.getId() != null ? b.getId() : 0);
            });

            Competence principale = groupe.get(0);
            if (!norm.equals(principale.getNomNormalise())) {
                principale.setNomNormalise(norm);
                competenceRepository.save(principale);
            }

            // Si doublons présents (ex: spring.boot alors que Spring Boot existe déjà)
            for (int i = 1; i < groupe.size(); i++) {
                Competence doublon = groupe.get(i);
                Long doublonId = doublon.getId();
                Long principaleId = principale.getId();

                if (doublonId != null && principaleId != null) {
                    // Pour etudiant_competence :
                    entityManager.createNativeQuery(
                            "INSERT IGNORE INTO etudiant_competence (etudiant_id, competence_id) " +
                            "SELECT etudiant_id, :principaleId FROM etudiant_competence WHERE competence_id = :doublonId")
                            .setParameter("principaleId", principaleId)
                            .setParameter("doublonId", doublonId)
                            .executeUpdate();
                    entityManager.createNativeQuery("DELETE FROM etudiant_competence WHERE competence_id = :doublonId")
                            .setParameter("doublonId", doublonId).executeUpdate();

                    // Pour projet_competence :
                    entityManager.createNativeQuery(
                            "INSERT IGNORE INTO projet_competence (projet_id, competence_id) " +
                            "SELECT projet_id, :principaleId FROM projet_competence WHERE competence_id = :doublonId")
                            .setParameter("principaleId", principaleId)
                            .setParameter("doublonId", doublonId)
                            .executeUpdate();
                    entityManager.createNativeQuery("DELETE FROM projet_competence WHERE competence_id = :doublonId")
                            .setParameter("doublonId", doublonId).executeUpdate();

                    // Pour experience_competence :
                    entityManager.createNativeQuery(
                            "INSERT IGNORE INTO experience_competence (experience_id, competence_id) " +
                            "SELECT experience_id, :principaleId FROM experience_competence WHERE competence_id = :doublonId")
                            .setParameter("principaleId", principaleId)
                            .setParameter("doublonId", doublonId)
                            .executeUpdate();
                    entityManager.createNativeQuery("DELETE FROM experience_competence WHERE competence_id = :doublonId")
                            .setParameter("doublonId", doublonId).executeUpdate();

                    // Pour offre_competences :
                    entityManager.createNativeQuery(
                            "UPDATE IGNORE offre_competences SET competence_id = :principaleId WHERE competence_id = :doublonId")
                            .setParameter("principaleId", principaleId)
                            .setParameter("doublonId", doublonId)
                            .executeUpdate();
                    entityManager.createNativeQuery("DELETE FROM offre_competences WHERE competence_id = :doublonId")
                            .setParameter("doublonId", doublonId).executeUpdate();

                    // Pour profil_competences :
                    entityManager.createNativeQuery(
                            "UPDATE IGNORE profil_competences SET competence_id = :principaleId WHERE competence_id = :doublonId")
                            .setParameter("principaleId", principaleId)
                            .setParameter("doublonId", doublonId)
                            .executeUpdate();
                    entityManager.createNativeQuery("DELETE FROM profil_competences WHERE competence_id = :doublonId")
                            .setParameter("doublonId", doublonId).executeUpdate();

                    competenceRepository.delete(doublon);
                }
            }
        }

        // Harmonisation des anciennes catégories vers les catégories officielles du référentiel
        entityManager.createNativeQuery(
                "UPDATE competences SET categorie = 'Backend' WHERE categorie = 'Développement Backend'").executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE competences SET categorie = 'Frontend' WHERE categorie = 'Développement Frontend'").executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE competences SET categorie = 'Data & IA' WHERE categorie = 'Data & Intelligence Artificielle'").executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE competences SET categorie = 'DevOps & Cloud' WHERE categorie = 'DevOps'").executeUpdate();
        entityManager.createNativeQuery(
                "UPDATE competences SET nom = 'Python', categorie = 'Langage' WHERE nom_normalise = 'python' AND (nom = 'python' OR categorie = 'Data & Intelligence Artificielle')").executeUpdate();
    }
}
