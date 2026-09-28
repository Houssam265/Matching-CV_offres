package com.gi3.matchingcv.model;

import com.gi3.matchingcv.model.enums.StatutCompetence;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "competences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Competence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nom;

    private String categorie;

    @Enumerated(EnumType.STRING)
    private StatutCompetence statut;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "competence_synonymes", joinColumns = @JoinColumn(name = "competence_id"))
    @Column(name = "synonyme")
    private List<String> synonymes = new ArrayList<>();
}
