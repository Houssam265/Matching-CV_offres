package com.gi3.matchingcv.model.enums;

/**
 * Types de conseils générés par le Coach CV.
 */
public enum TypeConseilCoach {
    /**
     * Conseil spécifique à une offre : compétence REQUISE ou ATOUT manquante ou sous-valorisée.
     */
    OFFRE_COMPETENCE,

    /**
     * Valorisation d'une compétence déclarative (0.4) vers un projet (0.7) ou une expérience (0.8+).
     */
    VALORISATION,

    /**
     * Compétence fortement demandée par le marché des offres actives mais absente du profil.
     */
    COMPETENCE_DEMANDEE,

    /**
     * Hygiène du profil : compétences en attente/rejetées, projets/expériences vides, complétion.
     */
    HYGIENE_PROFIL
}
