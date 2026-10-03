package com.gi3.matchingcv.repository;

import com.gi3.matchingcv.model.ProfilCompetence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfilCompetenceRepository extends JpaRepository<ProfilCompetence, Long> {
    List<ProfilCompetence> findByCompetenceId(Long competenceId);
    void deleteByCompetenceId(Long competenceId);
}
