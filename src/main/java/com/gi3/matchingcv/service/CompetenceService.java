package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.Competence;

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
     * Supprime une compétence du référentiel par son identifiant.
     * Nettoie les associations préalablement si nécessaire.
     *
     * @param id l'identifiant de la compétence à supprimer
     * @throws jakarta.persistence.EntityNotFoundException si la compétence n'existe pas
     */
    void supprimer(Long id);
}
