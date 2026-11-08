package com.gi3.matchingcv.repository;
import com.gi3.matchingcv.model.SavedOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SavedOfferRepository extends JpaRepository<SavedOffer,Long>{
    List<SavedOffer> findByUserIdOrderBySavedAtDesc(Long userId);
    boolean existsByUserIdAndOfferId(Long userId,Long offerId);
    void deleteByUserIdAndOfferId(Long userId,Long offerId);
}
