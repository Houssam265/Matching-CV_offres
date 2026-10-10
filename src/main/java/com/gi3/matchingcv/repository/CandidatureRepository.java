package com.gi3.matchingcv.repository;

import com.gi3.matchingcv.model.Candidature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CandidatureRepository extends JpaRepository<Candidature, Long> {

    boolean existsByOffreId(Long offreId);

    boolean existsByEtudiantIdAndOffreId(Long etudiantId, Long offreId);

    List<Candidature> findByOffreIdOrderByScoreDesc(Long offreId);
}
