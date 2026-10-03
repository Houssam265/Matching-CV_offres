package com.gi3.matchingcv.model;

import com.gi3.matchingcv.model.enums.TypeExigence;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "offre_competences",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_offre_competence_offre_competence", columnNames = {"offre_id", "competence_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OffreCompetence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeExigence typeExigence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offre_id", nullable = false)
    private Offre offre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competence_id", nullable = false)
    private Competence competence;
}
