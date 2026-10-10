package com.gi3.matchingcv.repository;

import com.gi3.matchingcv.model.Offre;
import com.gi3.matchingcv.model.enums.StatutOffre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OffreRepository extends JpaRepository<Offre, Long> {

    List<Offre> findByStatut(StatutOffre statut);

    List<Offre> findByRecruteurIdOrderByDatePublicationDesc(Long recruteurId);
}
