package com.gi3.matchingcv.model;

import com.gi3.matchingcv.model.enums.StatutOffre;
import com.gi3.matchingcv.model.enums.TypeContrat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "offres")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Offre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titre;

    @Column(length = 4000)
    private String description;

    private String domaine;

    private String localisation;

    @Enumerated(EnumType.STRING)
    private TypeContrat typeContrat;

    @Enumerated(EnumType.STRING)
    private StatutOffre statut = StatutOffre.ACTIVE;

    private LocalDateTime datePublication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruteur_id")
    private Recruteur recruteur;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "offre_competence",
        joinColumns = @JoinColumn(name = "offre_id"),
        inverseJoinColumns = @JoinColumn(name = "competence_id")
    )
    private List<Competence> competences = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (datePublication == null) {
            datePublication = LocalDateTime.now();
        }
        if (statut == null) {
            statut = StatutOffre.ACTIVE;
        }
    }
}
