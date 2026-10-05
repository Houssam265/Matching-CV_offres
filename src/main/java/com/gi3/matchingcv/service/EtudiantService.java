package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.Etudiant;

import java.util.List;

/**
 * Interface du service gérant les opérations métier sur les étudiants.
 * Permet l'inversion de contrôle (IoC) en découplant les consommateurs du service
 * de son implémentation concrète.
 */
public interface EtudiantService {

    /**
     * Récupère la liste de tous les étudiants enregistrés.
     *
     * @return la liste des étudiants
     */
    List<Etudiant> listerTous();

    /**
     * Enregistre un nouvel étudiant dans le système.
     * Valide l'unicité de l'e-mail avant la sauvegarde.
     *
     * @param etudiant l'entité étudiant à créer
     * @return l'étudiant persisté avec son identifiant généré
     * @throws com.gi3.matchingcv.exception.EmailDejaUtiliseException si l'e-mail est déjà utilisé
     */
    Etudiant creer(Etudiant etudiant);

    /**
     * Recherche un étudiant par son identifiant unique.
     *
     * @param id l'identifiant de l'étudiant
     * @return l'étudiant trouvé
     * @throws jakarta.persistence.EntityNotFoundException si aucun étudiant n'existe avec cet identifiant
     */
    Etudiant trouverParId(Long id);

    /**
     * Sauvegarde (met à jour) un étudiant déjà persisté sans effectuer la vérification d'unicité de l'e-mail.
     * À utiliser uniquement pour les modifications de profil (ajout/retrait de compétences, projets, expériences).
     *
     * @param etudiant l'entité étudiant à mettre à jour
     * @return l'étudiant mis à jour
     */
    Etudiant sauvegarder(Etudiant etudiant);
}
