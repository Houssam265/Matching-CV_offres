package com.gi3.matchingcv.controller;
import com.gi3.matchingcv.dto.OffreDto;
import com.gi3.matchingcv.model.SavedOffer;
import com.gi3.matchingcv.repository.*;
import com.gi3.matchingcv.security.Sessions;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@RestController @RequestMapping("/api/account/saved-offers") @Transactional @PreAuthorize("hasRole('ETUDIANT')")
public class SavedOfferController {
    private final SavedOfferRepository saved;private final OffreRepository offers;
    public SavedOfferController(SavedOfferRepository saved,OffreRepository offers){this.saved=saved;this.offers=offers;}
    @GetMapping public List<OffreDto> list(){return saved.findByUserIdOrderBySavedAtDesc(Sessions.current().id()).stream().map(s->offers.findById(s.getOfferId()).map(OffreDto::from)).flatMap(Optional::stream).toList();}
    @PutMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void save(@PathVariable Long id){if(!offers.existsById(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);Long userId=Sessions.current().id();if(!saved.existsByUserIdAndOfferId(userId,id)){var s=new SavedOffer();s.setUserId(userId);s.setOfferId(id);saved.save(s);}}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id){saved.deleteByUserIdAndOfferId(Sessions.current().id(),id);}
}
