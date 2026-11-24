package com.gi3.matchingcv.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class CvPdfServiceTest {

    private static byte[] png(int w, int h) throws Exception {
        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("La photo du PDF est toujours un portrait 4:5 recadré, jamais déformé")
    void photoRecadreeEnPortrait() throws Exception {
        for (byte[] source : new byte[][]{png(900, 300), png(300, 900), png(500, 500)}) {
            byte[] jpeg = CvPdfService.cadrerPortrait(source);
            assertThat(jpeg).isNotNull();
            BufferedImage result = ImageIO.read(new ByteArrayInputStream(jpeg));
            assertThat(result.getWidth()).isEqualTo(CvPdfService.PHOTO_W);
            assertThat(result.getHeight()).isEqualTo(CvPdfService.PHOTO_H);
            // Coins arrondis sur fond blanc, comme sur le site
            assertThat(result.getRGB(0, 0) & 0xFFFFFF).isGreaterThan(0xF0F0F0);
        }
    }

    @Test
    @DisplayName("Image illisible : pas de photo (les initiales sont affichées)")
    void imageIllisible() {
        assertThat(CvPdfService.cadrerPortrait("pas une image".getBytes())).isNull();
    }

    @Test
    @DisplayName("Nom de fichier lisible et sans accents")
    void nomFichier() {
        var cv = new com.gi3.matchingcv.dto.CvDto(1L, "Hélène", "N'Diaye", null, null, null, null, null, null,
                java.util.List.of(), null, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
        assertThat(CvPdfService.nomFichier(cv)).isEqualTo("CV-Helene-N-Diaye.pdf");
    }
}
