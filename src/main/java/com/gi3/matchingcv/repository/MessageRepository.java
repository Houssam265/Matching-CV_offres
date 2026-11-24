package com.gi3.matchingcv.repository;

import com.gi3.matchingcv.model.Message;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @EntityGraph(attributePaths = {"expediteur"})
    List<Message> findByCandidatureIdOrderByDateEnvoiAsc(Long candidatureId);

    boolean existsByCandidatureIdAndExpediteurId(Long candidatureId, Long expediteurId);

    /** Tous les messages des conversations où l'utilisateur participe, du plus récent au plus ancien. */
    @EntityGraph(attributePaths = {"expediteur", "candidature", "candidature.offre", "candidature.offre.recruteur", "candidature.etudiant"})
    @Query("SELECT m FROM Message m WHERE m.candidature.etudiant.id = :userId OR m.candidature.offre.recruteur.id = :userId ORDER BY m.dateEnvoi DESC, m.id DESC")
    List<Message> findAllForParticipant(@Param("userId") Long userId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.lu = false AND m.expediteur.id <> :userId " +
           "AND (m.candidature.etudiant.id = :userId OR m.candidature.offre.recruteur.id = :userId)")
    long countNonLus(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Message m SET m.lu = true WHERE m.candidature.id = :candidatureId AND m.expediteur.id <> :userId AND m.lu = false")
    int marquerLus(@Param("candidatureId") Long candidatureId, @Param("userId") Long userId);
}
