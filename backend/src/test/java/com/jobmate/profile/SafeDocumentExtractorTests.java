package com.jobmate.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobmate.common.exception.CustomException;
import com.jobmate.profile.infrastructure.GithubReadmeClient;
import com.jobmate.profile.infrastructure.SafeDocumentExtractor;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class SafeDocumentExtractorTests {

    private final SafeDocumentExtractor extractor = new SafeDocumentExtractor();

    @Test
    void 실제_PDF와_DOCX에서_문자열을_로컬로_추출한다() throws Exception {
        // given
        byte[] pdf = DocumentFixtures.pdf(false);
        byte[] docx = DocumentFixtures.docx();

        // when
        String pdfText = extractor.extract(pdf, "pdf");
        String docxText = extractor.extract(docx, "docx");

        // then
        assertThat(pdfText).contains("Java team project");
        assertThat(docxText).contains("Java team project");
    }

    @Test
    void 확장자와_MIME이_다르면_거절한다() {
        // given
        String name = "resume.pdf";

        // when
        String mismatchedMime = "application/octet-stream";

        // then
        assertThatThrownBy(() -> extractor.extension(name, mismatchedMime)).isInstanceOf(CustomException.class);
    }

    @Test
    void 파일시그니처_위장과_손상과_암호화_PDF를_거절한다() throws Exception {
        // given
        byte[] disguised = "not a PDF document".getBytes(StandardCharsets.UTF_8);
        byte[] broken = "%PDF-1.7 corrupted content".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = DocumentFixtures.pdf(true);

        // when
        var candidates = java.util.List.of(disguised, broken, encrypted);

        // then
        for (byte[] bytes : candidates) {
            assertThatThrownBy(() -> extractor.extract(bytes, "pdf")).isInstanceOf(CustomException.class);
        }
    }

    @Test
    void DOCX_매크로와_외부관계와_과도한_압축을_거절한다() throws Exception {
        // given
        byte[] original = DocumentFixtures.docx();
        byte[] macros = DocumentFixtures.appendZip(original, "word/vbaProject.bin", new byte[]{1});
        byte[] external = DocumentFixtures.appendZip(original, "word/_rels/external.rels", DocumentFixtures.externalRelationship());
        byte[] inflated = DocumentFixtures.appendZip(original, "word/bomb.xml", new byte[6 * 1024 * 1024]);

        // when
        var candidates = java.util.List.of(macros, external, inflated);

        // then
        for (byte[] bytes : candidates) {
            assertThatThrownBy(() -> extractor.extract(bytes, "docx")).isInstanceOf(CustomException.class);
        }
    }

    @Test
    void 손상된_XML_파서는_원문이나_오류를_stderr에_자동출력하지_않는다() throws Exception {
        // given
        byte[] malformed = DocumentFixtures.appendZip(DocumentFixtures.docx(), "word/malformed.xml",
                "<document><private>synthetic@example.test</document>".getBytes(StandardCharsets.UTF_8));
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        // when
        try (PrintStream temporary = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setErr(temporary);
            assertThatThrownBy(() -> extractor.extract(malformed, "docx")).isInstanceOf(CustomException.class);
        } finally {
            System.setErr(original);
        }

        // then
        assertThat(captured.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void 허용크기를_초과하는_문서는_거절한다() {
        // given
        byte[] oversized = new byte[SafeDocumentExtractor.MAX_BYTES + 1];

        // when
        String extension = "pdf";

        // then
        assertThatThrownBy(() -> extractor.extract(oversized, extension)).isInstanceOf(CustomException.class);
    }

    @Test
    void GitHub_저장소_루트만_허용하고_SSRF_경로를_거절한다() {
        // given
        GithubReadmeClient github = new GithubReadmeClient();

        // when
        String canonical = github.canonicalUrl("https://github.com/example/repository/");

        // then
        assertThat(canonical).isEqualTo("https://github.com/example/repository");
        for (String invalid : java.util.List.of("http://127.0.0.1/private", "https://github.com@example.test/repo",
                "https://github.com/example/repo/tree/main", "https://github.com/example/..",
                "https://github.com/example/repo?token=secret")) {
            assertThatThrownBy(() -> github.canonicalUrl(invalid)).isInstanceOf(CustomException.class);
        }
    }
}
