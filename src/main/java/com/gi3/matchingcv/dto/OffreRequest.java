package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.enums.TypeContrat;
import com.gi3.matchingcv.model.enums.TypeExigence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public record OffreRequest(
        @NotNull @Positive Long recruteurId,
        @NotBlank @Size(max = 255) String titre,
        @NotBlank @Size(max = 4000) String description,
        @NotBlank @Size(max = 255) String domaine,
        @NotBlank @Size(max = 255) String localisation,
        @NotNull TypeContrat typeContrat,
        @NotEmpty List<@NotNull @Valid Exigence> competences,
        LocalDateTime dateLimite) {

    public OffreRequest(Long recruteurId, String titre, String description, String domaine,
                        String localisation, TypeContrat typeContrat, List<Exigence> competences) {
        this(recruteurId, titre, description, domaine, localisation, typeContrat, competences, null);
    }

    public record Exigence(@NotNull @Positive Long competenceId, @NotNull TypeExigence typeExigence) {}
}
