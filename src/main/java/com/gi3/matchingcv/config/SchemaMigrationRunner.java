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

        upgradeCandidatureStatuses();
        upgradeEnum("notifications", "type", com.gi3.matchingcv.model.enums.TypeNotification.values());

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

    /** Extend the existing MySQL enum while retaining its old values and defaults. */
    private void upgradeCandidatureStatuses() {
        upgradeEnum("candidatures", "statut", com.gi3.matchingcv.model.enums.StatutCandidature.values());
    }

    private void upgradeEnum(String table, String field, Enum<?>[] values) {
        Boolean mysql = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
            String product = connection.getMetaData().getDatabaseProductName();
            return product.equalsIgnoreCase("MySQL") || product.equalsIgnoreCase("MariaDB");
        });
        if (!Boolean.TRUE.equals(mysql)) return;
        var columns = jdbc.queryForList("SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT " +
                "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() " +
                "AND TABLE_NAME = ? AND COLUMN_NAME = ?", table, field);
        if (columns.isEmpty()) return;
        var column = columns.get(0);
        String original = column.get("COLUMN_TYPE").toString();
        String updated = original;
        if (original.toLowerCase(java.util.Locale.ROOT).startsWith("enum(")) {
            for (var status : values) {
                String value = "'" + status.name() + "'";
                if (!updated.contains(value)) updated = updated.substring(0, updated.length()-1) + "," + value + ")";
            }
        } else if (original.matches("(?i)varchar\\([1-8]\\)")) {
            updated = "VARCHAR(32)";
        }
        if (updated.equals(original)) return;
        String nullable = "YES".equals(column.get("IS_NULLABLE")) ? " NULL" : " NOT NULL";
        Object defaultValue = column.get("COLUMN_DEFAULT");
        String defaultSql = statusDefaultSql(defaultValue, "YES".equals(column.get("IS_NULLABLE")));
        jdbc.execute("ALTER TABLE " + table + " MODIFY COLUMN " + field + " " + updated + nullable + defaultSql);
        log.info("[SchemaMigration] Statuts de candidature mis à jour (entretien et retrait).");
    }

    static String statusDefaultSql(Object value, boolean nullable) {
        // MariaDB may return the literal string NULL, or a quoted string default.
        if (value == null || "NULL".equalsIgnoreCase(value.toString().trim())) {
            return nullable ? " DEFAULT NULL" : "";
        }
        String literal = value.toString();
        if (literal.length() >= 2 && literal.startsWith("'") && literal.endsWith("'")) {
            literal = literal.substring(1, literal.length() - 1).replace("''", "'");
        }
        return " DEFAULT '" + literal.replace("'", "''") + "'";
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
