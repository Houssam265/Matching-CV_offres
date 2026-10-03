package com.gi3.matchingcv.repository;

import com.gi3.matchingcv.model.OffreCompetence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OffreCompetenceRepository extends JpaRepository<OffreCompetence, Long> {
    List<OffreCompetence> findByOffreId(Long offreId);
    List<OffreCompetence> findByCompetenceId(Long competenceId);
    void deleteByCompetenceId(Long competenceId);
}
