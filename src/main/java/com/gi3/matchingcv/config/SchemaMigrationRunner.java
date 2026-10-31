package com.gi3.matchingcv.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Applique les migrations SQL manuelles nécessaires lors du démarrage.
 * Hibernate ddl-auto=update ajoute des colonnes mais ne supprime/modifie jamais
 * les colonnes existantes. Ce runner corrige les reliquats de l'ancienne structure.
 */
@Component
public class SchemaMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationRunner.class);
    private final JdbcTemplate jdbc;

    public SchemaMigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {

        // 1. Supprimer toute FK sur etudiant_id (nom auto-généré par Hibernate, variable)
        dropAllForeignKeysOnColumn("notifications", "etudiant_id");

        // 2. Rendre etudiant_id nullable s'il existe encore en NOT NULL
        //    (vestige de l'ancienne entité Notification avant généralisation du destinataire)
        alterColumnIfExists(
            "notifications", "etudiant_id",
            "ALTER TABLE notifications MODIFY COLUMN etudiant_id BIGINT NULL"
        );

        // 3. Mettre actif = true pour tous les utilisateurs existants où la valeur est NULL ou false (0)
        //    (la colonne actif vient d'être ajoutée par Hibernate ddl-auto=update,
        //     MySQL peut avoir mis 0 ou NULL selon la version)
        try {
            int updated = jdbc.update(
                "UPDATE utilisateurs SET actif = 1 WHERE actif IS NULL OR actif = 0"
            );
            if (updated > 0) {
                log.info("[SchemaMigration] {} utilisateur(s) mis à actif=true (colonne actif corrigée).", updated);
            }
        } catch (Exception e) {
            log.debug("[SchemaMigration] Migration actif : {}", e.getMessage());
        }

        log.info("[SchemaMigration] Migration des colonnes legacy terminée.");
    }

    /**
     * Cherche et supprime toutes les contraintes FK qui référencent la colonne donnée.
     */
    private void dropAllForeignKeysOnColumn(String table, String column) {
        try {
            var names = jdbc.queryForList(
                "SELECT kcu.CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE kcu " +
                "JOIN information_schema.TABLE_CONSTRAINTS tc " +
                "  ON tc.TABLE_SCHEMA = kcu.TABLE_SCHEMA " +
                "  AND tc.TABLE_NAME = kcu.TABLE_NAME " +
                "  AND tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME " +
                "WHERE kcu.TABLE_SCHEMA = DATABASE() " +
                "  AND kcu.TABLE_NAME = ? " +
                "  AND kcu.COLUMN_NAME = ? " +
                "  AND tc.CONSTRAINT_TYPE = 'FOREIGN KEY'",
                String.class, table, column
            );
            for (String name : names) {
                jdbc.execute("ALTER TABLE " + table + " DROP FOREIGN KEY `" + name + "`");
                log.info("[SchemaMigration] FK '{}' supprimée de '{}'.", name, table);
            }
        } catch (Exception e) {
            log.debug("[SchemaMigration] dropAllForeignKeysOnColumn({}, {}) : {}", table, column, e.getMessage());
        }
    }

    /**
     * Exécute une instruction ALTER TABLE uniquement si la colonne cible existe dans la table.
     */
    private void alterColumnIfExists(String table, String column, String sql) {
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column
            );
            if (count != null && count > 0) {
                jdbc.execute(sql);
                log.info("[SchemaMigration] Colonne '{}.{}' modifiée avec succès.", table, column);
            } else {
                log.debug("[SchemaMigration] Colonne '{}.{}' absente, rien à faire.", table, column);
            }
        } catch (Exception e) {
            log.warn("[SchemaMigration] Impossible de modifier '{}.{}' : {}", table, column, e.getMessage());
        }
    }
}
