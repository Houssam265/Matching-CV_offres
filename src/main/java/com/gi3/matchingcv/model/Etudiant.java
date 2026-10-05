package com.gi3.matchingcv.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "etudiants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Etudiant extends Utilisateur {

    @Column(length = 100)
    private String filiere;

    @Column(length = 150)
    private String etablissement;

    @JsonIgnore
    @OneToMany(mappedBy = "etudiant", fetch = FetchType.LAZY)
    private List<ProfilCompetence> profilCompetences = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "etudiant", fetch = FetchType.LAZY)
    private List<Candidature> candidatures = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "etudiant", fetch = FetchType.LAZY)
    private List<Notification> notifications = new ArrayList<>();

    /** Compétences déclarées directement par l'étudiant (sans niveau de maîtrise). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "etudiant_competence",
        joinColumns = @JoinColumn(name = "etudiant_id"),
        inverseJoinColumns = @JoinColumn(name = "competence_id")
    )
    private List<Competence> competences = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "etudiant", fetch = FetchType.LAZY)
    private List<Projet> projets = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "etudiant", fetch = FetchType.LAZY)
    private List<ExperienceProfessionnelle> experiences = new ArrayList<>();
}
