package com.gi3.matchingcv.dto;

/**
 * Bloc entreprise exposé aux étudiants dans le détail d'une offre.
 */
public record EntrepriseBlocDto(
        String nom,
        String secteur,
        String ville,
        String siteWeb,
        String description
) {
}
