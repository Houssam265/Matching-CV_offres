package com.gi3.matchingcv.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
@Entity @Getter @Setter @NoArgsConstructor
public class Interview {
    @Id private Long candidatureId;
    private OffsetDateTime startsAt;
    @Column(length=500) private String location;
    @Column(length=2000) private String instructions;
}
