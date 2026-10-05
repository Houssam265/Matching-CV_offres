package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.AuthResponse;
import com.gi3.matchingcv.dto.ConnexionRequest;
import com.gi3.matchingcv.dto.InscriptionRequest;
import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.Recruteur;
import com.gi3.matchingcv.model.Utilisateur;
import com.gi3.matchingcv.model.enums.Role;
import com.gi3.matchingcv.repository.EtudiantRepository;
import com.gi3.matchingcv.repository.RecruteurRepository;
import com.gi3.matchingcv.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation du service d'authentification.
 * Utilise l'injection par constructeur (IoC) pour toutes ses dépendances.
 *
 * Note : le mot de passe est stocké en clair pour cette version pédagogique.
 * En production, utiliser BCryptPasswordEncoder de Spring Security.
 */
@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final EtudiantRepository etudiantRepository;
    private final RecruteurRepository recruteurRepository;

    /**
     * Injection par constructeur (IoC/DI) : Spring injecte automatiquement les repositories
     * depuis le conteneur IoC, démontrant le principe d'inversion de contrôle.
     */
    public AuthServiceImpl(UtilisateurRepository utilisateurRepository,
                           EtudiantRepository etudiantRepository,
                           RecruteurRepository recruteurRepository) {
        this.utilisateurRepository = utilisateurRepository;
        this.etudiantRepository = etudiantRepository;
        this.recruteurRepository = recruteurRepository;
    }

    @Override
    public AuthResponse inscrire(InscriptionRequest request) {
        // Vérification unicité de l'email
        if (utilisateurRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            throw new IllegalArgumentException("Un compte existe déjà avec l'adresse email : " + request.getEmail());
        }

        Utilisateur saved;

        if (request.getRole() == Role.ETUDIANT) {
            Etudiant etudiant = new Etudiant();
            etudiant.setPrenom(request.getPrenom().trim());
            etudiant.setNom(request.getNom().trim());
            etudiant.setEmail(request.getEmail().trim().toLowerCase());
            etudiant.setMotDePasse(request.getMotDePasse());
            etudiant.setRole(Role.ETUDIANT);
            if (request.getFiliere() != null && !request.getFiliere().isBlank()) {
                etudiant.setFiliere(request.getFiliere().trim());
            }
            if (request.getEtablissement() != null && !request.getEtablissement().isBlank()) {
                etudiant.setEtablissement(request.getEtablissement().trim());
            }
            saved = etudiantRepository.save(etudiant);

        } else if (request.getRole() == Role.RECRUTEUR) {
            Recruteur recruteur = new Recruteur();
            recruteur.setPrenom(request.getPrenom().trim());
            recruteur.setNom(request.getNom().trim());
            recruteur.setEmail(request.getEmail().trim().toLowerCase());
            recruteur.setMotDePasse(request.getMotDePasse());
            recruteur.setRole(Role.RECRUTEUR);
            recruteur.setNomEntreprise(request.getNomEntreprise() != null ? request.getNomEntreprise().trim() : null);
            recruteur.setSecteurActivite(request.getSecteurActivite() != null ? request.getSecteurActivite().trim() : null);
            saved = recruteurRepository.save(recruteur);

        } else {
            throw new IllegalArgumentException("Rôle non autorisé pour l'inscription : " + request.getRole());
        }

        return new AuthResponse(
                saved.getId(),
                saved.getPrenom(),
                saved.getNom(),
                saved.getEmail(),
                saved.getRole(),
                "Compte créé avec succès. Bienvenue, " + saved.getPrenom() + " !"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse connecter(ConnexionRequest request) {
        String emailNormalise = request.getEmail().trim().toLowerCase();

        Utilisateur utilisateur = utilisateurRepository.findByEmail(emailNormalise)
                .orElseThrow(() -> new IllegalArgumentException("Identifiants incorrects. Vérifiez votre email et mot de passe."));

        // Vérification du mot de passe (comparaison directe — version pédagogique)
        if (!utilisateur.getMotDePasse().equals(request.getMotDePasse())) {
            throw new IllegalArgumentException("Identifiants incorrects. Vérifiez votre email et mot de passe.");
        }

        return new AuthResponse(
                utilisateur.getId(),
                utilisateur.getPrenom(),
                utilisateur.getNom(),
                utilisateur.getEmail(),
                utilisateur.getRole(),
                "Connexion réussie. Bon retour, " + utilisateur.getPrenom() + " !"
        );
    }
}
