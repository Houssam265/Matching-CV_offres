package com.gi3.matchingcv.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO de requête pour la connexion (authentification).
 */
public class ConnexionRequest {

    @Email(message = "L'email n'est pas valide")
    @NotBlank(message = "L'email est obligatoire")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    private String motDePasse;
    private com.gi3.matchingcv.model.enums.Role role;
    public com.gi3.matchingcv.model.enums.Role getRole() { return role; }
    public void setRole(com.gi3.matchingcv.model.enums.Role role) { this.role = role; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }
}
