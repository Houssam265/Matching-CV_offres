package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.Administrateur;
import com.gi3.matchingcv.model.AuditLog;
import com.gi3.matchingcv.repository.AdministrateurRepository;
import com.gi3.matchingcv.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Implémentation du service d'audit.
 * // TODO sécurité : réservé ADMIN.
 */
@Service
@Transactional
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AdministrateurRepository administrateurRepository;

    public AuditServiceImpl(AuditLogRepository auditLogRepository,
                            AdministrateurRepository administrateurRepository) {
        this.auditLogRepository = auditLogRepository;
        this.administrateurRepository = administrateurRepository;
    }

    @Override
    public AuditLog journaliser(Long adminId, String action, String cible, String details) {
        Administrateur admin = adminId != null
                ? administrateurRepository.findById(adminId).orElse(null)
                : null;
        String adminNom = admin != null
                ? admin.getPrenom() + " " + admin.getNom()
                : "Système";

        AuditLog log = new AuditLog();
        log.setAdminId(adminId);
        log.setAdminNom(adminNom);
        log.setAction(action);
        log.setCible(cible);
        log.setDetails(details);
        return auditLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLog> listerLogs(String action, String query) {
        List<AuditLog> logs;
        if (action != null && !action.isBlank()) {
            logs = auditLogRepository.findByActionOrderByDateActionDesc(action.trim());
        } else {
            logs = auditLogRepository.findAllByOrderByDateActionDesc();
        }

        if (query == null || query.isBlank()) {
            return logs;
        }

        String q = query.trim().toLowerCase(Locale.ROOT);
        return logs.stream()
                .filter(l -> (l.getCible() != null && l.getCible().toLowerCase(Locale.ROOT).contains(q))
                        || (l.getAdminNom() != null && l.getAdminNom().toLowerCase(Locale.ROOT).contains(q))
                        || (l.getDetails() != null && l.getDetails().toLowerCase(Locale.ROOT).contains(q))
                        || (l.getAction() != null && l.getAction().toLowerCase(Locale.ROOT).contains(q)))
                .toList();
    }
}
