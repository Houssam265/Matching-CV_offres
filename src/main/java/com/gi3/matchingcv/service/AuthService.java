package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.AuthResponse;
import com.gi3.matchingcv.dto.ConnexionRequest;
import com.gi3.matchingcv.dto.InscriptionRequest;

/**
 * Interface du service d'authentification.
 * Gère l'inscription et la connexion des utilisateurs (Etudiant et Recruteur).
 */
public interface AuthService {

    /**
     * Inscrit un nouvel utilisateur (Etudiant ou Recruteur) et retourne ses infos publiques.
     *
     * @param request les données d'inscription
     * @return les données publiques de l'utilisateur créé
     * @throws IllegalArgumentException si l'email est déjà utilisé ou si le rôle est invalide
     */
    AuthResponse inscrire(InscriptionRequest request);

    /**
     * Authentifie un utilisateur par email + mot de passe et retourne ses infos.
     *
     * @param request les identifiants de connexion
     * @return les données publiques de l'utilisateur authentifié
     * @throws IllegalArgumentException si l'email ou le mot de passe est incorrect
     */
    AuthResponse connecter(ConnexionRequest request);
}
