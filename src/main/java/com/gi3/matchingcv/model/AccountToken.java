package com.gi3.matchingcv.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Getter @Setter @NoArgsConstructor
public class AccountToken {
    @Id @Column(length=64) private String hash;
    private Long userId;
    @Column(length=16) private String purpose;
    private Instant expiresAt;
}
