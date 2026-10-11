package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.Candidature;
import com.gi3.matchingcv.model.enums.StatutCandidature;
import com.gi3.matchingcv.model.enums.StatutOffre;
import com.gi3.matchingcv.model.enums.TypeContrat;

import java.time.LocalDateTime;

/**
 * Vue aplatie d'une candidature — aucune boucle JSON possible.
 */
public record CandidatureDto(
        Long id,
        Long etudiantId,
        Long offreId,
        String offreTitre,
        String entreprise,
        String domaine,
        String localisation,
        TypeContrat typeContrat,
        StatutOffre statutOffre,
        Double score,
        StatutCandidature statut,
        LocalDateTime dateCandidature
) {
    public static CandidatureDto from(Candidature c) {
        var offre = c.getOffre();
        String entreprise = (offre.getRecruteur() != null) ? offre.getRecruteur().getNomEntreprise() : "";
        return new CandidatureDto(
                c.getId(),
                c.getEtudiant().getId(),
                offre.getId(),
                offre.getTitre(),
                entreprise,
                offre.getDomaine(),
                offre.getLocalisation(),
                offre.getTypeContrat(),
                offre.getStatut(),
                c.getScore(),
                c.getStatut(),
                c.getDateCandidature()
        );
    }
}
