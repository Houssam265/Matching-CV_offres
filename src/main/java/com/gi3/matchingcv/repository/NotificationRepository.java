package com.gi3.matchingcv.repository;

import com.gi3.matchingcv.model.Notification;
import com.gi3.matchingcv.model.enums.TypeNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByDestinataireIdOrderByDateCreationDesc(Long destinataireId, Pageable pageable);

    Page<Notification> findByDestinataireIdAndLueFalseOrderByDateCreationDesc(Long destinataireId, Pageable pageable);

    List<Notification> findByDestinataireIdOrderByDateCreationDesc(Long destinataireId);

    List<Notification> findByDestinataireIdAndLueFalse(Long destinataireId);

    long countByDestinataireIdAndLueFalse(Long destinataireId);

    @Query("SELECT COUNT(n) > 0 FROM Notification n WHERE n.destinataire.id = :destinataireId AND n.type = :type AND n.offre.id = :offreId")
    boolean existsByDestinataireIdAndTypeAndOffreId(@Param("destinataireId") Long destinataireId, @Param("type") TypeNotification type, @Param("offreId") Long offreId);

    @Modifying
    @Query("UPDATE Notification n SET n.lue = true WHERE n.destinataire.id = :destinataireId AND n.lue = false")
    void toutMarquerLu(@Param("destinataireId") Long destinataireId);

    default List<Notification> findByEtudiantIdAndLueFalse(Long etudiantId) {
        return findByDestinataireIdAndLueFalse(etudiantId);
    }
}
