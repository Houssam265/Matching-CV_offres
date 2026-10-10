package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.Utilisateur;

import java.util.List;

/**
 * Interface du service gérant les opérations métier sur les compétences.
 * Permet l'inversion de contrôle (IoC) en découplant les consommateurs du service
 * de son implémentation concrète.
 */
public interface CompetenceService {

    /**
     * Récupère la liste de toutes les compétences existantes.
     *
     * @return la liste des compétences
     */
    List<Competence> listerToutes();

    /**
     * Enregistre une nouvelle compétence dans le système.
     *
     * @param competence l'entité compétence à créer
     * @return la compétence persistée avec son identifiant généré
     */
    Competence creer(Competence competence);

    /**
     * Recherche une compétence par son identifiant unique.
     * Lève une exception si la compétence est absente.
     *
     * @param id l'identifiant de la compétence
     * @return la compétence trouvée
     * @throws jakarta.persistence.EntityNotFoundException si aucune compétence n'existe avec cet identifiant
     */
    Competence trouverParId(Long id);

    /**
     * Recherche une compétence par son nom normalisé.
     *
     * @param nomNormalise nom normalisé
     * @return Optional contenant la compétence si trouvée
     */
    java.util.Optional<Competence> trouverParNomNormalise(String nomNormalise);

    /**
     * Supprime une compétence du référentiel par son identifiant.
     * Nettoie les associations préalablement si nécessaire.
     *
     * @param id l'identifiant de la compétence à supprimer
     * @throws jakarta.persistence.EntityNotFoundException si la compétence n'existe pas
     */
    void supprimer(Long id);

    /**
     * Propose une nouvelle compétence avec le statut EN_ATTENTE.
     * Même normalisation et détection de doublon que creer(), mais la compétence
     * attend une validation par un administrateur avant d'être intégrée au référentiel.
     *
     * @param nom         le nom de la compétence proposée
     * @param categorie   la catégorie (peut être null)
     * @param proposeePar l'étudiant/utilisateur qui propose la compétence (nullable)
     * @return la compétence créée avec le statut EN_ATTENTE
     * @throws com.gi3.matchingcv.exception.CompetenceDejaExistanteException si un doublon existe déjà
     */
    Competence proposer(String nom, String categorie, Utilisateur proposeePar);

    /**
     * Surcharge rétrocompatible sans utilisateur spécifié.
     */
    default Competence proposer(String nom, String categorie) {
        return proposer(nom, categorie, null);
    }
}
