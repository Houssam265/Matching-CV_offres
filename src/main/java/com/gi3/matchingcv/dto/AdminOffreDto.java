package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.Offre;
import com.gi3.matchingcv.model.enums.StatutOffre;
import com.gi3.matchingcv.model.enums.TypeContrat;

import java.time.LocalDateTime;

/**
 * DTO représentant une offre dans l'espace administrateur.
 */
public record AdminOffreDto(
        Long id,
        String titre,
        String description,
        String domaine,
        String localisation,
        TypeContrat typeContrat,
        StatutOffre statut,
        LocalDateTime datePublication,
        LocalDateTime dateLimite,
        Long recruteurId,
        String recruteurNom,
        String entreprise,
        long nbCandidatures
) {
    public static AdminOffreDto from(Offre o, long nbCandidatures) {
        String recruteurNom = o.getRecruteur() != null
                ? o.getRecruteur().getPrenom() + " " + o.getRecruteur().getNom()
                : null;
        String entreprise = o.getRecruteur() != null
                ? o.getRecruteur().getNomEntreprise()
                : null;
        return new AdminOffreDto(
                o.getId(),
                o.getTitre(),
                o.getDescription(),
                o.getDomaine(),
                o.getLocalisation(),
                o.getTypeContrat(),
                o.getStatut(),
                o.getDatePublication(),
                o.getDateLimite(),
                o.getRecruteur() != null ? o.getRecruteur().getId() : null,
                recruteurNom,
                entreprise,
                nbCandidatures
        );
    }
}
