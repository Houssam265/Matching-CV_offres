package com.gi3.matchingcv.model;

import com.gi3.matchingcv.model.enums.StatutCandidature;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "candidatures",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_candidature_etudiant_offre", columnNames = {"etudiant_id", "offre_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Candidature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double score;

    @Enumerated(EnumType.STRING)
    private StatutCandidature statut = StatutCandidature.ENVOYEE;

    private LocalDateTime dateCandidature;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offre_id", nullable = false)
    private Offre offre;

    @PrePersist
    public void prePersist() {
        if (dateCandidature == null) {
            dateCandidature = LocalDateTime.now();
        }
        if (statut == null) {
            statut = StatutCandidature.ENVOYEE;
        }
    }
}
