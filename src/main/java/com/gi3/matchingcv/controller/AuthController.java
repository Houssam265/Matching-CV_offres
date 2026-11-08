package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.AuthResponse;
import com.gi3.matchingcv.dto.ConnexionRequest;
import com.gi3.matchingcv.dto.InscriptionRequest;
import com.gi3.matchingcv.repository.UtilisateurRepository;
import com.gi3.matchingcv.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Contrôleur REST pour l'authentification.
 * Base URL : /api/auth
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UtilisateurRepository utilisateurRepository;

    /**
     * Injection par constructeur (IoC) : Spring injecte AuthService (interface)
     * et UtilisateurRepository directement depuis le conteneur IoC.
     */
    public AuthController(AuthService authService, UtilisateurRepository utilisateurRepository) {
        this.authService = authService;
        this.utilisateurRepository = utilisateurRepository;
    }

    /**
     * POST /api/auth/inscription — Crée un nouveau compte Etudiant ou Recruteur.
     *
     * @return 201 Created, 400 si données invalides, 409 si email déjà utilisé
     */
    @PostMapping("/inscription")
    public ResponseEntity<?> inscrire(@Valid @RequestBody InscriptionRequest request,
                                      BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String message = bindingResult.getFieldErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst()
                    .orElse("Données invalides");
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
        try {
            AuthResponse response = authService.inscrire(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Service temporairement indisponible."));
        }
    }

    /**
     * POST /api/auth/connexion — Authentifie un utilisateur par email + mot de passe.
     *
     * @return 200 OK avec infos publiques, ou 401 si identifiants incorrects
     */
    @PostMapping("/connexion")
    public ResponseEntity<?> connecter(@Valid @RequestBody ConnexionRequest request,
                                       BindingResult bindingResult,
                                       jakarta.servlet.http.HttpServletRequest httpRequest,
                                       jakarta.servlet.http.HttpServletResponse httpResponse) {
        if (bindingResult.hasErrors()) {
            String message = bindingResult.getFieldErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst()
                    .orElse("Données invalides");
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
        try {
            AuthResponse response = authService.connecter(request);
            com.gi3.matchingcv.security.Sessions.login(utilisateurRepository.findById(response.getId()).orElseThrow(), httpRequest, httpResponse);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Service temporairement indisponible."));
        }
    }

    /**
     * GET /api/auth/check-email?email=...
     * Vérifie directement via UtilisateurRepository si l'email est déjà en base.
     * Fix: utilisation de existsByEmail() au lieu du hack via connecter() qui ne pouvait
     * pas distinguer "email inexistant" de "mauvais mot de passe".
     *
     * @return { "disponible": true } si l'email est libre, { "disponible": false } si pris
     */
    @GetMapping("/csrf")
    public Map<String,String> csrf(org.springframework.security.web.csrf.CsrfToken token) {
        return Map.of("token",token.getToken(),"headerName",token.getHeaderName());
    }

    @GetMapping("/me")
    public AuthResponse me() {
        var u=utilisateurRepository.findById(com.gi3.matchingcv.security.Sessions.current().id()).orElseThrow();
        return new AuthResponse(u.getId(),u.getPrenom(),u.getNom(),u.getEmail(),u.getRole(),"Session active");
    }

    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestParam String email) {
        boolean existe = utilisateurRepository.existsByEmail(email.trim().toLowerCase());
        return ResponseEntity.ok(Map.of("disponible", !existe));
    }
}
