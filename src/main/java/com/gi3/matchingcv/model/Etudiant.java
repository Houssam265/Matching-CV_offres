package com.gi3.matchingcv.model;

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

    @Column(name = "chemin_cv", nullable = true)
    private String cheminCV;

    @OneToMany(mappedBy = "etudiant", fetch = FetchType.LAZY)
    private List<ProfilCompetence> profilCompetences = new ArrayList<>();

    @OneToMany(mappedBy = "etudiant", fetch = FetchType.LAZY)
    private List<Candidature> candidatures = new ArrayList<>();

    @OneToMany(mappedBy = "etudiant", fetch = FetchType.LAZY)
    private List<Notification> notifications = new ArrayList<>();
}
