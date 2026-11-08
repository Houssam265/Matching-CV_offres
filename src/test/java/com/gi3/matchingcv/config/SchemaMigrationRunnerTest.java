package com.gi3.matchingcv.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaMigrationRunnerTest {
    @Test void sqlNullMustNotBeQuoted() {
        assertEquals(" DEFAULT NULL", SchemaMigrationRunner.statusDefaultSql(null, true));
        assertEquals(" DEFAULT NULL", SchemaMigrationRunner.statusDefaultSql("NULL", true));
        assertEquals("", SchemaMigrationRunner.statusDefaultSql("NULL", false));
    }
    @Test void preservesExistingEnumDefaultAcrossDatabaseFormats() {
        assertEquals(" DEFAULT 'ENVOYEE'", SchemaMigrationRunner.statusDefaultSql("ENVOYEE", false));
        assertEquals(" DEFAULT 'ENVOYEE'", SchemaMigrationRunner.statusDefaultSql("'ENVOYEE'", false));
    }
}
