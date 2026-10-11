package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.enums.TypeContrat;

/**
 * Filtres optionnels pour la recommandation d'offres.
 */
public record OffreFiltreDto(
        String domaine,
        String localisation,
        TypeContrat typeContrat
) {
}
