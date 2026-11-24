package com.gi3.matchingcv.model;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class AccountProfile {
    @Id private Long userId;
    private String headline;
    @Column(length=2000) private String biography;
    private String availability;
    private String professionalUrl;
    private String portfolioUrl;
    private Boolean notificationsEnabled=true;
    // Coordonnées et rubriques du CV (toutes optionnelles)
    @Column(length=254) private String contactEmail;
    @Column(length=30) private String telephone;
    @Column(length=100) private String ville;
    @Column(length=500) private String githubUrl;
    @Column(length=100) private String formationPeriode;
    @Column(length=500) private String langues;
    @Column(length=500) private String interets;
}
