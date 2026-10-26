package com.genlogs.app.service;

import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Marca GENLOGS para documentos PDF. Recurso: /static/logo-genlogs.png */
final class PdfBranding {
    private PdfBranding() {}
    static void logo(PDDocument doc, PDPageContentStream stream, float x, float y, float width, float height) throws IOException {
        try (InputStream input = PdfBranding.class.getResourceAsStream("/static/logo-genlogs.png")) {
            if (input == null) return;
            PDImageXObject image = PDImageXObject.createFromByteArray(doc, input.readAllBytes(), "logo-genlogs");
            stream.drawImage(image, x, y, width, height);
        }
    }
}

