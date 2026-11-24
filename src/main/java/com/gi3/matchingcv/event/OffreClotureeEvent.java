package com.gi3.matchingcv.event;

import java.util.List;

/**
 * Une offre vient d'être clôturée (par le recruteur ou à sa date limite).
 * {@code etudiantIds} : candidats dont la candidature était encore en cours.
 */
public record OffreClotureeEvent(Long offreId, String titre, List<Long> etudiantIds) {}
