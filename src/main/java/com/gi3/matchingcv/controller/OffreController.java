package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.model.enums.TypeContrat;
import com.gi3.matchingcv.service.OffreService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/offres")
public class OffreController {
    private final OffreService offres;
    public OffreController(OffreService offres) { this.offres = offres; }

    @GetMapping
    public List<OffreDto> lister(@RequestParam(required = false) String domaine,
                                 @RequestParam(required = false) String localisation,
                                 @RequestParam(required = false) TypeContrat typeContrat) {
        return offres.listerPubliques(domaine, localisation, typeContrat);
    }

    @GetMapping("/{id}")
    public OffreDto trouver(@PathVariable Long id, @RequestParam(required = false) Long recruteurId) {
        // TODO sécurité : l'accès propriétaire doit utiliser l'identité authentifiée, pas le paramètre client.
        return offres.trouver(id, recruteurId);
    }

    // TODO sécurité : vérifier RECRUTEUR et l'identité authentifiée pour toutes les mutations.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OffreDto creer(@Valid @RequestBody OffreRequest request) { return offres.creer(request); }

    @PutMapping("/{id}")
    public OffreDto modifier(@PathVariable Long id, @Valid @RequestBody OffreRequest request) {
        return offres.modifier(id, request);
    }

    @PatchMapping("/{id}/cloturer")
    public OffreDto cloturer(@PathVariable Long id, @RequestParam Long recruteurId) {
        return offres.cloturer(id, recruteurId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable Long id, @RequestParam Long recruteurId) {
        offres.supprimer(id, recruteurId);
    }
}
