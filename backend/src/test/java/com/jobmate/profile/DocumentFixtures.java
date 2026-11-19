package com.jobmate.profile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

final class DocumentFixtures {

    private DocumentFixtures() {
    }

    static byte[] pdf(boolean encrypted) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(40, 700);
                content.showText("Java team project synthetic@example.test 010-1234-5678");
                content.endText();
            }
            if (encrypted) {
                document.protect(new StandardProtectionPolicy("owner-password", "user-password", new AccessPermission()));
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    static byte[] docx() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Java team project synthetic@example.test 010-1234-5678");
            document.write(output);
            return output.toByteArray();
        }
    }

    static byte[] appendZip(byte[] original, String name, byte[] content) throws Exception {
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output);
                var input = new ZipInputStream(new ByteArrayInputStream(original))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                zip.putNextEntry(new ZipEntry(entry.getName()));
                input.transferTo(zip);
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        }
    }

    static byte[] externalRelationship() {
        return """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="external" Type="http://example.test/link" TargetMode="External"
                    Target="http://127.0.0.1/private"/>
                </Relationships>
                """.getBytes(StandardCharsets.UTF_8);
    }
}
