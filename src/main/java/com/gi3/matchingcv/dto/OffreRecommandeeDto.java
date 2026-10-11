package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.enums.StatutOffre;
import com.gi3.matchingcv.model.enums.TypeContrat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Offre recommandée pour un étudiant, enrichie avec son score de matching et le détail du calcul.
 */
public record OffreRecommandeeDto(
        Long id,
        String titre,
        String description,
        String domaine,
        String localisation,
        TypeContrat typeContrat,
        StatutOffre statut,
        LocalDateTime datePublication,
        String entreprise,
        Double score,
        boolean scoreDisponible,
        MatchingDetailDto detailScore,
        List<OffreDto.CompetenceDto> competences
) {
}
