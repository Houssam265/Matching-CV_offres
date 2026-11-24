package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.ConversationDetailDto;
import com.gi3.matchingcv.dto.ConversationDto;
import com.gi3.matchingcv.dto.MessageDto;
import com.gi3.matchingcv.security.Sessions;
import com.gi3.matchingcv.service.MessagerieService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Messagerie recruteur ↔ candidat. L'identité vient toujours de la session. */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessagerieService messagerie;

    public MessageController(MessagerieService messagerie) {
        this.messagerie = messagerie;
    }

    @GetMapping("/conversations")
    public List<ConversationDto> conversations() {
        return messagerie.lister(Sessions.current().id());
    }

    @GetMapping("/conversations/{candidatureId}")
    public ConversationDetailDto conversation(@PathVariable Long candidatureId) {
        return messagerie.ouvrir(candidatureId, Sessions.current().id());
    }

    public record EnvoiRequest(String contenu) {}

    @PostMapping("/conversations/{candidatureId}")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto envoyer(@PathVariable Long candidatureId, @RequestBody EnvoiRequest request) {
        return messagerie.envoyer(candidatureId, Sessions.current().id(), request.contenu());
    }

    @GetMapping("/non-lus")
    public Map<String, Long> nonLus() {
        return Map.of("count", messagerie.compterNonLus(Sessions.current().id()));
    }
}
