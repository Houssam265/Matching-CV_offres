package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/recruteurs")
public class RecruteurController {
    private final RecruteurService recruteurs;
    private final OffreService offres;
    public RecruteurController(RecruteurService recruteurs, OffreService offres) {
        this.recruteurs = recruteurs; this.offres = offres;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse inscrire(@Valid @RequestBody RecruteurInscriptionRequest request) {
        return recruteurs.inscrire(request);
    }

    // TODO sécurité : vérifier RECRUTEUR et que l'identité authentifiée correspond à id.
    @GetMapping("/{id}")
    public RecruteurDto trouver(@PathVariable Long id) { return recruteurs.trouver(id); }

    @GetMapping("/{id}/offres")
    public List<OffreDto> lister(@PathVariable Long id) { return offres.listerParRecruteur(id); }

    @GetMapping("/{id}/competences/suggestions")
    public List<Competence> suggerer(@PathVariable Long id, @RequestParam(defaultValue = "") String q) {
        return recruteurs.suggerer(id, q);
    }

    public record Proposition(@NotBlank @Size(max = 255) String nom, @Size(max = 255) String categorie) {}

    @PostMapping("/{id}/competences/propositions")
    public Competence proposer(@PathVariable Long id, @Valid @RequestBody Proposition request) {
        return recruteurs.proposer(id, request.nom(), request.categorie());
    }
}
