package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.MatchingDetailDto;
import com.gi3.matchingcv.dto.OffreFiltreDto;
import com.gi3.matchingcv.dto.OffreRecommandeeDto;
import com.gi3.matchingcv.model.enums.TypeContrat;

import java.util.List;

/**
 * Service de matching et de recommandation entre étudiants et offres.
 * Contrat d'interface stable destiné à être réutilisé côté recruteur pour classer les candidats d'une offre.
 */
public interface MatchingService {

    /**
     * Calcule le score de correspondance entre un étudiant et une offre, avec le détail complet du calcul.
     *
     * @param etudiantId identifiant de l'étudiant
     * @param offreId    identifiant de l'offre
     * @return le détail complet du matching (score, décomposition par compétence, motifs)
     */
    MatchingDetailDto calculerScore(Long etudiantId, Long offreId);

    /**
     * Recommande les offres actives pour un étudiant avec filtres optionnels, triées par score décroissant.
     *
     * @param etudiantId identifiant de l'étudiant
     * @param filtres    filtres optionnels (domaine, localisation, type de contrat)
     * @return liste ordonnée des offres recommandées avec leur score calculé
     */
    List<OffreRecommandeeDto> recommanderOffres(Long etudiantId, OffreFiltreDto filtres);

    /**
     * Surcharge pratique acceptant directement les paramètres de filtre.
     */
    List<OffreRecommandeeDto> recommanderOffres(Long etudiantId, String domaine, String localisation, TypeContrat typeContrat);
}
