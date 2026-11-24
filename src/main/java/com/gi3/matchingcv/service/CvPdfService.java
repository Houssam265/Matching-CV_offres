package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.CvDto;
import com.gi3.matchingcv.model.AccountPhoto;
import com.gi3.matchingcv.repository.AccountPhotoRepository;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Génère le CV au format PDF (A4, une colonne, texte sélectionnable, liens cliquables).
 * Même contenu et même ordre que la page « Mon CV ». Police Inter embarquée (SIL OFL 1.1).
 */
@Service
public class CvPdfService {

    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("MMM yyyy", Locale.FRENCH);

    private final AccountPhotoRepository photos;

    public CvPdfService(AccountPhotoRepository photos) {
        this.photos = photos;
    }

    public byte[] generer(CvDto cv, boolean avecPhoto) {
        String html = html(cv, avecPhoto ? photoDataUri(cv.etudiantId()) : null, avecPhoto);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            police(builder, "/pdf-fonts/Inter_400Regular.ttf", 400);
            police(builder, "/pdf-fonts/Inter_600SemiBold.ttf", 600);
            police(builder, "/pdf-fonts/Inter_700Bold.ttf", 700);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Génération du CV impossible", e);
        }
    }

    /** Nom de fichier lisible et sûr : CV-Prenom-Nom.pdf */
    public static String nomFichier(CvDto cv) {
        String brut = ("CV " + (cv.prenom() == null ? "" : cv.prenom()) + " " + (cv.nom() == null ? "" : cv.nom())).strip();
        String sansAccents = java.text.Normalizer.normalize(brut, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String propre = sansAccents.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return (propre.isEmpty() ? "CV" : propre) + ".pdf";
    }

    private void police(PdfRendererBuilder builder, String chemin, int graisse) {
        builder.useFont(() -> {
            InputStream in = CvPdfService.class.getResourceAsStream(chemin);
            if (in == null) throw new IllegalStateException("Police introuvable : " + chemin);
            return in;
        }, "Inter", graisse, FontStyle.NORMAL, true);
    }

    /** Photo réelle (JPEG/PNG) en data URI ; sinon null (les initiales sont affichées). */
    /** Format de la photo du CV : portrait 4:5, coins arrondis — identique à l'affichage sur le site. */
    static final int PHOTO_W = 480, PHOTO_H = 600, PHOTO_ARC = 74;

    private String photoDataUri(Long userId) {
        if (userId == null) return null;
        AccountPhoto p = photos.findById(userId).orElse(null);
        if (p == null || p.getContent() == null) return null;
        byte[] jpeg = cadrerPortrait(p.getContent());
        return jpeg == null ? null : "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
    }

    /**
     * Recadre au centre en 4:5 (comme « background-size: cover »), redimensionne et arrondit les coins
     * sur fond blanc. Évite toute déformation, quel que soit le format d'origine. Null si illisible (ex. WebP).
     */
    static byte[] cadrerPortrait(byte[] source) {
        try {
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(source));
            if (src == null) return null;
            double cible = (double) PHOTO_W / PHOTO_H;
            int sw = src.getWidth(), sh = src.getHeight();
            int cw = sw, ch = sh;
            if ((double) sw / sh > cible) cw = (int) Math.round(sh * cible); else ch = (int) Math.round(sw / cible);
            int cx = (sw - cw) / 2, cy = (sh - ch) / 2;

            var masque = new java.awt.image.BufferedImage(PHOTO_W, PHOTO_H, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var g = masque.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.WHITE);
            g.fill(new java.awt.geom.RoundRectangle2D.Double(0, 0, PHOTO_W, PHOTO_H, PHOTO_ARC, PHOTO_ARC));
            g.setComposite(java.awt.AlphaComposite.SrcIn);
            g.drawImage(src, 0, 0, PHOTO_W, PHOTO_H, cx, cy, cx + cw, cy + ch, null);
            g.dispose();

            var rgb = new java.awt.image.BufferedImage(PHOTO_W, PHOTO_H, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var g2 = rgb.createGraphics();
            g2.setColor(java.awt.Color.WHITE);
            g2.fillRect(0, 0, PHOTO_W, PHOTO_H);
            g2.drawImage(masque, 0, 0, null);
            g2.dispose();

            var out = new ByteArrayOutputStream();
            var writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next();
            var params = writer.getDefaultWriteParam();
            params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.9f);
            try (var ios = javax.imageio.ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(rgb, null, null), params);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null; // image illisible : les initiales sont affichées
        }
    }

    /** Même palette que l'avatar aux initiales du site (/api/account/photo/{id}). */
    private static String couleurInitiales(Long userId) {
        String[] palette = {"#22744b", "#1d5f8a", "#7a4fa3", "#b0582b", "#2b7a78", "#8a6d1d"};
        return palette[(int) (Math.abs(userId == null ? 0 : userId) % palette.length)];
    }

    // ------------------------------------------------------------------ Gabarit XHTML

    private String html(CvDto cv, String photo, boolean avecPhoto) {
        String nom = (nz(cv.prenom()) + " " + nz(cv.nom())).strip();
        StringBuilder h = new StringBuilder(8192);
        h.append("<!DOCTYPE html><html lang=\"fr\"><head><meta charset=\"utf-8\"/>")
         .append("<title>").append(esc("CV — " + (nom.isEmpty() ? "Candidat" : nom))).append("</title>")
         .append("<meta name=\"author\" content=\"").append(esc(nom)).append("\"/>")
         .append("<meta name=\"subject\" content=\"Curriculum vitae\"/>")
         .append("<style>").append(CSS).append("</style></head><body>");

        // ---------- En-tête : photo · identité · coordonnées (une par ligne)
        h.append("<div class=\"head-wrap\"><table class=\"head\"><tr>");
        if (avecPhoto) {
            h.append("<td class=\"photo-cell\">");
            if (photo != null) h.append("<img class=\"photo\" src=\"").append(photo).append("\" alt=\"\"/>");
            else h.append("<div class=\"initials\" style=\"background-color: ").append(couleurInitiales(cv.etudiantId())).append(";\">")
                  .append(esc(initiales(cv))).append("</div>");
            h.append("</td>");
        }
        h.append("<td class=\"identity\"><h1>").append(esc(nom.isEmpty() ? "Candidat" : nom)).append("</h1>");
        if (nonVide(cv.titre())) h.append("<p class=\"title\">").append(esc(cv.titre())).append("</p>");
        if (nonVide(cv.disponibilite())) h.append("<p class=\"availability\">").append(esc(cv.disponibilite())).append("</p>");
        h.append("</td>");

        StringBuilder contact = new StringBuilder();
        if (nonVide(cv.email())) ligneContact(contact, "email", "Email", cv.email(), "mailto:" + cv.email());
        if (nonVide(cv.telephone())) ligneContact(contact, "phone", "Téléphone", cv.telephone(), "tel:" + cv.telephone().replaceAll("[^+0-9]", ""));
        if (nonVide(cv.ville())) ligneContact(contact, "ville", "Ville", cv.ville(), null);
        for (CvDto.Lien l : cv.liens() == null ? List.<CvDto.Lien>of() : cv.liens()) {
            String url = urlSure(l.url());
            if (url != null) ligneContact(contact, l.type(), l.libelle(), url.replaceFirst("^https?://(www\\.)?", "").replaceFirst("/$", ""), url);
        }
        if (contact.length() > 0) h.append("<td class=\"contact-cell\"><table class=\"contact\">").append(contact).append("</table></td>");
        h.append("</tr></table></div>");

        // ---------- Sections (même ordre que la page)
        if (nonVide(cv.resume())) section(h, "Profil", "<p class=\"summary\">" + multiligne(cv.resume()) + "</p>");

        CvDto.Formation f = cv.formation();
        if (f != null && (nonVide(f.filiere()) || nonVide(f.etablissement()))) {
            StringBuilder b = new StringBuilder("<div class=\"item\">");
            ligneTitre(b, nonVide(f.filiere()) ? f.filiere() : "Formation", f.periode());
            if (nonVide(f.etablissement())) b.append("<p class=\"org\">").append(esc(f.etablissement())).append("</p>");
            section(h, "Formation", b.append("</div>").toString());
        }

        if (cv.experiences() != null && !cv.experiences().isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (CvDto.Experience x : cv.experiences()) {
                b.append("<div class=\"item\">");
                ligneTitre(b, x.poste(), periode(x.dateDebut(), x.dateFin()));
                b.append("<p class=\"org\">").append(esc(x.entreprise())).append("</p>");
                competences(b, x.competences());
                b.append("</div>");
            }
            section(h, "Expérience professionnelle", b.toString());
        }

        if (cv.projets() != null && !cv.projets().isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (CvDto.Projet p : cv.projets()) {
                b.append("<div class=\"item\">");
                ligneTitre(b, p.titre(), null);
                if (nonVide(p.description())) b.append("<p class=\"text\">").append(multiligne(p.description())).append("</p>");
                competences(b, p.competences());
                b.append("</div>");
            }
            section(h, "Projets", b.toString());
        }

        if (cv.competences() != null && !cv.competences().isEmpty()) {
            StringBuilder b = new StringBuilder("<table class=\"skills\">");
            for (CvDto.GroupeCompetences g : cv.competences())
                b.append("<tr><td class=\"cat\">").append(esc(g.categorie())).append("</td><td>")
                 .append(esc(String.join(", ", g.competences()))).append("</td></tr>");
            section(h, "Compétences", b.append("</table>").toString());
        }

        if (cv.langues() != null && !cv.langues().isEmpty()) {
            StringBuilder b = new StringBuilder("<table class=\"two-col\">");
            List<String> l = cv.langues();
            for (int i = 0; i < l.size(); i += 2) {
                b.append("<tr><td><span class=\"dot\">•</span>").append(esc(l.get(i))).append("</td><td>");
                if (i + 1 < l.size()) b.append("<span class=\"dot\">•</span>").append(esc(l.get(i + 1)));
                b.append("</td></tr>");
            }
            section(h, "Langues", b.append("</table>").toString());
        }

        if (cv.interets() != null && !cv.interets().isEmpty())
            section(h, "Centres d’intérêt", "<p class=\"text\">" + esc(String.join(" · ", cv.interets())) + "</p>");

        return h.append("</body></html>").toString();
    }

    private static void section(StringBuilder h, String titre, String contenu) {
        h.append("<div class=\"section\"><h2>").append(esc(titre)).append("</h2>").append(contenu).append("</div>");
    }

    private static void ligneTitre(StringBuilder b, String titre, String date) {
        b.append("<table class=\"item-top\"><tr><td class=\"item-title\">").append(esc(titre)).append("</td>");
        if (nonVide(date)) b.append("<td class=\"date\">").append(esc(date)).append("</td>");
        b.append("</tr></table>");
    }

    private static void competences(StringBuilder b, List<String> noms) {
        if (noms != null && !noms.isEmpty())
            b.append("<p class=\"skills-line\">").append(esc(String.join(" · ", noms))).append("</p>");
    }

    /** Icônes de contact (mêmes tracés que sur le site, rendus en PNG haute résolution). */
    private static final java.util.Map<String, String> ICONES = new java.util.concurrent.ConcurrentHashMap<>();

    private static String icone(String type) {
        String cle = switch (type == null ? "" : type) {
            case "email", "phone", "ville", "linkedin", "github" -> type;
            default -> "web"; // portfolio, site personnel…
        };
        return ICONES.computeIfAbsent(cle, k -> {
            try (InputStream in = CvPdfService.class.getResourceAsStream("/pdf-icons/" + k + ".png")) {
                return in == null ? "" : "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
            } catch (IOException e) {
                return "";
            }
        });
    }

    private static void ligneContact(StringBuilder c, String type, String label, String texte, String href) {
        String ico = icone(type);
        c.append("<tr><td class=\"ico\">");
        if (!ico.isEmpty()) c.append("<img src=\"").append(ico).append("\" alt=\"").append(esc(label)).append("\"/>");
        c.append("</td><td class=\"value\">");
        if (href != null) c.append("<a href=\"").append(esc(href)).append("\">").append(esc(texte)).append("</a>");
        else c.append(esc(texte));
        c.append("</td></tr>");
    }

    private static String periode(LocalDate debut, LocalDate fin) {
        String d = debut == null ? "" : MOIS.format(debut);
        return d + " – " + (fin == null ? "aujourd’hui" : MOIS.format(fin));
    }

    private static String initiales(CvDto cv) {
        String i = (premiere(cv.prenom()) + premiere(cv.nom())).toUpperCase(Locale.ROOT);
        return i.isEmpty() ? "?" : i;
    }

    private static String premiere(String s) {
        if (s == null) return "";
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (Character.isLetter(cp)) return new String(Character.toChars(cp));
            i += Character.charCount(cp);
        }
        return "";
    }

    /** N'accepte que des liens http(s) bien formés. */
    private static String urlSure(String url) {
        if (url == null) return null;
        try {
            URI u = URI.create(url.strip());
            return ("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme())) && u.getHost() != null ? u.toString() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String multiligne(String s) {
        return esc(s.strip()).replace("\r\n", "\n").replace("\n", "<br/>");
    }

    private static boolean nonVide(String s) { return s != null && !s.isBlank(); }

    private static String nz(String s) { return s == null ? "" : s; }

    /** Échappement XML (le gabarit est du XHTML strict). Retire aussi les caractères de contrôle. */
    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> { if (c >= 0x20 || c == '\n' || c == '\r' || c == '\t') b.append(c); }
            }
        }
        return b.toString();
    }

    private static final String CSS = """
            @page { size: A4; margin: 15mm 16mm 14mm 16mm; }
            body { font-family: 'Inter'; font-size: 10pt; line-height: 1.42; color: #1b2a22; margin: 0; }
            a { color: #1b2a22; text-decoration: none; }
            table { border-collapse: collapse; width: 100%; }
            td { vertical-align: top; padding: 0; }
            .head-wrap { border-bottom: 2px solid #22744b; padding-bottom: 4.5mm; margin-bottom: 4mm; }
            .head td { vertical-align: middle; }
            .photo-cell { width: 31mm; }
            .photo { width: 26mm; height: 32.5mm; display: block; }
            .initials { width: 26mm; height: 32.5mm; border-radius: 2mm; background-color: #22744b; color: #ffffff;
                        font-size: 20pt; font-weight: 700; text-align: center; line-height: 32.5mm; }
            .identity { padding-right: 5mm; }
            h1 { font-size: 21pt; font-weight: 700; margin: 0; line-height: 1.1; }
            .title { font-size: 11.5pt; font-weight: 600; color: #22744b; margin: 1.4mm 0 0 0; }
            .availability { font-size: 9pt; font-weight: 600; color: #56665c; margin: 1.4mm 0 0 0; }
            .contact-cell { width: 66mm; border-left: 1px solid #d9e2dc; padding-left: 4.5mm; }
            .contact td { padding: 0.55mm 0; font-size: 8.8pt; vertical-align: top; }
            .contact .ico { width: 6mm; padding-top: 0.25mm; }
            .contact .ico img { width: 3.9mm; height: 3.9mm; display: block; }
            .contact .value { color: #1b2a22; word-wrap: break-word; }
            .section { margin-top: 4.2mm; }
            h2 { font-size: 9.6pt; font-weight: 700; color: #22744b; text-transform: uppercase; letter-spacing: 0.08em;
                 margin: 0 0 2.2mm 0; padding-bottom: 1mm; border-bottom: 1px solid #d9e2dc; }
            .summary { margin: 0; text-align: justify; }
            .item { margin-bottom: 2.8mm; page-break-inside: avoid; }
            .item-title { font-size: 10.4pt; font-weight: 600; }
            .date { text-align: right; white-space: nowrap; font-size: 9pt; color: #56665c; width: 52mm; }
            .org { margin: 0.3mm 0 0 0; color: #56665c; }
            .text { margin: 0.6mm 0 0 0; }
            .skills-line { margin: 0.6mm 0 0 0; font-size: 9pt; color: #56665c; }
            .skills td { padding: 0.6mm 0; }
            .skills .cat { width: 40mm; font-weight: 600; padding-right: 4mm; }
            .two-col td { width: 50%; padding: 0.5mm 4mm 0.5mm 0; }
            .dot { color: #22744b; font-weight: 700; padding-right: 2mm; }
            """;
}
