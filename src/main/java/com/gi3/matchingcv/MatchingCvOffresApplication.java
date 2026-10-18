package com.gi3.matchingcv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Point d'entrée principal de l'application Matching CV-Offres.
 * GI3 — ENSA Tétouan
 */
@EnableAsync
@SpringBootApplication
public class MatchingCvOffresApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchingCvOffresApplication.class, args);
    }

}
