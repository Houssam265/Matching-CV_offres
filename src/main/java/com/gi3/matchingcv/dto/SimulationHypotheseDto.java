package com.gi3.matchingcv.dto;

/**
 * Hypothèse de simulation pour une compétence donnée.
 */
public record SimulationHypotheseDto(
        Long competenceId,
        Double niveauSimule
) {
}
