package com.gi3.matchingcv.model;

import com.gi3.matchingcv.model.enums.Provenance;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "profil_competences",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_profil_competence_etudiant_competence", columnNames = {"etudiant_id", "competence_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProfilCompetence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Provenance provenance;

    private Integer dureeMois;

    private LocalDateTime dateAjout;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competence_id", nullable = false)
    private Competence competence;

    @PrePersist
    public void prePersist() {
        if (dateAjout == null) {
            dateAjout = LocalDateTime.now();
        }
    }
}
