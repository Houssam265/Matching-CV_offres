package com.gi3.matchingcv.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Message échangé entre un recruteur et un candidat. Une conversation correspond
 * à une candidature : ses deux seuls participants sont l'étudiant et le recruteur
 * propriétaire de l'offre.
 */
@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_message_candidature_date", columnList = "candidature_id, dateEnvoi")
})
@Getter @Setter @NoArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidature_id", nullable = false)
    private Candidature candidature;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expediteur_id", nullable = false)
    private Utilisateur expediteur;

    @Column(nullable = false, length = 2000)
    private String contenu;

    @Column(nullable = false)
    private LocalDateTime dateEnvoi;

    @Column(nullable = false)
    private boolean lu = false;

    @PrePersist
    void prePersist() {
        if (dateEnvoi == null) dateEnvoi = LocalDateTime.now();
    }
}
