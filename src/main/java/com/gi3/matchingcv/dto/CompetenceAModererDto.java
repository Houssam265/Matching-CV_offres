package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.enums.StatutCompetence;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO enrichi pour la file de modération : compétence EN_ATTENTE + méta-données utiles à l'admin.
 */
public record CompetenceAModererDto(
        Long id,
        String nom,
        String nomNormalise,
        String categorie,
        List<String> synonymes,
        StatutCompetence statut,
        Long proposeeParId,
        String proposeeParNom,
        LocalDateTime dateProposition,

        /** Nombre total d'utilisations dans profils, projets, expériences, offres. */
        long usages,

        /** Noms des compétences VALIDEE dont le nomNormalise est très proche (doublons potentiels). */
        List<String> doublonsPotentiels
) {
    public static CompetenceAModererDto from(Competence c, long usages, List<String> doublons) {
        String propNom = (c.getProposeePar() != null)
                ? c.getProposeePar().getPrenom() + " " + c.getProposeePar().getNom()
                : null;
        return new CompetenceAModererDto(
                c.getId(),
                c.getNom(),
                c.getNomNormalise(),
                c.getCategorie(),
                c.getSynonymes() != null ? List.copyOf(c.getSynonymes()) : List.of(),
                c.getStatut(),
                c.getProposeePar() != null ? c.getProposeePar().getId() : null,
                propNom,
                c.getDateCreation(),
                usages,
                doublons
        );
    }
}
