package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.model.enums.TypeContrat;
import java.time.LocalDateTime;
import java.util.List;

public interface OffreService {
    OffreDto creer(OffreRequest request);
    OffreDto modifier(Long id, OffreRequest request);
    OffreDto cloturer(Long id, Long recruteurId);
    OffreDto rouvrir(Long id, Long recruteurId, LocalDateTime nouvelleDateLimite);
    default OffreDto rouvrir(Long id, Long recruteurId) { return rouvrir(id, recruteurId, null); }
    OffreDto dupliquer(Long id, Long recruteurId);
    int cloturerOffresEchues();
    void supprimer(Long id, Long recruteurId);
    OffreDto trouver(Long id, Long recruteurId);
    List<OffreDto> listerPubliques(String domaine, String localisation, TypeContrat typeContrat);
    List<OffreDto> listerParRecruteur(Long recruteurId);
}
