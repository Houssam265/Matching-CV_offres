package com.gi3.matchingcv.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Photo de profil d'un utilisateur (tous rôles). Une seule photo par compte. */
@Entity
@Table(name = "account_photo")
@Getter @Setter @NoArgsConstructor
public class AccountPhoto {
    @Id
    private Long userId;

    @Lob
    @Column(length = 2_097_152, nullable = false)
    private byte[] content;

    @Column(nullable = false, length = 50)
    private String contentType;

    private LocalDateTime updatedAt;
}
