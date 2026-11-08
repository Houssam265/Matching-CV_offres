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
}
