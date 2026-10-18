package com.gi3.matchingcv.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gi3.matchingcv.model.enums.TypeNotification;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notif_destinataire_lue", columnList = "destinataire_id, lue"),
        @Index(name = "idx_notif_destinataire_date", columnList = "destinataire_id, dateCreation")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destinataire_id", nullable = false)
    @JsonIgnore
    private Utilisateur destinataire;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TypeNotification type;

    @Column(nullable = false, length = 255)
    private String titre;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(length = 500)
    private String lien;

    private Double score;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offre_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @NotFound(action = NotFoundAction.IGNORE)
    private Offre offre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidature_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @NotFound(action = NotFoundAction.IGNORE)
    private Candidature candidature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "competence_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @NotFound(action = NotFoundAction.IGNORE)
    private Competence competence;

    @Column(nullable = false)
    private boolean lue = false;

    private LocalDateTime dateCreation;

    @PrePersist
    public void prePersist() {
        if (dateCreation == null) {
            dateCreation = LocalDateTime.now();
        }
    }

    /**
     * Méthodes de compatibilité et accesseurs sécurisés
     */
    public Long getOffreId() {
        return offre != null ? offre.getId() : null;
    }

    public Long getCandidatureId() {
        return candidature != null ? candidature.getId() : null;
    }

    public Long getCompetenceId() {
        return competence != null ? competence.getId() : null;
    }

    public Etudiant getEtudiant() {
        return destinataire instanceof Etudiant e ? e : null;
    }

    public LocalDateTime getDateEnvoi() {
        return dateCreation;
    }
}
