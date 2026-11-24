package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.dto.CvDto;
import com.gi3.matchingcv.security.Sessions;
import com.gi3.matchingcv.service.CvPdfService;
import com.gi3.matchingcv.service.CvService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CV : données (affichage) et téléchargement en PDF. */
@RestController
public class CvController {

    private final CvService cvService;
    private final CvPdfService pdfService;

    public CvController(CvService cvService, CvPdfService pdfService) {
        this.cvService = cvService;
        this.pdfService = pdfService;
    }

    /** CV d'un candidat, consulté par le recruteur propriétaire de l'offre (sans coordonnées). */
    @GetMapping("/api/candidatures/{id}/cv")
    public CvDto cvCandidat(@PathVariable Long id) {
        return cvService.pourRecruteur(id, Sessions.current().id());
    }

    /** Téléchargement du CV de l'étudiant connecté. */
    @GetMapping("/api/account/cv-pdf")
    public ResponseEntity<byte[]> monCvPdf(@RequestParam(defaultValue = "true") boolean photo) {
        return pdf(cvService.pourEtudiant(Sessions.current().id()), photo);
    }

    /** Téléchargement du CV d'un candidat par le recruteur propriétaire de l'offre. */
    @GetMapping("/api/candidatures/{id}/cv-pdf")
    public ResponseEntity<byte[]> cvCandidatPdf(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean photo) {
        return pdf(cvService.pourRecruteur(id, Sessions.current().id()), photo);
    }

    private ResponseEntity<byte[]> pdf(CvDto cv, boolean photo) {
        byte[] body = pdfService.generer(cv, photo);
        String fichier = CvPdfService.nomFichier(cv);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fichier).build().toString() /* nom déjà en ASCII */)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }
}
