package com.gi3.matchingcv;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Test de démarrage du contexte Spring — vérifie que l'application
 * se charge correctement (datasource, beans, configuration…).
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:context;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.show-sql=false", "app.seed.competences=false"
})
class MatchingCvOffresApplicationTests {

    @Test
    void contextLoads() {
        // Si le contexte Spring démarre sans exception, ce test passe.
    }
}
