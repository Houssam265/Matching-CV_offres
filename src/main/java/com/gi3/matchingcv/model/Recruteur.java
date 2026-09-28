package com.gi3.matchingcv.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recruteurs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Recruteur extends Utilisateur {

    private String nomEntreprise;

    private String secteurActivite;

    @OneToMany(mappedBy = "recruteur", fetch = FetchType.LAZY)
    private List<Offre> offres = new ArrayList<>();
}
