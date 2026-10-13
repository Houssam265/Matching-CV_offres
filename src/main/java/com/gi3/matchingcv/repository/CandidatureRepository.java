package com.gi3.matchingcv.repository;

import com.gi3.matchingcv.model.Candidature;
import com.gi3.matchingcv.model.enums.StatutCandidature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("SELECT COUNT(c) FROM Candidature c WHERE c.offre.id = :offreId")
    long countByOffreId(@Param("offreId") Long offreId);

    @Query("SELECT COUNT(c) FROM Candidature c WHERE c.offre.id = :offreId AND c.statut = :statut")
    long countByOffreIdAndStatut(@Param("offreId") Long offreId, @Param("statut") StatutCandidature statut);

    @Query("SELECT c FROM Candidature c WHERE c.offre.recruteur.id = :recruteurId ORDER BY c.dateCandidature DESC")
    List<Candidature> findByRecruteurIdOrderByDateDesc(@Param("recruteurId") Long recruteurId);

    @Query("SELECT COUNT(c) FROM Candidature c WHERE c.offre.recruteur.id = :recruteurId")
    long countByRecruteurId(@Param("recruteurId") Long recruteurId);

    @Query("SELECT COUNT(c) FROM Candidature c WHERE c.offre.recruteur.id = :recruteurId AND c.statut = :statut")
    long countByRecruteurIdAndStatut(@Param("recruteurId") Long recruteurId, @Param("statut") StatutCandidature statut);
}
