package com.gi3.matchingcv.controller;
import com.gi3.matchingcv.service.AccountRecoveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/auth")
public class RecoveryController {
    private final AccountRecoveryService recovery;
    public RecoveryController(AccountRecoveryService recovery){this.recovery=recovery;}
    public record EmailRequest(@NotBlank @Email @Size(max=254) String email){}
    public record ResetRequest(@NotBlank @Size(max=100) String token,@NotBlank @Size(max=72) String motDePasse){}
    public record VerifyRequest(@NotBlank @Size(max=100) String token){}
    @PostMapping("/password-reset/request")
    public Map<String,String> request(@Valid @RequestBody EmailRequest r){recovery.send(r.email(),"RESET");return Map.of("message","Si le compte existe, un lien a été envoyé.");}
    @PostMapping("/password-reset/confirm")
    public Map<String,String> confirm(@Valid @RequestBody ResetRequest r){recovery.consume(r.token(),"RESET",r.motDePasse());return Map.of("message","Mot de passe modifié. Vous pouvez vous connecter.");}
    @PostMapping("/verify-email")
    public Map<String,String> verify(@Valid @RequestBody VerifyRequest r){recovery.consume(r.token(),"VERIFY",null);return Map.of("message","Adresse email vérifiée.");}
}
