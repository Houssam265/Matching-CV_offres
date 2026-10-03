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

    @OneToMany(mappedBy = "offre", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OffreCompetence> offreCompetences = new ArrayList<>();

    @OneToMany(mappedBy = "offre", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Candidature> candidatures = new ArrayList<>();

    @OneToMany(mappedBy = "offre", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Notification> notifications = new ArrayList<>();

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
