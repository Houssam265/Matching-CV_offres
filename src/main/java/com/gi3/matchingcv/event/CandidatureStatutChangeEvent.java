package com.gi3.matchingcv.event;

import com.gi3.matchingcv.model.enums.StatutCandidature;

public record CandidatureStatutChangeEvent(
        Long candidatureId,
        Long etudiantId,
        Long offreId,
        StatutCandidature ancienStatut,
        StatutCandidature nouveauStatut,
        String offreTitre,
        Long auteurId
) {}
