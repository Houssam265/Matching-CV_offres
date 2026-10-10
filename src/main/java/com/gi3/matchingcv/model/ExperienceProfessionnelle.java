package com.gi3.matchingcv.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Représente une expérience professionnelle (stage, emploi…) d'un étudiant.
 * dateFin nullable = poste actuel (en cours).
 */
@Entity
@Table(name = "experiences_professionnelles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceProfessionnelle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "L'intitulé du poste est obligatoire")
    @Column(nullable = false)
    private String poste;

    @NotBlank(message = "Le nom de l'entreprise est obligatoire")
    @Column(nullable = false)
    private String entreprise;

    @NotNull(message = "La date de début est obligatoire")
    @Column(nullable = false)
    private LocalDate dateDebut;

    /** Null si le poste est toujours en cours. */
    private LocalDate dateFin;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "experience_competence",
        joinColumns = @JoinColumn(name = "experience_id"),
        inverseJoinColumns = @JoinColumn(name = "competence_id")
    )
    private List<Competence> competences = new ArrayList<>();

    @JsonIgnore
    @AssertTrue(message = "La date de fin ne peut pas être antérieure à la date de début")
    public boolean isDateFinValide() {
        if (dateDebut == null || dateFin == null) {
            return true;
        }
        return !dateFin.isBefore(dateDebut);
    }
}
