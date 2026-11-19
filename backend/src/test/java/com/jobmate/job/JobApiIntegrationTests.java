package com.jobmate.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmate.job.application.command.CollectJobCommand;
import com.jobmate.job.application.service.JobCommandService;
import com.jobmate.job.domain.entity.DetailAvailability;
import com.jobmate.job.domain.entity.DetailSource;
import com.jobmate.job.domain.entity.Job;
import com.jobmate.job.domain.entity.JobData;
import com.jobmate.job.domain.entity.JobSource;
import com.jobmate.job.infrastructure.repository.JobRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class JobApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobCommandService jobCommandService;

    @Test
    void 공고를_등록하면_수동_출처와_세부_섹션을_조회한다() throws Exception {
        // given
        String body = """
                {"companyName":"테스트 기업","title":"Java 개발자","responsibilities":["API 개발"],
                 "requiredQualifications":["Java 경험"],"preferredQualifications":[],"skills":["Java"],
                 "workConditions":[],"originalUrl":"https://example.com/jobs/test"}
                """;

        // when
        long id = create(body);

        // then
        mockMvc.perform(get("/api/v1/jobs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("MANUAL"))
                .andExpect(jsonPath("$.data.responsibilities[0]").value("API 개발"))
                .andExpect(jsonPath("$.data.preferredQualifications").isEmpty())
                .andExpect(jsonPath("$.data.detailAvailability").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.jobVersion").value(1));
    }

    @Test
    void 상세를_입력하지_않으면_미확인과_명시적_없음을_구분한다() throws Exception {
        // given
        long id = create("{\"companyName\":\"테스트\",\"title\":\"개발\",\"preferredQualifications\":[]}");

        // when
        var response = mockMvc.perform(get("/api/v1/jobs/{id}", id)).andReturn().getResponse();

        // then
        var data = objectMapper.readTree(response.getContentAsString()).path("data");
        assertThat(data.path("responsibilities").isNull()).isTrue();
        assertThat(data.path("preferredQualifications").isArray()).isTrue();
        assertThat(data.path("preferredQualifications").size()).isZero();
        assertThat(data.path("detailAvailability").asText()).isEqualTo("PARTIAL");
    }

    @Test
    void 목록은_키워드와_지역과_직무와_출처를_함께_필터한다() throws Exception {
        // given
        String keyword = UUID.randomUUID().toString();
        create("{\"companyName\":\"필터 기업\",\"title\":\"" + keyword
                + " Java\",\"region\":\"서울\",\"jobRole\":\"BACKEND\"}");
        create("{\"companyName\":\"필터 기업\",\"title\":\"" + keyword
                + " Java\",\"region\":\"부산\",\"jobRole\":\"FRONTEND\"}");

        // when
        var request = get("/api/v1/jobs").param("keyword", keyword).param("region", "서울")
                .param("jobRole", "BACKEND").param("source", "MANUAL");

        // then
        mockMvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].region").value("서울"));
        mockMvc.perform(get("/api/v1/jobs").param("keyword", keyword).param("source", "SARAMIN"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 검색어의_퍼센트와_밑줄은_와일드카드로_확장하지_않는다() throws Exception {
        // given
        String key = UUID.randomUUID().toString();
        create("{\"companyName\":\"테스트\",\"title\":\"" + key + " 100%_개발\"}");
        create("{\"companyName\":\"테스트\",\"title\":\"" + key + " 100XX개발\"}");

        // when
        var request = get("/api/v1/jobs").param("keyword", key + " 100%_");

        // then
        mockMvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 회사명도_검색하고_일치하지_않으면_빈_페이지를_반환한다() throws Exception {
        // given
        String company = UUID.randomUUID().toString();
        create("{\"companyName\":\"" + company + " 기업\",\"title\":\"개발자\"}");

        // when
        var matching = get("/api/v1/jobs").param("keyword", company);
        var empty = get("/api/v1/jobs").param("keyword", UUID.randomUUID().toString());

        // then
        mockMvc.perform(matching).andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(empty).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 마감되거나_비활성인_공고는_기본목록에서_제외하고_상세는_허용한다() throws Exception {
        // given
        String key = UUID.randomUUID().toString();
        long expired = create("{\"companyName\":\"테스트\",\"title\":\"" + key
                + " 마감\",\"deadlineAt\":\"2020-01-01T00:00:00Z\"}");
        create("{\"companyName\":\"테스트\",\"title\":\"" + key + " 비활성\",\"active\":false}");
        create("{\"companyName\":\"테스트\",\"title\":\"" + key + " 모집중\"}");

        // when
        var request = get("/api/v1/jobs").param("keyword", key);

        // then
        mockMvc.perform(request).andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/v1/jobs").param("keyword", key).param("activeOnly", "false"))
                .andExpect(jsonPath("$.data.totalElements").value(3));
        mockMvc.perform(get("/api/v1/jobs/{id}", expired)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void 동일_게시시각은_ID_내림차순으로_안정적으로_페이징한다() throws Exception {
        // given
        String key = UUID.randomUUID().toString();
        long first = create("{\"companyName\":\"테스트\",\"title\":\"" + key
                + " A\",\"postedAt\":\"2026-01-01T00:00:00Z\"}");
        long second = create("{\"companyName\":\"테스트\",\"title\":\"" + key
                + " B\",\"postedAt\":\"2026-01-01T00:00:00Z\"}");
        create("{\"companyName\":\"테스트\",\"title\":\"" + key + " 날짜미확인\"}");

        // when
        var firstPage = get("/api/v1/jobs").param("keyword", key).param("size", "1");

        // then
        mockMvc.perform(firstPage).andExpect(jsonPath("$.data.items[0].jobId").value(second))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.totalPages").value(3));
        mockMvc.perform(get("/api/v1/jobs").param("keyword", key).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.data.items[0].jobId").value(first));
    }

    @Test
    void 마감임박순은_날짜가_없는_공고를_마지막에_정렬한다() throws Exception {
        // given
        String key = UUID.randomUUID().toString();
        create("{\"companyName\":\"테스트\",\"title\":\"" + key + " 없음\"}");
        long later = create("{\"companyName\":\"테스트\",\"title\":\"" + key
                + " 나중\",\"deadlineAt\":\"2099-12-01T00:00:00Z\"}");
        long sooner = create("{\"companyName\":\"테스트\",\"title\":\"" + key
                + " 먼저\",\"deadlineAt\":\"2099-01-01T00:00:00Z\"}");

        // when
        var request = get("/api/v1/jobs").param("keyword", key).param("sort", "DEADLINE");

        // then
        mockMvc.perform(request).andExpect(jsonPath("$.data.items[0].jobId").value(sooner))
                .andExpect(jsonPath("$.data.items[1].jobId").value(later))
                .andExpect(jsonPath("$.data.items[2].deadlineAt").isEmpty());
    }

    @Test
    void 부분수정은_생략필드를_보존하고_null로_선택값을_제거한다() throws Exception {
        // given
        long id = create("{\"companyName\":\"테스트\",\"title\":\"원래제목\",\"region\":\"서울\"}");

        // when
        mockMvc.perform(patch("/api/v1/dev/jobs/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"바뀐제목\",\"region\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.jobId").value(id));

        // then
        mockMvc.perform(get("/api/v1/jobs/{id}", id))
                .andExpect(jsonPath("$.data.title").value("바뀐제목"))
                .andExpect(jsonPath("$.data.companyName").value("테스트"))
                .andExpect(jsonPath("$.data.region").isEmpty())
                .andExpect(jsonPath("$.data.jobVersion").value(2));
    }

    @Test
    void 부분수정은_필수값_null과_출처변조와_잘못된_섹션을_거절한다() throws Exception {
        // given
        long id = create("{\"companyName\":\"테스트\",\"title\":\"개발\"}");
        List<String> invalidChanges = List.of("{\"title\":null}", "{\"active\":null}",
                "{\"source\":\"SARAMIN\"}", "{\"skills\":[\"\"]}", "{}",
                "{\"detailAvailability\":\"AVAILABLE\"}", "{\"active\":\"true\"}");

        // when
        for (String changes : invalidChanges) {
            // then
            mockMvc.perform(patch("/api/v1/dev/jobs/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .content(changes)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Test
    void 제한되거나_미제공된_상세본문은_영속화하지_않는다() throws Exception {
        // given
        String body = "{\"companyName\":\"테스트\",\"title\":\"개발\","
                + "\"detailAvailability\":\"RESTRICTED\",\"responsibilities\":[\"저장하면안됨\"]}";
        long count = jobRepository.count();

        // when
        mockMvc.perform(post("/api/v1/dev/jobs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RESTRICTED_JOB_CONTENT"));

        // then
        assertThat(jobRepository.count()).isEqualTo(count);
    }

    @Test
    void 잘못된_입력과_쿼리는_400을_반환한다() throws Exception {
        // given
        List<String> invalid = List.of("{}", "{\"companyName\":\" \",\"title\":\"개발\"}",
                "{\"companyName\":\"테스트\",\"title\":\"개발\",\"originalUrl\":\"javascript:alert(1)\"}",
                "{\"companyName\":\"테스트\",\"title\":\"개발\",\"source\":\"MANUAL\"}",
                "{\"companyName\":\"테스트\",\"title\":\"개발\",\"skills\":[null]}");

        // when
        for (String body : invalid) {
            // then
            mockMvc.perform(post("/api/v1/dev/jobs").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
        mockMvc.perform(get("/api/v1/jobs").param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/jobs").param("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/jobs").param("sort", "MATCH_SCORE")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/jobs").param("source", "UNKNOWN")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/jobs").param("page", "2147483647").param("size", "100"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 존재하지_않는_공고는_안전한_404를_반환한다() throws Exception {
        // given
        long absent = Long.MAX_VALUE;

        // when
        var request = get("/api/v1/jobs/{id}", absent);

        // then
        mockMvc.perform(request).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 동일한_외부공고를_재수집하면_중복저장없이_변경내용만_갱신한다() {
        // given
        String externalId = UUID.randomUUID().toString();
        Instant collected = Instant.parse("2026-01-01T00:00:00Z");
        JobData first = data("첫제목");
        Long id = jobCommandService.collect(new CollectJobCommand(JobSource.SARAMIN, externalId, first, collected));
        Instant initialUpdatedAt = jobRepository.findById(id).orElseThrow().getUpdatedAt();

        // when
        Long same = jobCommandService.collect(new CollectJobCommand(JobSource.SARAMIN, externalId, first,
                collected.plusSeconds(60)));

        // then
        assertThat(same).isEqualTo(id);
        Job unchanged = jobRepository.findById(id).orElseThrow();
        assertThat(unchanged.getJobVersion()).isEqualTo(1);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(initialUpdatedAt);
        assertThat(unchanged.getLastCollectedAt()).isEqualTo(collected.plusSeconds(60));
        jobCommandService.collect(new CollectJobCommand(JobSource.SARAMIN, externalId, data("변경제목"),
                collected.plusSeconds(120)));
        Job changed = jobRepository.findById(id).orElseThrow();
        assertThat(changed.getTitle()).isEqualTo("변경제목");
        assertThat(changed.getJobVersion()).isEqualTo(2);
        assertThat(jobRepository.findBySourceAndExternalJobId(JobSource.SARAMIN, externalId)).isPresent();
    }

    @Test
    void 상세_확보시각만_바뀌면_내용버전과_수정시각을_유지한다() {
        // given
        String externalId = UUID.randomUUID().toString();
        Instant collected = Instant.parse("2026-01-01T00:00:00Z");
        JobData first = partialData(collected);
        Long id = jobCommandService.collect(new CollectJobCommand(JobSource.SARAMIN, externalId, first, collected));
        jobRepository.flush();
        Instant updatedAt = jobRepository.findById(id).orElseThrow().getUpdatedAt();
        Instant refreshedAt = collected.plusSeconds(60);

        // when
        jobCommandService.collect(new CollectJobCommand(JobSource.SARAMIN, externalId,
                partialData(refreshedAt), refreshedAt));
        jobRepository.flush();

        // then
        Job job = jobRepository.findById(id).orElseThrow();
        assertThat(job.getJobVersion()).isEqualTo(1);
        assertThat(job.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(job.getDetailExtractedAt()).isEqualTo(refreshedAt);
        assertThat(job.getLastCollectedAt()).isEqualTo(refreshedAt);
    }

    @Test
    void 오래된_수집은_최신공고를_덮어쓰지_않는다() {
        // given
        String externalId = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Long id = jobCommandService.collect(new CollectJobCommand(JobSource.WANTED, externalId, data("최신"), now));

        // when
        jobCommandService.collect(new CollectJobCommand(JobSource.WANTED, externalId, data("과거"), now.minusSeconds(1)));

        // then
        Job job = jobRepository.findById(id).orElseThrow();
        assertThat(job.getTitle()).isEqualTo("최신");
        assertThat(job.getJobVersion()).isEqualTo(1);
    }

    @Test
    void DB_고유제약은_같은_출처와_외부ID의_중복을_차단한다() {
        // given
        String externalId = UUID.randomUUID().toString();
        jobRepository.saveAndFlush(Job.create(JobSource.SARAMIN, externalId, data("첫번째"), Instant.now()));

        // when
        Job duplicate = Job.create(JobSource.SARAMIN, externalId, data("두번째"), Instant.now());

        // then
        assertThatThrownBy(() -> jobRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 외부_식별자의_대소문자와_다른_출처는_서로_병합하지_않는다() {
        // given
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Long first = jobCommandService.collect(new CollectJobCommand(JobSource.SARAMIN, id + "a", data("첫번째"), now));

        // when
        Long otherCase = jobCommandService.collect(new CollectJobCommand(JobSource.SARAMIN, id + "A", data("두번째"), now));
        Long otherSource = jobCommandService.collect(new CollectJobCommand(JobSource.WANTED, id + "a", data("세번째"), now));

        // then
        assertThat(otherCase).isNotEqualTo(first);
        assertThat(otherSource).isNotEqualTo(first).isNotEqualTo(otherCase);
    }

    @Test
    void 부분상세를_미확인으로_되돌릴때_본문과_가용성을_함께_변경한다() throws Exception {
        // given
        long id = create("{\"companyName\":\"테스트\",\"title\":\"개발\",\"skills\":[\"Java\"]}");

        // when
        mockMvc.perform(patch("/api/v1/dev/jobs/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"skills\":null,\"detailAvailability\":\"UNAVAILABLE\"}"))
                .andExpect(status().isOk());

        // then
        mockMvc.perform(get("/api/v1/jobs/{id}", id))
                .andExpect(jsonPath("$.data.skills").isEmpty())
                .andExpect(jsonPath("$.data.detailAvailability").value("UNAVAILABLE"));
    }

    @Test
    void 외부출처_공고는_개발용_수정으로_변경할_수_없다() throws Exception {
        // given
        Long id = jobCommandService.collect(new CollectJobCommand(JobSource.SARAMIN,
                UUID.randomUUID().toString(), data("외부공고"), Instant.now()));

        // when
        var request = patch("/api/v1/dev/jobs/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"변경시도\"}");

        // then
        mockMvc.perform(request).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_MANUAL"));
    }

    private long create(String body) throws Exception {
        var result = mockMvc.perform(post("/api/v1/dev/jobs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("jobId").asLong();
    }

    private JobData partialData(Instant extractedAt) {
        return new JobData("테스트 기업", "Java 개발", "BACKEND", "서울", null, null, null, null,
                "https://example.com/jobs/test", null, null, true, List.of("API 개발"),
                null, null, List.of("Java"), null, DetailAvailability.PARTIAL,
                DetailSource.OFFICIAL_API, extractedAt);
    }

    private JobData data(String title) {
        return new JobData("테스트 기업", title, "BACKEND", "서울", null, null, null, null,
                "https://example.com/jobs/test", null, null, true, null, null, null, null, null,
                DetailAvailability.UNAVAILABLE, DetailSource.UNKNOWN, null);
    }
}
