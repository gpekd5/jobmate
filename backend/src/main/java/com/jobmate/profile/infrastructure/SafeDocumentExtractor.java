package com.jobmate.profile.infrastructure;

import com.jobmate.common.exception.CustomException;
import com.jobmate.profile.domain.exception.ProfileErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

// 로컬 파싱만 수행하며 외부 리소스, 실행 콘텐츠, 과도한 압축/분량을 허용하지 않는다.
@Component
public class SafeDocumentExtractor {

    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_TEXT = 100_000;
    private static final int MAX_INFLATED = 25 * 1024 * 1024;
    private static final Set<String> UNSAFE_PDF_KEYS = Set.of("JS", "JavaScript", "Launch", "EmbeddedFiles",
            "EmbeddedFile", "RichMedia", "XFA", "OpenAction", "AA", "AcroForm");

    public String extension(String fileName, String mime) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf") && "application/pdf".equals(mime)) {
            return "pdf";
        }
        if (lower.endsWith(".docx")
                && "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mime)) {
            return "docx";
        }
        throw new CustomException(ProfileErrorCode.INVALID_DOCUMENT);
    }

    public String extract(byte[] bytes, String extension) {
        if (bytes.length > MAX_BYTES) {
            throw new CustomException(ProfileErrorCode.DOCUMENT_TOO_LARGE);
        }
        if (bytes.length < 8) {
            throw new CustomException(ProfileErrorCode.INVALID_DOCUMENT);
        }
        try {
            String text = "pdf".equals(extension) ? pdf(bytes) : docx(bytes);
            if (text.isBlank() || text.length() > MAX_TEXT) {
                throw new IOException("Unsupported text content");
            }
            return text;
        } catch (Exception exception) {
            // 파서 예외에는 원문 조각이 포함될 수 있으므로 메시지나 원인을 외부에 전달하지 않는다.
            throw new CustomException(ProfileErrorCode.INVALID_DOCUMENT);
        }
    }

    private String pdf(byte[] bytes) throws IOException {
        if (!new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new IOException("Invalid PDF signature");
        }
        PDFParser parser = new PDFParser(new RandomAccessReadBuffer(bytes));
        try (PDDocument document = parser.parse(false)) {
            if (document.isEncrypted() || document.getNumberOfPages() > 100) {
                throw new IOException("Unsupported PDF");
            }
            int[] total = {0};
            Set<COSBase> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            scanPdf(document.getDocumentCatalog().getCOSObject(), seen, total, 0);
            PDFTextStripper stripper = new PDFTextStripper() {
                private int count;

                @Override
                protected void writeString(String text) throws IOException {
                    count += text.length();
                    if (count > MAX_TEXT) {
                        throw new IOException("Text limit");
                    }
                    super.writeString(text);
                }
            };
            return stripper.getText(document);
        }
    }

    private void scanPdf(COSBase node, Set<COSBase> seen, int[] total, int depth) throws IOException {
        if (node == null || !seen.add(node)) {
            return;
        }
        if (depth > 50 || seen.size() > 50_000) {
            throw new IOException("PDF complexity limit");
        }
        if (node instanceof COSObject reference) {
            scanPdf(reference.getObject(), seen, total, depth + 1);
        } else if (node instanceof COSDictionary dictionary) {
            for (COSName key : dictionary.keySet()) {
                if (UNSAFE_PDF_KEYS.contains(key.getName())) {
                    throw new IOException("Active PDF content");
                }
                scanPdf(dictionary.getItem(key), seen, total, depth + 1);
            }
            if (dictionary instanceof COSStream stream) {
                try (InputStream input = stream.createInputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total[0] += count;
                        if (total[0] > MAX_INFLATED) {
                            throw new IOException("PDF inflation limit");
                        }
                    }
                }
            }
        } else if (node instanceof COSArray array) {
            for (COSBase value : array) {
                scanPdf(value, seen, total, depth + 1);
            }
        }
    }

    private String docx(byte[] bytes) throws Exception {
        if (bytes[0] != 'P' || bytes[1] != 'K' || bytes[2] != 3 || bytes[3] != 4) {
            throw new IOException("Invalid DOCX signature");
        }
        boolean mainDocument = false;
        boolean contentTypes = false;
        int entries = 0;
        int total = 0;
        Set<String> names = new java.util.HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                if (++entries > 2000 || !names.add(name) || name.contains("..") || name.startsWith("/")
                        || name.contains("\\") || lower.contains("vba") || lower.contains("embeddings/")
                        || lower.endsWith(".bin")) {
                    throw new IOException("Unsupported ZIP content");
                }
                byte[] data = zip.readNBytes(5 * 1024 * 1024 + 1);
                total += data.length;
                if (data.length > 5 * 1024 * 1024 || total > MAX_INFLATED) {
                    throw new IOException("ZIP inflation limit");
                }
                if (name.endsWith(".xml") || name.endsWith(".rels")) {
                    var builder = xmlFactory().newDocumentBuilder();
                    builder.setErrorHandler(new DefaultHandler() {
                        @Override
                        public void error(SAXParseException exception) throws SAXException {
                            throw exception;
                        }

                        @Override
                        public void fatalError(SAXParseException exception) throws SAXException {
                            throw exception;
                        }
                    });
                    var document = builder.parse(new ByteArrayInputStream(data));
                    var elements = document.getElementsByTagName("*");
                    for (int index = 0; index < elements.getLength(); index++) {
                        Element element = (Element) elements.item(index);
                        if ("External".equalsIgnoreCase(element.getAttribute("TargetMode"))
                                || "altChunk".equals(element.getLocalName())) {
                            throw new IOException("External or embedded DOCX content");
                        }
                    }
                }
                if ("[Content_Types].xml".equals(name)) {
                    String types = new String(data, StandardCharsets.UTF_8);
                    if (types.contains("macroEnabled") || !types.contains("wordprocessingml.document.main+xml")) {
                        throw new IOException("Invalid DOCX content types");
                    }
                    contentTypes = true;
                }
                mainDocument |= "word/document.xml".equals(name);
            }
        }
        if (!mainDocument || !contentTypes) {
            throw new IOException("Missing DOCX parts");
        }
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(5 * 1024 * 1024);
        ZipSecureFile.setMaxTextSize(MAX_TEXT);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private DocumentBuilderFactory xmlFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }
}
