package com.gi3.matchingcv.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "administrateurs")
@Getter
@Setter
@NoArgsConstructor
public class Administrateur extends Utilisateur {
}
