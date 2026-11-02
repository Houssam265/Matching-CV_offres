package com.gi3.matchingcv.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RecruteurProfilRequest(
        @Size(max = 255)
        String prenom,

        @Size(max = 255)
        String nom,

        @NotBlank(message = "Le nom de l'entreprise est obligatoire.")
        @Size(max = 255)
        String nomEntreprise,

        @Size(max = 255)
        String secteurActivite,

        @Size(max = 255)
        String ville,

        @Pattern(regexp = "^(https?://.*|)$", message = "Le site web doit être une URL valide commençant par http:// ou https:// (ou laissé vide).")
        @Size(max = 255)
        String siteWeb,

        @Size(max = 2000)
        String descriptionEntreprise
) {
}
