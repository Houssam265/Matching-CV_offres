package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.enums.StatutCandidature;

import java.time.LocalDateTime;

/**
 * Résumé d'une conversation (une par candidature) pour l'utilisateur connecté.
 * Aucune coordonnée personnelle (email) n'est exposée : les échanges passent par la messagerie.
 */
public record ConversationDto(
        Long candidatureId,
        Long offreId,
        String offreTitre,
        String entreprise,
        Long interlocuteurId,
        String interlocuteurNom,
        String interlocuteurDetail,
        StatutCandidature statut,
        String dernierMessage,
        LocalDateTime dateDernierMessage,
        boolean dernierEnvoyeParMoi,
        long nonLus,
        boolean peutEcrire,
        String raisonFermeture
) {}
