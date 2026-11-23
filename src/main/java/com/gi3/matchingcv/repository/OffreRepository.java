package com.gi3.matchingcv.repository;

import org.springframework.data.jpa.repository.EntityGraph;

import com.gi3.matchingcv.model.Offre;
import com.gi3.matchingcv.model.enums.StatutOffre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OffreRepository extends JpaRepository<Offre, Long> {

    /** Charge aussi recruteur et compétences exigées : évite une requête par offre lors du matching. */
    @EntityGraph(attributePaths = {"recruteur", "offreCompetences", "offreCompetences.competence"})
    List<Offre> findByStatut(StatutOffre statut);

    List<Offre> findByRecruteurIdOrderByDatePublicationDesc(Long recruteurId);

    List<Offre> findByStatutAndDateLimiteBefore(StatutOffre statut, LocalDateTime dateLimite);
}
