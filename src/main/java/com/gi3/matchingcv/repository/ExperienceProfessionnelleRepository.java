package com.gi3.matchingcv.repository;

import com.gi3.matchingcv.model.ExperienceProfessionnelle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperienceProfessionnelleRepository extends JpaRepository<ExperienceProfessionnelle, Long> {

    List<ExperienceProfessionnelle> findByEtudiantId(Long etudiantId);
}
