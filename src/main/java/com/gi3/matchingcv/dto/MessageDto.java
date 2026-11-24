package com.gi3.matchingcv.dto;

import java.time.LocalDateTime;

/** Message d'une conversation, vu par l'utilisateur connecté ({@code moi} = envoyé par lui). */
public record MessageDto(Long id, Long expediteurId, boolean moi, String contenu, LocalDateTime dateEnvoi, boolean lu) {}
