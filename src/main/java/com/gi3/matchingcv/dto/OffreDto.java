package com.gi3.matchingcv.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gi3.matchingcv.model.Offre;
import com.gi3.matchingcv.model.enums.*;
import java.time.LocalDateTime;
import java.util.List;

/** Contrat partagé avec la partie étudiante. Aucun graphe d'entités exposé. */
public record OffreDto(
        Long id,
        String titre,
        String description,
        String domaine,
        String localisation,
        TypeContrat typeContrat,
        StatutOffre statut,
        LocalDateTime datePublication,
        String entreprise,
        List<CompetenceDto> competences,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long nbCandidatures,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long nbATraiter
) {
    public OffreDto(Long id, String titre, String description, String domaine,
                    String localisation, TypeContrat typeContrat, StatutOffre statut,
                    LocalDateTime datePublication, String entreprise, List<CompetenceDto> competences) {
        this(id, titre, description, domaine, localisation, typeContrat, statut, datePublication, entreprise, competences, null, null);
    }

    public record CompetenceDto(Long competenceId, String nom, StatutCompetence statut,
                                TypeExigence typeExigence) {}

    public static OffreDto from(Offre offre) {
        return from(offre, null, null);
    }

    public static OffreDto from(Offre offre, Long nbCandidatures, Long nbATraiter) {
        String entreprise = (offre.getRecruteur() != null) ? offre.getRecruteur().getNomEntreprise() : null;
        return new OffreDto(
                offre.getId(),
                offre.getTitre(),
                offre.getDescription(),
                offre.getDomaine(),
                offre.getLocalisation(),
                offre.getTypeContrat(),
                offre.getStatut(),
                offre.getDatePublication(),
                entreprise,
                offre.getOffreCompetences().stream()
                        .map(c -> new CompetenceDto(c.getCompetence().getId(), c.getCompetence().getNom(),
                                c.getCompetence().getStatut(), c.getTypeExigence())).toList(),
                nbCandidatures,
                nbATraiter
        );
    }
}

