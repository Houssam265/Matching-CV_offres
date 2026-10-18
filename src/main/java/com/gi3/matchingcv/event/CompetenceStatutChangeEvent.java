package com.gi3.matchingcv.event;

import com.gi3.matchingcv.model.enums.StatutCompetence;

public record CompetenceStatutChangeEvent(
        Long competenceId,
        String competenceNom,
        StatutCompetence ancienStatut,
        StatutCompetence nouveauStatut,
        Long proposeeParId
) {}
