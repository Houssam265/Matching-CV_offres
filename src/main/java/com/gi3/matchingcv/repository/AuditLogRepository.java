package com.gi3.matchingcv.repository;

import com.gi3.matchingcv.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByAdminIdOrderByDateActionDesc(Long adminId);

    List<AuditLog> findByActionAndDateActionBetween(String action, LocalDateTime start, LocalDateTime end);
}
