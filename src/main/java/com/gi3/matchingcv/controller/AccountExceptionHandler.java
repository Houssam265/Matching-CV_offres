package com.gi3.matchingcv.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.Map;
@RestControllerAdvice(assignableTypes={AccountController.class,RecoveryController.class,SavedOfferController.class,ApplicationWorkflowController.class})
public class AccountExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Opération impossible.":e.getReason()));}
    @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<?> invalid(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(Map.of("message","Vérifiez les champs du formulaire : "+e.getBindingResult().getFieldErrors().get(0).getField()));}
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict(IllegalStateException e){return ResponseEntity.status(409).body(Map.of("message",e.getMessage()));}
    @ExceptionHandler(org.springframework.mail.MailException.class) public ResponseEntity<?> email(Exception e){return ResponseEntity.status(503).body(Map.of("message","Envoi impossible. Réessayez plus tard."));}
}
