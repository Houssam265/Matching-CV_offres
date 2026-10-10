package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.Offre;
import com.gi3.matchingcv.model.enums.*;
import java.time.LocalDateTime;
import java.util.List;

/** Contrat partagé avec la partie étudiante. Aucun graphe d'entités exposé. */
public record OffreDto(Long id, String titre, String description, String domaine,
                       String localisation, TypeContrat typeContrat, StatutOffre statut,
                       LocalDateTime datePublication, String entreprise, List<CompetenceDto> competences) {
    public record CompetenceDto(Long competenceId, String nom, StatutCompetence statut,
                                TypeExigence typeExigence) {}

    public static OffreDto from(Offre offre) {
        return new OffreDto(offre.getId(), offre.getTitre(), offre.getDescription(), offre.getDomaine(),
                offre.getLocalisation(), offre.getTypeContrat(), offre.getStatut(), offre.getDatePublication(),
                offre.getRecruteur().getNomEntreprise(), offre.getOffreCompetences().stream()
                .map(c -> new CompetenceDto(c.getCompetence().getId(), c.getCompetence().getNom(),
                        c.getCompetence().getStatut(), c.getTypeExigence())).toList());
    }
}
