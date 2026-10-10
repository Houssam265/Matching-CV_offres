package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.model.Competence;
import java.util.List;

public interface RecruteurService {
    AuthResponse inscrire(RecruteurInscriptionRequest request);
    RecruteurDto trouver(Long id);
    List<Competence> suggerer(Long id, String query);
    Competence proposer(Long id, String nom, String categorie);
}
