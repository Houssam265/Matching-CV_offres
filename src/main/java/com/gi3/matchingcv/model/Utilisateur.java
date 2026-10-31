package com.gi3.matchingcv.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gi3.matchingcv.model.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "utilisateurs")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;

    private String prenom;

    @Column(nullable = false, unique = true)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private LocalDateTime dateInscription;

    /**
     * Indique si le compte est actif. Un compte suspendu (actif=false) ne peut plus
     * se connecter et ses offres sont masquées. Jamais de suppression physique.
     */
    @Column(nullable = false, columnDefinition = "BIT(1) DEFAULT 1")
    private boolean actif = true;

    @PrePersist
    public void prePersist() {
        if (dateInscription == null) {
            dateInscription = LocalDateTime.now();
        }
    }
}
