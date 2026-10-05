package com.gi3.matchingcv.repository;

import com.gi3.matchingcv.model.Projet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjetRepository extends JpaRepository<Projet, Long> {

    List<Projet> findByEtudiantId(Long etudiantId);
}
