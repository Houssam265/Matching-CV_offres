package com.gi3.matchingcv.repository;

import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompetenceRepository extends JpaRepository<Competence, Long> {

    Optional<Competence> findByNomIgnoreCase(String nom);

    List<Competence> findByStatut(StatutCompetence statut);
}
