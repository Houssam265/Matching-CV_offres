package com.gi3.matchingcv.security;

import com.gi3.matchingcv.model.enums.Role;
import com.gi3.matchingcv.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Default-deny ownership policy for the existing HTTP API. */
@Component
public class ResourceAccess {
    private final UtilisateurRepository users;
    private final OffreRepository offers;
    private final CandidatureRepository applications;
    private final NotificationRepository notifications;
    public ResourceAccess(UtilisateurRepository users, OffreRepository offers,
                          CandidatureRepository applications, NotificationRepository notifications) {
        this.users=users; this.offers=offers; this.applications=applications; this.notifications=notifications;
    }
    // Security filters run before MVC's persistence context is opened.
    // Keep ownership relationships attached for the entire authorization check.
    @Transactional(readOnly = true)
    public boolean allowed(AccountPrincipal p, HttpServletRequest r) {
        var user = users.findById(p.id()).orElse(null);
        if (user == null || !user.isActive() || user.getSecurityVersion()!=p.version() || user.getRole()!=p.role()) return false;
        String path=r.getRequestURI().substring(r.getContextPath().length());
        String method=r.getMethod(); boolean get=method.equals("GET");
        if (path.startsWith("/ws")) return true;
        if (path.startsWith("/api/account") || path.equals("/api/auth/me")) return true;
        if (p.role()==Role.ADMIN) return true;
        try {
            String[] parts=path.split("/");
            if (path.startsWith("/api/etudiants/")) return p.role()==Role.ETUDIANT && Long.parseLong(parts[3])==p.id();
            if (path.startsWith("/api/recruteurs/")) return p.role()==Role.RECRUTEUR && Long.parseLong(parts[3])==p.id();
            if (path.startsWith("/api/utilisateurs/")) return Long.parseLong(parts[3])==p.id();
            if (path.startsWith("/api/notifications/")) return notifications.findById(Long.parseLong(parts[3]))
                    .map(n->n.getDestinataire().getId().equals(p.id())).orElse(false);
            if (path.startsWith("/api/competences")) return get || (method.equals("POST") && path.equals("/api/competences/proposer"));
            if (path.equals("/api/candidatures")) return method.equals("POST") && p.role()==Role.ETUDIANT && ownParameter(r,"etudiantId",p.id(),true);
            if (path.startsWith("/api/candidatures/")) return applications.findById(Long.parseLong(parts[3])).map(c -> {
                if (p.role()==Role.ETUDIANT) return c.getEtudiant().getId().equals(p.id()) && ((get && path.endsWith("/entretien")) || path.endsWith("/retirer"));
                return p.role()==Role.RECRUTEUR && c.getOffre().getRecruteur().getId().equals(p.id())
                        && ownParameter(r,"recruteurId",p.id(),false);
            }).orElse(false);
            if (path.equals("/api/offres")) return get || (method.equals("POST") && p.role()==Role.RECRUTEUR);
            if (path.startsWith("/api/offres/")) {
                if (get && parts.length==4 && r.getParameter("recruteurId")==null) return true;
                return p.role()==Role.RECRUTEUR && ownParameter(r,"recruteurId",p.id(),false)
                        && offers.findById(Long.parseLong(parts[3])).map(o->o.getRecruteur().getId().equals(p.id())).orElse(false);
            }
        } catch (NumberFormatException | NullPointerException ignored) { return false; }
        return false;
    }
    private boolean ownParameter(HttpServletRequest r,String name,Long id,boolean required) {
        String value=r.getParameter(name);
        return value==null ? !required : id.equals(Long.valueOf(value));
    }
}
