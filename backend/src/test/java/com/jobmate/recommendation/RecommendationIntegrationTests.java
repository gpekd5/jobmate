package com.jobmate.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmate.common.ai.AiGateway;
import com.jobmate.common.ai.AiPurpose;
import com.jobmate.common.exception.CustomException;
import com.jobmate.job.application.command.CreateJobCommand;
import com.jobmate.job.application.service.JobCommandService;
import com.jobmate.job.domain.entity.DetailAvailability;
import com.jobmate.job.domain.entity.DetailSource;
import com.jobmate.job.domain.entity.JobData;
import com.jobmate.profile.infrastructure.repository.ProfileStateRepository;
import com.jobmate.recommendation.application.service.MatchAnalysisProcessor;
import com.jobmate.recommendation.application.service.MatchAnalysisService;
import com.jobmate.recommendation.application.service.RecommendationCommandService;
import com.jobmate.recommendation.application.service.RecommendationQueryService;
import com.jobmate.recommendation.domain.exception.RecommendationErrorCode;
import com.jobmate.recommendation.infrastructure.repository.MatchAnalysisRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "jobmate.recommendation.scheduling-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class RecommendationIntegrationTests {

    @Autowired private ObjectMapper mapper;
    @Autowired private ProfileStateRepository profiles;
    @Autowired private JobCommandService jobs;
    @Autowired private RecommendationCommandService commands;
    @Autowired private RecommendationQueryService query;
    @Autowired private MatchAnalysisProcessor processor;
    @Autowired private MatchAnalysisRepository analyses;
    @Autowired private MatchAnalysisService matching;
    @Autowired private MockMvc mvc;
    @MockitoBean private AiGateway gateway;

    @BeforeEach
    void prepare() throws Exception {
        var state = profiles.findById(1L).orElseThrow();
        state.confirm(mapper.readTree("""
                {"workExperiences":[{"name":"장비 C# 소프트웨어","context":"WORK","summary":"제조 장비 제어","evidence":"자체 경력 예시","confidence":"HIGH"}],
                 "skills":[{"name":"Java Spring Boot","context":"TEAM_PROJECT","summary":"팀 API 프로젝트","evidence":"자체 프로젝트 예시","confidence":"HIGH"}],
                 "projects":[],"education":[],"certifications":[]}
                """));
        state.changeConsent(true, List.of(AiPurpose.JOB_MATCHING));
        profiles.flush();
        when(gateway.modelVersion()).thenReturn("test-model");
        when(gateway.complete(any(), anyString(), anyString(), any())).thenReturn(output("PARTIAL", "GAP"));
    }

    @Test
    void 장비C샵_실무를_자바웹실무로_허위인정하지_않고_부분일치와_필수부족을_표시한다() throws Exception {
        // given
        long id = create(true);

        // when
        var request = commands.request(id);
        processor.process(requestId(request.requestId()));

        // then
        var result = query.match(id);
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.score()).isEqualTo(25);
        assertThat(result.requirements().get(0).evidence().get(0)).contains("TEAM_PROJECT");
        assertThat(result.gaps()).anyMatch(value -> value.contains("필수조건 미충족"));
        assertThat(result.scoreMeaning()).contains("합격 확률 아님");
        verify(gateway).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 동일캐시의_중복요청과_GET피드는_LLM을_반복호출하지_않는다() throws Exception {
        // given
        long id = create(true);
        var first = commands.request(id);

        // when
        var duplicate = commands.request(id);
        processor.process(requestId(first.requestId()));
        commands.request(id);
        mvc.perform(get("/api/v1/recommendations")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/jobs/{id}/match", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(25));

        // then
        assertThat(duplicate.requestId()).isEqualTo(first.requestId());
        verify(gateway).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void JD부족은_공고를_숨기지_않고_비용없이_분석불충분을_표시한다() throws Exception {
        // given
        long id = create(false);

        // when
        var request = commands.request(id);

        // then
        assertThat(request.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(query.match(id).score()).isNull();
        assertThat(query.feed(0, 100, "MATCH_SCORE", false, "ALL", "ALL").items())
                .anyMatch(item -> item.jobId() == id);
        assertThat(query.feed(0, 100, "MATCH_SCORE", false, "NOT_ANALYZED", "ALL").items())
                .anyMatch(item -> item.jobId() == id);
        assertThat(query.feed(0, 100, "MATCH_SCORE", false, "ANALYZED", "ALL").items())
                .noneMatch(item -> item.jobId() == id);
        verify(gateway, never()).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 확정했어도_검증할_프로필근거가_없으면_외부AI를_호출하지_않는다() throws Exception {
        // given
        profiles.findById(1L).orElseThrow().confirm(mapper.readTree("""
                {"workExperiences":[],"skills":[],"projects":[],"education":[],"certifications":[]}
                """));
        long id = create(true);

        // when
        var result = commands.request(id);

        // then
        assertThat(result.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(query.match(id).score()).isNull();
        verify(gateway, never()).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 없는근거ID는_분석실패로_보존하고_검증안된_점수를_반환하지_않는다() throws Exception {
        // given
        long id = create(true);
        when(gateway.complete(any(), anyString(), anyString(), any())).thenReturn(mapper.readTree("""
                {"requirements":[{"id":"r1","match":"MATCH","reason":"허위 근거","evidence":["p999"]},
                {"id":"r2","match":"MATCH","reason":"허위 근거","evidence":["p999"]}]}
                """));

        // when
        processor.process(requestId(commands.request(id).requestId()));

        // then
        assertThat(query.match(id).status()).isEqualTo("FAILED");
        assertThat(query.match(id).errorCode()).isEqualTo("INVALID_AI_MATCH");
        assertThat(query.match(id).score()).isNull();
    }

    @Test
    void 근거없는_MATCH는_UNKNOWN이며_숫자를_만들지_않는다() throws Exception {
        // given
        long id = create(true);
        var response = output("MATCH", "MATCH").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) response.path("requirements").get(0)).putArray("evidence");
        ((com.fasterxml.jackson.databind.node.ObjectNode) response.path("requirements").get(1)).putArray("evidence");
        when(gateway.complete(any(), anyString(), anyString(), any())).thenReturn(response);

        // when
        processor.process(requestId(commands.request(id).requestId()));

        // then
        assertThat(query.match(id).status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(query.match(id).requirements()).allMatch(item -> item.match().equals("UNKNOWN"));
        assertThat(query.match(id).score()).isNull();
    }

    @Test
    void 모델이_장비C샵실무를_Java상용실무_MATCH로_과장해도_서버가_완전일치를_차단한다() throws Exception {
        // given
        long id = create(true);
        when(gateway.complete(any(), anyString(), anyString(), any())).thenReturn(output("MATCH", "MATCH"));

        // when
        processor.process(requestId(commands.request(id).requestId()));

        // then
        var result = query.match(id);
        assertThat(result.requirements().get(1).match()).isEqualTo("PARTIAL");
        assertThat(result.score()).isEqualTo(75);
        assertThat(result.gaps()).anyMatch(value -> value.contains("필수조건 일부만 확인"));
    }

    @Test
    void 개인분석은_서로다른공고_하루3건을_초과해_선점하지_못한다() {
        // given
        for (int index = 0; index < 3; index++) commands.request(create(true));
        long fourth = create(true);

        // when / then
        assertThatThrownBy(() -> commands.request(fourth)).isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(RecommendationErrorCode.MATCH_DAILY_LIMIT));
        verify(gateway, never()).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 처리중_동의철회나_프로필변경은_결과저장을_차단한다() throws Exception {
        // given
        long id = create(true);
        when(gateway.complete(any(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            profiles.findById(1L).orElseThrow().changeConsent(false, List.of());
            profiles.flush();
            return output("MATCH", "MATCH");
        });
        var request = commands.request(id);

        // when
        processor.process(requestId(request.requestId()));

        // then
        assertThatThrownBy(() -> query.match(id)).isInstanceOf(CustomException.class);
        profiles.findById(1L).orElseThrow().changeConsent(true, List.of(AiPurpose.JOB_MATCHING));
        assertThat(query.match(id).status()).isEqualTo("NOT_ANALYZED");
        verify(gateway).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 미분석_전체목록과_권한없는_개인읽음필터를_구분한다() throws Exception {
        // given
        long id = create(true);

        // when / then
        mvc.perform(get("/api/v1/jobs/{id}/match", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_ANALYZED"));
        mvc.perform(get("/api/v1/recommendations").param("viewStatus", "READ"))
                .andExpect(status().isBadRequest());
        profiles.findById(1L).orElseThrow().removePersonalData(true);
        mvc.perform(get("/api/v1/recommendations")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROFILE_NOT_CONFIRMED"));
        verify(gateway, never()).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 실패후_명시적_재시도는_같은_행을_재사용하고_개별한도를_다시_차감한다() throws Exception {
        // given
        long id = create(true);
        when(gateway.complete(any(), anyString(), anyString(), any())).thenThrow(new IllegalStateException("safe test failure"));
        var first = commands.request(id);
        processor.process(requestId(first.requestId()));
        assertThat(query.match(id).status()).isEqualTo("FAILED");

        // when
        doReturn(output("PARTIAL", "GAP")).when(gateway).complete(any(), anyString(), anyString(), any());
        var retry = commands.request(id);
        var duplicate = commands.request(id);
        processor.process(requestId(retry.requestId()));

        // then
        assertThat(retry.requestId()).isEqualTo(first.requestId()).isEqualTo(duplicate.requestId());
        assertThat(query.match(id).score()).isEqualTo(25);
        commands.request(create(true));
        assertThatThrownBy(() -> commands.request(create(true))).isInstanceOf(CustomException.class);
    }

    @Test
    void 자동분석은_개별분석과_별도로_하루20건까지만_원자적으로_예약한다() {
        // given
        var context = query.context();
        for (int index = 0; index < 20; index++) {
            long id = create(true);
            assertThat(analyses.reserve(query.key(query.job(id), context), true, false)).isNotNull();
        }
        long next = create(true);

        // when
        var exceeded = analyses.reserve(query.key(query.job(next), context), true, false);

        // then
        assertThat(exceeded).isNull();
        assertThat(commands.request(next).status()).isEqualTo("PENDING");
        verify(gateway, never()).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 명시적_희망지역을_서버근거로_전송하고_근무조건_가중치에_반영한다() throws Exception {
        // given
        profiles.findById(1L).orElseThrow().changePreferences(mapper.readTree("{\"regions\":[\"서울\"]}"));
        long id = createWithWorkCondition();
        when(gateway.complete(any(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            String input = invocation.getArgument(2);
            assertThat(input).contains("preferences.regions", "서울", "f1");
            return workOutput("f1");
        });

        // when
        processor.process(requestId(commands.request(id).requestId()));

        // then
        var result = query.match(id);
        assertThat(result.requirements().get(2).match()).isEqualTo("MATCH");
        assertThat(result.requirements().get(2).evidence()).anyMatch(value -> value.contains("preferences.regions"));
        assertThat(result.evaluatedCategories()).contains("WORK_CONDITION");
        assertThat(result.score()).isEqualTo(56);
    }

    @Test
    void 희망조건이_없으면_경력근거를_써도_근무조건_MATCH는_UNKNOWN으로_바꾼다() throws Exception {
        // given
        long id = createWithWorkCondition();
        when(gateway.complete(any(), anyString(), anyString(), any())).thenReturn(workOutput("p1"));

        // when
        processor.process(requestId(commands.request(id).requestId()));

        // then
        var result = query.match(id);
        assertThat(result.requirements().get(2).match()).isEqualTo("UNKNOWN");
        assertThat(result.requirements().get(2).evidence()).isEmpty();
        assertThat(result.evaluatedCategories()).doesNotContain("WORK_CONDITION");
        assertThat(result.score()).isEqualTo(50);
    }

    @Test
    void 희망조건을_필수지원자격의_실제경험_근거로_허위사용하지_못한다() throws Exception {
        // given
        profiles.findById(1L).orElseThrow().changePreferences(mapper.readTree("{\"desiredRoles\":[\"Java 실무\"]}"));
        long id = create(true);
        var result = output("MATCH", "MATCH").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) result.path("requirements").get(1)).putArray("evidence").add("f1");
        when(gateway.complete(any(), anyString(), anyString(), any())).thenReturn(result);

        // when
        processor.process(requestId(commands.request(id).requestId()));

        // then
        assertThat(query.match(id).requirements().get(1).match()).isEqualTo("UNKNOWN");
        assertThat(query.match(id).score()).isNull();
    }

    @Test
    void 최신200개가_이미처리됐어도_더오래된_미분석공고를_자동후보로_선택한다() {
        // given
        long oldest = create(true);
        var context = query.context();
        for (int index = 0; index < 200; index++) {
            long newer = create(false);
            analyses.reserve(query.key(query.job(newer), context), true, true);
        }

        // when
        commands.refresh();

        // then
        assertThat(query.match(oldest).status()).isEqualTo("PENDING");
        verify(gateway, never()).complete(any(), anyString(), anyString(), any());
    }

    @Test
    void 한국시간에서도_한시간후_마감공고는_유효모집_추천목록에서_누락하지_않는다() {
        // given
        var data = new JobData("자체 예시", "한시간 후 마감", "BACKEND", "서울", null, null, null, null,
                null, Instant.now(), Instant.now().plusSeconds(3600), true,
                null, null, null, null, null, DetailAvailability.UNAVAILABLE, DetailSource.USER_PROVIDED, null);
        long id = jobs.create(new CreateJobCommand(data));

        // when
        var feed = query.feed(0, 100, "MATCH_SCORE", true, "ALL", "ALL");

        // then
        assertThat(feed.items()).anyMatch(item -> item.jobId() == id && item.active());
    }

    private long requestId(String value) {
        return Long.parseLong(value.substring("match-".length()));
    }

    private long create(boolean detailed) {
        var data = new JobData("자체 예시 기업", "자체 Java 개발 공고", "BACKEND", "서울", null, null, null, null,
                "https://example.com/jobs/sample", Instant.now(), null, true,
                detailed ? List.of("Spring API 구현 경험") : null,
                detailed ? List.of("상용 Java 웹 운영 실무") : null,
                null, null, null, detailed ? DetailAvailability.PARTIAL : DetailAvailability.UNAVAILABLE,
                DetailSource.USER_PROVIDED, detailed ? Instant.now() : null);
        return jobs.create(new CreateJobCommand(data));
    }

    private long createWithWorkCondition() {
        var data = new JobData("자체 예시 기업", "자체 조건 공고", "BACKEND", "서울", "FULL_TIME", null, null,
                "연봉 5000만원", "https://example.com/jobs/sample", Instant.now(), null, true,
                List.of("Spring API 구현 경험"), List.of("상용 Java 웹 운영 실무"), null, null,
                List.of("서울 사무실 근무"), DetailAvailability.PARTIAL, DetailSource.USER_PROVIDED, Instant.now());
        return jobs.create(new CreateJobCommand(data));
    }

    private JsonNode workOutput(String reference) throws Exception {
        return mapper.readTree("""
                {"requirements":[{"id":"r1","match":"MATCH","reason":"명시된 API 프로젝트","evidence":["p2"]},
                {"id":"r2","match":"GAP","reason":"Java 상용 실무는 확인되지 않음","evidence":["p1"]},
                {"id":"r3","match":"MATCH","reason":"명시적 서울 근무 희망","evidence":["%s"]}]}
                """.formatted(reference));
    }

    private JsonNode output(String responsibility, String must) throws Exception {
        return mapper.readTree("""
                {"requirements":[{"id":"r1","match":"%s","reason":"팀 프로젝트는 실무와 구분합니다.","evidence":["p2"]},
                 {"id":"r2","match":"%s","reason":"장비 C# 실무는 Java 웹 상용 운영이 아닙니다.","evidence":["p1"]}]}
                """.formatted(responsibility, must));
    }
}
