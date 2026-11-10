package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.enums.Role;
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
        Role proposeeParRole,
        ProposeeParDto proposeePar,
        LocalDateTime dateProposition,

        /** Nombre total d'utilisations dans listes personnelles, projets, expériences, offres. */
        long usage,
        long usages,

        /** Noms des compétences VALIDEE dont le nomNormalise est très proche (doublons probables). */
        List<String> doublonsProbables,
        List<String> doublonsPotentiels
) {
    public record ProposeeParDto(Long id, String nom, String prenom, Role role) {}

    public static CompetenceAModererDto from(Competence c, long usages, List<String> doublons) {
        String propNom = (c.getProposeePar() != null)
                ? ((c.getProposeePar().getPrenom() != null ? c.getProposeePar().getPrenom() + " " : "")
                + (c.getProposeePar().getNom() != null ? c.getProposeePar().getNom() : "")).trim()
                : null;
        Role propRole = (c.getProposeePar() != null)
                ? c.getProposeePar().getRole()
                : null;
        ProposeeParDto propDto = (c.getProposeePar() != null)
                ? new ProposeeParDto(c.getProposeePar().getId(), c.getProposeePar().getNom(), c.getProposeePar().getPrenom(), c.getProposeePar().getRole())
                : null;
        LocalDateTime dateProp = c.getDateProposition() != null ? c.getDateProposition() : c.getDateCreation();
        List<String> dList = doublons != null ? List.copyOf(doublons) : List.of();
        return new CompetenceAModererDto(
                c.getId(),
                c.getNom(),
                c.getNomNormalise(),
                c.getCategorie(),
                c.getSynonymes() != null ? List.copyOf(c.getSynonymes()) : List.of(),
                c.getStatut(),
                c.getProposeePar() != null ? c.getProposeePar().getId() : null,
                propNom,
                propRole,
                propDto,
                dateProp,
                usages,
                usages,
                dList,
                dList
        );
    }
}
