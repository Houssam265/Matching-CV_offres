package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.enums.Role;

/**
 * DTO de réponse retourné après une connexion ou inscription réussie.
 * Contient les informations publiques de l'utilisateur (jamais le mot de passe).
 */
public class AuthResponse {

    private Long id;
    private String prenom;
    private String nom;
    private String email;
    private Role role;
    private String message;

    public AuthResponse() {}

    public AuthResponse(Long id, String prenom, String nom, String email, Role role, String message) {
        this.id = id;
        this.prenom = prenom;
        this.nom = nom;
        this.email = email;
        this.role = role;
        this.message = message;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
