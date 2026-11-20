package com.jobmate.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmate.common.ai.AiGateway;
import com.jobmate.common.ai.AiPurpose;
import com.jobmate.common.ai.AiErrorCode;
import com.jobmate.common.exception.CustomException;
import com.jobmate.profile.application.command.AnalyzeProfileCommand;
import com.jobmate.profile.application.service.ProfileQueryService;
import com.jobmate.profile.application.service.ProfileAnalysisWorker;
import com.jobmate.profile.application.service.ProfileService;
import com.jobmate.profile.domain.entity.DocumentType;
import com.jobmate.profile.infrastructure.PrivateDocumentStorage;
import com.jobmate.profile.infrastructure.GithubReadmeClient;
import com.jobmate.profile.infrastructure.repository.ProfileDocumentRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "jobmate.recommendation.scheduling-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ProfileIntegrationTests {

    private static final Path DIRECTORY;

    static {
        try {
            DIRECTORY = Files.createTempDirectory("jobmate-profile-test-");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry properties) {
        properties.add("jobmate.documents.path", DIRECTORY::toString);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ProfileService service;

    @Autowired
    private ProfileQueryService query;

    @Autowired
    private ProfileAnalysisWorker worker;

    @Autowired
    private ProfileDocumentRepository documents;

    @Autowired
    private PrivateDocumentStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @MockitoBean
    private AiGateway gateway;

    @MockitoBean(name = "aiTaskExecutor")
    private TaskExecutor executor;

    @MockitoSpyBean
    private GithubReadmeClient github;

    private final List<Long> createdLinks = new ArrayList<>();
    private final AtomicReference<Runnable> task = new AtomicReference<>();
    private final List<Long> createdDocuments = new ArrayList<>();

    @BeforeEach
    void 준비한다() throws Exception {
        // 개발 DB의 파일·프로필에 영향을 주지 않도록 전용 테스트 DB에서만 실행한다.
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        doAnswer(invocation -> {
            task.set(invocation.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));
        when(gateway.complete(any(), anyString(), anyString(), any())).thenReturn(draft());
        when(gateway.modelVersion()).thenReturn("test-model");
        doReturn("Java team project README").when(github).read(anyString());
    }

    @AfterEach
    void 생성한_문서만_정리한다() {
        for (Long id : createdDocuments) {
            if (documents.findByIdAndOwnerId(id, 1).isPresent()) {
                service.deleteDocument(id);
            }
        }
        for (Long id : createdLinks) {
            if (service.links().stream().anyMatch(link -> link.linkId().equals(id))) {
                service.deleteLink(id);
            }
        }
        service.consent("OPENAI", List.of(), false);
    }

    @Test
    void 업로드는_AI를_호출하지_않고_비공개_원본과_미분석_상태만_저장한다() throws Exception {
        // given
        var file = pdf();

        // when
        long id = upload(file);

        // then
        mvc.perform(get("/api/v1/me/documents")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].analysisStatus").value("NOT_ANALYZED"));
        var document = documents.findById(id).orElseThrow();
        assertThat(storage.exists(document.getStorageKey())).isTrue();
        assertThat(document.getStorageKey()).doesNotContain("resume");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(DIRECTORY))).isEqualTo("rwx------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(DIRECTORY.resolve(document.getStorageKey()))))
                .isEqualTo("rw-------");
        verify(gateway, never()).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 별도_목적_동의가_없으면_분석을_시작하지_않는다() throws Exception {
        // given
        long id = upload(pdf());
        service.consent("OPENAI", List.of(AiPurpose.JOB_MATCHING), true);

        // when
        var request = analyzeRequest(id);

        // then
        mvc.perform(request).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AI_CONSENT_REQUIRED"));
        verify(gateway, never()).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 명시_분석은_마스킹한_입력으로_초안을_저장하고_자동확정하지_않는다() throws Exception {
        // given
        long id = upload(pdf());
        consent();
        AtomicReference<String> sent = new AtomicReference<>();
        when(gateway.complete(any(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            sent.set(invocation.getArgument(2));
            return draft();
        });

        // when
        long analysis = analyze(id);
        task.get().run();

        // then
        mvc.perform(get("/api/v1/me/profile/analyses/{id}", analysis)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.skills[0].context").value("TEAM_PROJECT"));
        assertThat(sent.get()).doesNotContain("synthetic@example.test", "010-1234-5678", "resume.pdf", DIRECTORY.toString());
        assertThat(query.current().confirmed()).isFalse();
        assertThat(documents.findById(id).orElseThrow().getAnalysisStatus().name()).isEqualTo("ANALYZED");
    }

    @Test
    void 실제_DOCX도_선택_포트폴리오_없이_분석한다() throws Exception {
        // given
        long id = upload(new MockMultipartFile("file", "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", DocumentFixtures.docx()));
        consent();

        // when
        long analysis = analyze(id);
        task.get().run();

        // then
        assertThat(service.analysis(analysis).status().name()).isEqualTo("SUCCEEDED");
    }

    @Test
    void 분석실패는_원본을_보존하고_버튼_재요청으로_재시도한다() throws Exception {
        // given
        long id = upload(pdf());
        consent();
        when(gateway.complete(any(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("synthetic sensitive transport detail"))
                .thenReturn(draft());

        // when
        long failed = analyze(id);
        task.get().run();
        long retried = analyze(id);
        task.get().run();

        // then
        assertThat(service.analysis(failed).status().name()).isEqualTo("FAILED");
        assertThat(service.analysis(failed).errorCode()).isEqualTo("AI_ANALYSIS_FAILED");
        assertThat(service.analysis(retried).status().name()).isEqualTo("SUCCEEDED");
        assertThat(storage.exists(documents.findById(id).orElseThrow().getStorageKey())).isTrue();
    }

    @Test
    void 분석중_삭제하면_원본과_초안을_제거하고_늦은_AI완료를_보관하지_않는다() throws Exception {
        // given
        long id = upload(pdf());
        String key = documents.findById(id).orElseThrow().getStorageKey();
        consent();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(gateway.complete(any(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return draft();
        });
        long analysis = analyze(id);

        // when
        var completion = CompletableFuture.runAsync(task.get());
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        service.deleteDocument(id);
        release.countDown();
        completion.get(5, TimeUnit.SECONDS);

        // then
        assertThat(storage.exists(key)).isFalse();
        assertThat(query.current().confirmed()).isFalse();
        mvc.perform(get("/api/v1/me/profile/analyses/{id}", analysis)).andExpect(status().isNotFound());
    }

    @Test
    void 분석중_동의를_철회하면_늦은_결과와_개인정보를_저장하지_않는다() throws Exception {
        // given
        long id = upload(pdf());
        consent();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(gateway.complete(any(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return draft();
        });
        long analysis = analyze(id);

        // when
        var completion = CompletableFuture.runAsync(task.get());
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        service.consent("OPENAI", List.of(), false);
        release.countDown();
        completion.get(5, TimeUnit.SECONDS);

        // then
        assertThat(service.analysis(analysis).status().name()).isEqualTo("FAILED");
        assertThat(service.analysis(analysis).result()).isNull();
        assertThat(service.analysis(analysis).errorCode()).isEqualTo("AI_CONSENT_CHANGED");
    }

    @Test
    void 검토확정후_새_이력서를_올려도_기존_프로필과_버전을_보존한다() throws Exception {
        // given
        long oldId = upload(pdf());
        String oldKey = documents.findById(oldId).orElseThrow().getStorageKey();
        confirm();
        var before = query.current();

        // when
        long replacement = upload(pdf());

        // then
        assertThat(replacement).isNotEqualTo(oldId);
        assertThat(documents.findById(oldId)).isEmpty();
        assertThat(storage.exists(oldKey)).isFalse();
        assertThat(query.current().confirmed()).isTrue();
        assertThat(query.current().profileVersion()).isEqualTo(before.profileVersion());
        assertThat(query.current().profile()).isEqualTo(before.profile());
    }

    @Test
    void 이력서교체_트랜잭션이_실패하면_기존_원본과_메타데이터를_보존한다() throws Exception {
        // given
        long original = upload(pdf());
        String key = documents.findById(original).orElseThrow().getStorageKey();
        var replacement = pdf();

        // when
        var transaction = new TransactionTemplate(transactions);

        // then
        assertThatThrownBy(() -> transaction.execute(status -> {
            service.upload(DocumentType.RESUME, replacement);
            throw new IllegalStateException("rollback fixture");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(documents.findById(original)).isPresent();
        assertThat(storage.exists(key)).isTrue();
        try (var files = Files.list(DIRECTORY)) {
            assertThat(files.count()).isEqualTo(1);
        }
    }

    @Test
    void 존재하지않는_소유문서와_잘못된_프로필은_거절한다() throws Exception {
        // given
        long id = upload(pdf());
        consent();

        // when
        var missing = analyzeRequest(Long.MAX_VALUE);

        // then
        mvc.perform(missing).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/me/documents/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/me/profile").contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmed\":true,\"ownerId\":2}"))
                .andExpect(status().isBadRequest());
        assertThat(documents.findById(id)).isPresent();
    }

    @Test
    void 선택_선호조건은_빈값을_허용하고_입력길이를_검증한다() throws Exception {
        // given
        String empty = "{}";

        // when
        var request = put("/api/v1/me/preferences").contentType(MediaType.APPLICATION_JSON).content(empty);

        // then
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.desiredRoles").isEmpty())
                .andExpect(jsonPath("$.data.avoidDispatch").value(false));
        mvc.perform(put("/api/v1/me/preferences").contentType(MediaType.APPLICATION_JSON)
                .content("{\"minSalary\":-1}")).andExpect(status().isBadRequest());
    }

    @Test
    void 링크등록은_외부조회하지_않고_명시_분석에서만_선택한_README를_읽는다() throws Exception {
        // given
        long document = upload(pdf());
        consent();
        var added = mvc.perform(post("/api/v1/me/portfolio-links").contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://github.com/example/repository\",\"description\":\"선택 프로젝트\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long linkId = mapper.readTree(added.getContentAsString()).path("data").path("linkId").asLong();
        createdLinks.add(linkId);
        verify(github, never()).read(anyString());

        // when
        var response = mvc.perform(post("/api/v1/me/profile/analyses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentIds\":[" + document + "],\"portfolioLinkIds\":[" + linkId
                        + "],\"analysisMode\":\"EXTERNAL_LLM\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse();
        long analysis = mapper.readTree(response.getContentAsString()).path("data").path("analysisId").asLong();
        task.get().run();

        // then
        verify(github).read("https://github.com/example/repository");
        assertThat(service.analysis(analysis).status().name()).isEqualTo("SUCCEEDED");
        assertThat(service.analysis(analysis).portfolioLinkIds()).containsExactly(linkId);
    }

    @Test
    void 분석이_진행중이면_같은_버튼_요청을_중복_실행하지_않는다() throws Exception {
        // given
        long id = upload(pdf());
        consent();
        analyze(id);

        // when
        var duplicate = analyzeRequest(id);

        // then
        mvc.perform(duplicate).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ANALYSIS_ALREADY_RUNNING"));
        verify(gateway, never()).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 입력에_없는_근거를_생성한_AI초안은_보관하지_않는다() throws Exception {
        // given
        long id = upload(pdf());
        consent();
        JsonNode invalid = draft();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.path("skills").get(0))
                .put("evidence", "입력에 존재하지 않는 허위 경력");
        when(gateway.complete(any(), anyString(), anyString(), any())).thenReturn(invalid);

        // when
        long analysis = analyze(id);
        task.get().run();

        // then
        assertThat(service.analysis(analysis).status().name()).isEqualTo("FAILED");
        assertThat(service.analysis(analysis).errorCode()).isEqualTo("AI_OUTPUT_INVALID");
        assertThat(service.analysis(analysis).result()).isNull();
    }

    @Test
    void 명시적_문서삭제는_확정프로필과_버전을_무효화하고_초안을_제거한다() throws Exception {
        // given
        long id = upload(pdf());
        consent();
        long analysis = analyze(id);
        task.get().run();
        confirm();
        long confirmedVersion = query.current().profileVersion();

        // when
        mvc.perform(delete("/api/v1/me/documents/{id}", id)).andExpect(status().isNoContent());

        // then
        var snapshot = query.current();
        assertThat(snapshot.confirmed()).isFalse();
        assertThat(snapshot.profileVersion()).isGreaterThan(confirmedVersion);
        assertThat(snapshot.profile().path("skills").isArray()).isTrue();
        assertThat(snapshot.profile().path("skills").size()).isZero();
        mvc.perform(get("/api/v1/me/profile/analyses/{id}", analysis)).andExpect(status().isNotFound());
    }

    @Test
    void 프로필_확정은_현재_버전을_필수로_받고_오래된_수정을_거절한다() throws Exception {
        // given
        upload(pdf());
        var missingVersion = (com.fasterxml.jackson.databind.node.ObjectNode) draft();
        missingVersion.put("confirmed", true);
        var stale = missingVersion.deepCopy();
        stale.put("profileVersion", query.current().profileVersion());
        confirm();

        // when
        var missing = put("/api/v1/me/profile").contentType(MediaType.APPLICATION_JSON).content(missingVersion.toString());
        var older = put("/api/v1/me/profile").contentType(MediaType.APPLICATION_JSON).content(stale.toString());

        // then
        mvc.perform(missing).andExpect(status().isBadRequest());
        mvc.perform(older).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROFILE_CONFLICT"));
    }

    @Test
    void 다른_소유자의_문서ID를_분석하거나_삭제할_수_없다() throws Exception {
        // given
        long id = upload(pdf());
        consent();
        jdbc.update("UPDATE profile_document SET owner_id = 2 WHERE id = ?", id);

        // when
        try {
            var analyze = analyzeRequest(id);
            var remove = delete("/api/v1/me/documents/{id}", id);

            // then
            mvc.perform(analyze).andExpect(status().isNotFound());
            mvc.perform(remove).andExpect(status().isNotFound());
        } finally {
            jdbc.update("UPDATE profile_document SET owner_id = 1 WHERE id = ?", id);
        }
    }

    @Test
    void 프로세스가_중단한_분석은_자동_AI호출_없이_명시적으로_재시도할_수_있다() throws Exception {
        // given
        long id = upload(pdf());
        consent();
        long interrupted = analyze(id);

        // when
        worker.recoverInterrupted();

        // then
        assertThat(service.analysis(interrupted).errorCode()).isEqualTo("ANALYSIS_INTERRUPTED");
        assertThat(service.analysis(interrupted).status().name()).isEqualTo("FAILED");
        verify(gateway, never()).complete(any(), anyString(), anyString(), any());
        long retried = analyze(id);
        task.get().run();
        assertThat(service.analysis(retried).status().name()).isEqualTo("SUCCEEDED");
    }

    @Test
    void 같은_문서와_정책의_성공결과는_예산확인이나_AI재호출_없이_재사용한다() throws Exception {
        // given
        long id = upload(pdf());
        consent();
        long first = analyze(id);
        task.get().run();
        doThrow(new CustomException(AiErrorCode.AI_BUDGET_EXHAUSTED))
                .when(gateway).assertAvailable(AiPurpose.PROFILE_EXTRACTION);

        // when
        long reused = analyze(id);

        // then
        assertThat(reused).isEqualTo(first);
        assertThat(service.analysis(reused).status().name()).isEqualTo("SUCCEEDED");
        verify(gateway, times(1)).assertAvailable(AiPurpose.PROFILE_EXTRACTION);
        verify(gateway, times(1)).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 선택_문서의_순서만_바뀌어도_같은_성공결과를_재사용한다() throws Exception {
        // given
        long resume = upload(pdf());
        var portfolio = service.upload(DocumentType.PORTFOLIO, pdf());
        createdDocuments.add(portfolio.documentId());
        consent();
        var first = service.analyze(new AnalyzeProfileCommand(List.of(resume, portfolio.documentId()), List.of(), "EXTERNAL_LLM"));
        task.get().run();

        // when
        var reordered = service.analyze(new AnalyzeProfileCommand(List.of(portfolio.documentId(), resume), List.of(), "EXTERNAL_LLM"));

        // then
        assertThat(reordered.analysisId()).isEqualTo(first.analysisId());
        verify(gateway, times(1)).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 모델버전이_바뀌면_문서가_같아도_새로_분석한다() throws Exception {
        // given
        long id = upload(pdf());
        consent();
        long first = analyze(id);
        task.get().run();
        when(gateway.modelVersion()).thenReturn("test-model-v2");

        // when
        long changed = analyze(id);
        task.get().run();

        // then
        assertThat(changed).isNotEqualTo(first);
        assertThat(service.analysis(changed).modelVersion()).isEqualTo("test-model-v2");
        verify(gateway, times(2)).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 정책버전이_다르거나_동의epoch가_바뀐_성공결과는_재사용하지_않는다() throws Exception {
        // given
        long id = upload(pdf());
        consent();
        long first = analyze(id);
        task.get().run();
        jdbc.update("UPDATE profile_analysis SET prompt_version = 'old-policy' WHERE id = ?", first);

        // when
        long changedPolicy = analyze(id);
        task.get().run();
        service.consent("OPENAI", List.of(), false);
        mvc.perform(analyzeRequest(id)).andExpect(status().isForbidden());
        consent();
        long changedConsent = analyze(id);
        task.get().run();

        // then
        assertThat(changedPolicy).isNotEqualTo(first);
        assertThat(changedConsent).isNotEqualTo(changedPolicy);
        verify(gateway, times(3)).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 원격_README를_포함한_요청은_내용버전을_보장할_수_없어_재사용하지_않는다() throws Exception {
        // given
        long id = upload(pdf());
        consent();
        var link = service.addLink("https://github.com/example/repository", "테스트 프로젝트");
        createdLinks.add(link.linkId());
        var command = new AnalyzeProfileCommand(List.of(id), List.of(link.linkId()), "EXTERNAL_LLM");
        var first = service.analyze(command);
        task.get().run();

        // when
        var repeated = service.analyze(command);
        task.get().run();

        // then
        assertThat(repeated.analysisId()).isNotEqualTo(first.analysisId());
        verify(github, times(2)).read(link.url());
        verify(gateway, times(2)).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void AI출력도_마스킹한_뒤_초안에_저장한다() throws Exception {
        // given
        long id = upload(pdf());
        consent();
        var output = draft();
        ((com.fasterxml.jackson.databind.node.ObjectNode) output.path("skills").get(0))
                .put("summary", "Java project contact synthetic@example.test");
        when(gateway.complete(any(), anyString(), anyString(), any())).thenReturn(output);

        // when
        long analysis = analyze(id);
        task.get().run();

        // then
        assertThat(service.analysis(analysis).status().name()).isEqualTo("SUCCEEDED");
        assertThat(service.analysis(analysis).result().toString()).doesNotContain("synthetic@example.test");
    }

    private long upload(MockMultipartFile file) throws Exception {
        var response = mvc.perform(multipart("/api/v1/me/documents").file(file).param("type", "RESUME"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        long id = mapper.readTree(response.getContentAsString()).path("data").path("documentId").asLong();
        createdDocuments.add(id);
        return id;
    }

    private long analyze(long documentId) throws Exception {
        var response = mvc.perform(analyzeRequest(documentId)).andExpect(status().isAccepted())
                .andReturn().getResponse();
        return mapper.readTree(response.getContentAsString()).path("data").path("analysisId").asLong();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder analyzeRequest(long documentId) {
        return post("/api/v1/me/profile/analyses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentIds\":[" + documentId + "],\"portfolioLinkIds\":[],\"analysisMode\":\"EXTERNAL_LLM\"}");
    }

    private void consent() {
        service.consent("OPENAI", List.of(AiPurpose.PROFILE_EXTRACTION, AiPurpose.JOB_MATCHING), true);
    }

    private void confirm() throws Exception {
        var content = draft().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("confirmed", true);
        ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("profileVersion", query.current().profileVersion());
        mvc.perform(put("/api/v1/me/profile").contentType(MediaType.APPLICATION_JSON).content(content.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.confirmed").value(true));
    }

    private MockMultipartFile pdf() throws Exception {
        return new MockMultipartFile("file", "resume.pdf", "application/pdf", DocumentFixtures.pdf(false));
    }

    private JsonNode draft() throws Exception {
        return mapper.readTree("""
                {"workExperiences":[],"skills":[{"name":"Java","context":"TEAM_PROJECT",
                  "summary":"Java project","evidence":"Java team project","confidence":"HIGH"}],
                  "projects":[],"education":[],"certifications":[]}
                """);
    }
}
