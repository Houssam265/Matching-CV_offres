package com.gi3.matchingcv.repository;

import com.gi3.matchingcv.model.Candidature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidatureRepository extends JpaRepository<Candidature, Long> {

    boolean existsByOffreId(Long offreId);

    boolean existsByEtudiantIdAndOffreId(Long etudiantId, Long offreId);

    Optional<Candidature> findByEtudiantIdAndOffreId(Long etudiantId, Long offreId);

    List<Candidature> findByEtudiantIdOrderByDateCandidatureDesc(Long etudiantId);

    List<Candidature> findByOffreIdOrderByScoreDesc(Long offreId);

    long countByEtudiantId(Long etudiantId);
}
