package com.gi3.matchingcv.model;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class AccountDocument {
    @Id private Long userId;
    @Lob @Column(length=5242880) private byte[] content;
    private String filename;
}
