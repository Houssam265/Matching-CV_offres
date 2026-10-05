package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.Projet;

import java.util.List;

/**
 * Interface du service gérant les opérations métier sur les projets des étudiants.
 */
public interface ProjetService {

    /**
     * Récupère les projets d'un étudiant donné.
     *
     * @param etudiantId identifiant de l'étudiant
     * @return liste de ses projets
     */
    List<Projet> listerParEtudiant(Long etudiantId);

    /**
     * Crée et persiste un projet rattaché à un étudiant.
     *
     * @param projet le projet à créer (l'etudiant doit déjà être défini)
     * @return le projet persisté avec son identifiant
     */
    Projet creer(Projet projet);

    /**
     * Supprime un projet.
     *
     * @param etudiantId identifiant de l'étudiant propriétaire
     * @param projetId   identifiant du projet à supprimer
     * @throws jakarta.persistence.EntityNotFoundException si le projet est introuvable ou n'appartient pas à cet étudiant
     */
    void supprimer(Long etudiantId, Long projetId);
}
