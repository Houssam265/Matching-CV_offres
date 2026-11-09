package com.gi3.matchingcv.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Requête de création d'un compte administrateur par un administrateur existant.
 */
public record AdminCreationRequest(
        @NotBlank(message = "Le prénom est obligatoire")
        String prenom,

        @NotBlank(message = "Le nom est obligatoire")
        String nom,

        @NotBlank(message = "L'adresse email est obligatoire")
        @Email(message = "L'adresse email doit être valide")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 6, message = "Le mot de passe doit comporter au moins 6 caractères")
        String motDePasse
) {
}
