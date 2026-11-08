package com.gi3.matchingcv.controller;
import com.gi3.matchingcv.dto.CandidatureDto;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.*;
import com.gi3.matchingcv.repository.*;
import com.gi3.matchingcv.security.Sessions;
import com.gi3.matchingcv.service.CandidatureService;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
@RestController @Transactional
public class ApplicationWorkflowController {
    private final CandidatureRepository applications;private final InterviewRepository interviews;private final CandidatureService service;
    private final com.gi3.matchingcv.service.NotificationService notifications;
    public ApplicationWorkflowController(CandidatureRepository applications,InterviewRepository interviews,CandidatureService service,com.gi3.matchingcv.service.NotificationService notifications){this.applications=applications;this.interviews=interviews;this.service=service;this.notifications=notifications;}
    @PatchMapping("/api/candidatures/{id}/retirer") @PreAuthorize("hasRole('ETUDIANT')")
    public CandidatureDto withdraw(@PathVariable Long id){var c=applications.findById(id).orElseThrow();if(!c.getEtudiant().getId().equals(Sessions.current().id()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);if(c.getStatut()==StatutCandidature.RETIREE)return CandidatureDto.from(c);return service.changerStatut(id,StatutCandidature.RETIREE);}
    public record InterviewRequest(@NotNull @Future OffsetDateTime startsAt,@NotBlank @Size(max=500) String location,@Size(max=2000) String instructions){}
    @PutMapping("/api/candidatures/{id}/entretien") @PreAuthorize("hasRole('RECRUTEUR')")
    public Interview schedule(@PathVariable Long id,@Valid @RequestBody InterviewRequest r){
        var c=applications.findById(id).orElseThrow();
        if(c.getStatut()==StatutCandidature.REFUSEE || c.getStatut()==StatutCandidature.RETIREE)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Cette candidature ne permet plus de planifier un entretien.");
        if(c.getStatut()!=StatutCandidature.ENTRETIEN && c.getStatut()!=StatutCandidature.ACCEPTEE)
            service.changerStatut(id,StatutCandidature.ENTRETIEN);
        var previous=interviews.findById(id).orElse(null);
        String location=r.location().trim();
        String instructions=r.instructions()==null?"":r.instructions().trim();
        if(previous!=null && previous.getStartsAt().toInstant().equals(r.startsAt().toInstant()) && location.equals(previous.getLocation()) && instructions.equals(previous.getInstructions()==null?"":previous.getInstructions()))return previous;
        var i=new Interview();i.setCandidatureId(id);i.setStartsAt(r.startsAt());i.setLocation(location);i.setInstructions(instructions);interviews.save(i);
        String date=r.startsAt().atZoneSameInstant(java.time.ZoneId.of("Africa/Casablanca")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm"));
        notifications.notifier(c.getEtudiant().getId(),previous==null?TypeNotification.ENTRETIEN_PLANIFIE:TypeNotification.ENTRETIEN_MODIFIE,
            previous==null?"Invitation à un entretien":"Votre entretien a été modifié",
            "Rendez-vous le "+date+" (heure du Maroc). Lieu ou lien : "+location+". Consultez les instructions dans votre espace.",
            "/mes-entretiens.html#candidature-"+id,c.getOffre().getId(),id,null,null,Sessions.current().id());
        return i;
    }
    @GetMapping("/api/candidatures/{id}/entretien") public Interview get(@PathVariable Long id){return interviews.findById(id).orElse(null);}
}
