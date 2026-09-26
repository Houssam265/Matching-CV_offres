package com.gi3.matchingcv.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Contrôleur de vérification — permet de confirmer que l'API Spring Boot
 * tourne correctement dès le démarrage.
 *
 * Endpoint : GET /api/health
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * Retourne un JSON simple indiquant que l'API est opérationnelle.
     *
     * @return { "status": "OK", "message": "Matching CV-Offres API is running" }
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = Map.of(
            "status",  "OK",
            "message", "Matching CV-Offres API is running"
        );
        return ResponseEntity.ok(response);
    }
}
