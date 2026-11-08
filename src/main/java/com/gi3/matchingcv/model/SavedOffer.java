package com.gi3.matchingcv.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Getter @Setter @NoArgsConstructor
@Table(uniqueConstraints=@UniqueConstraint(columnNames={"userId","offerId"}))
public class SavedOffer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long userId; private Long offerId; private Instant savedAt=Instant.now();
}
