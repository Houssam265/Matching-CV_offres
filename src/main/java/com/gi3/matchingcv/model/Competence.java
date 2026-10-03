package com.gi3.matchingcv.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "competences",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_competence_nom_normalise", columnNames = {"nom_normalise"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Competence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Le nom de la comp\u00e9tence est obligatoire")
    @Column(nullable = false, unique = true)
    private String nom;

    @JsonIgnore
    @Column(name = "nom_normalise", nullable = false, unique = true)
    private String nomNormalise;

    private String categorie;

    @Enumerated(EnumType.STRING)
    private StatutCompetence statut;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "competence_synonymes", joinColumns = @JoinColumn(name = "competence_id"))
    @Column(name = "synonyme")
    private List<String> synonymes = new ArrayList<>();

    /**
     * Normalise un nom de comp\u00e9tence :
     * - conversion en minuscules
     * - suppression exclusive des espaces, tirets (-) et underscores (_)
     * - conservation intacte de tous les autres caract\u00e8res (+, #, ., etc.)
     */
    public static String normaliserNom(String nom) {
        if (nom == null) {
            return null;
        }
        return nom.toLowerCase().replaceAll("[\\s_\\-]+", "");
    }

    @PrePersist
    @PreUpdate
    public void calculerNomNormalise() {
        if (this.nom != null) {
            this.nomNormalise = normaliserNom(this.nom);
        }
    }
}

