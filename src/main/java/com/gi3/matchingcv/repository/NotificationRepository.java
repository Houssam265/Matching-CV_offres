package com.gi3.matchingcv.repository;

import com.gi3.matchingcv.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByEtudiantIdAndLueFalse(Long etudiantId);
}
