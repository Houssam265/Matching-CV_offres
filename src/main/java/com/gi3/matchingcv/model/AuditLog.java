package com.gi3.matchingcv.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Trace d'audit immuable enregistrant toute action administrative.
 * Pas de FK forte sur l'admin : la trace reste même si le compte est supprimé.
 */
@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_admin_id", columnList = "admin_id"),
        @Index(name = "idx_audit_date",     columnList = "date_action"),
        @Index(name = "idx_audit_action",   columnList = "action")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifiant de l'administrateur (nullable = action système). */
    @Column(name = "admin_id")
    private Long adminId;

    /** Nom affiché de l'admin au moment de l'action (dénormalisé pour traçabilité). */
    @Column(name = "admin_nom", length = 200)
    private String adminNom;

    /**
     * Code action : VALIDER_COMPETENCE, REJETER_COMPETENCE, FUSIONNER_COMPETENCE,
     * MODIFIER_COMPETENCE, IMPORT_CSV, SUSPENDRE_UTILISATEUR, REACTIVER_UTILISATEUR,
     * CREER_ADMIN, CLOTURER_OFFRE.
     */
    @Column(nullable = false, length = 80)
    private String action;

    /** Description de la cible (ex: "Compétence #42 'Python'"). */
    @Column(length = 300)
    private String cible;

    /** Détails libres (motif de rejet, nom cible fusion, etc.). */
    @Column(length = 1000)
    private String details;

    @Column(name = "date_action", nullable = false)
    private LocalDateTime dateAction;

    @PrePersist
    public void prePersist() {
        if (dateAction == null) {
            dateAction = LocalDateTime.now();
        }
    }
}
