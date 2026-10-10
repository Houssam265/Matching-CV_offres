package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import java.util.Objects;

final class CompetenceOffrePolicy {
    private CompetenceOffrePolicy() {}

    static void verifierUtilisable(Competence competence, Long recruteurId) {
        boolean validee = competence.getStatut() == StatutCompetence.VALIDEE;
        boolean personnelle = competence.getStatut() == StatutCompetence.EN_ATTENTE &&
                competence.getProposeePar() != null && recruteurId != null &&
                Objects.equals(competence.getProposeePar().getId(), recruteurId);
        if (!validee && !personnelle)
            throw new IllegalArgumentException("Cette compétence n'est pas utilisable : elle doit être validée ou proposée par vous et en attente.");
    }
}
