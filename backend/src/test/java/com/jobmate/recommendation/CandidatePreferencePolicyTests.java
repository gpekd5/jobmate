package com.jobmate.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmate.job.domain.entity.DetailAvailability;
import com.jobmate.job.domain.entity.DetailSource;
import com.jobmate.job.domain.entity.JobData;
import com.jobmate.recommendation.domain.policy.CandidatePreferencePolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidatePreferencePolicyTests {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 희망조건이_없으면_지역직무급여_미확인을_제외조건으로_사용하지_않는다() throws Exception {
        // given
        var preferences = mapper.readTree("{}");

        // when
        boolean preferred = CandidatePreferencePolicy.preferred(data(null, null, null), preferences);

        // then
        assertThat(preferred).isTrue();
    }

    @Test
    void 고용형태와_연봉을_명확히_비교하고_다르거나_미확인이면_추가탐색으로_남긴다() throws Exception {
        // given
        var preferences = mapper.readTree("{\"employmentTypes\":[\"FULL_TIME\"],\"minSalary\":45000000}");

        // when / then
        assertThat(CandidatePreferencePolicy.preferred(data("FULL_TIME", "연봉 5,000만원", null), preferences)).isTrue();
        assertThat(CandidatePreferencePolicy.preferred(data("CONTRACT", "연봉 5,000만원", null), preferences)).isFalse();
        assertThat(CandidatePreferencePolicy.preferred(data("FULL_TIME", "연봉 4,000만원", null), preferences)).isFalse();
        assertThat(CandidatePreferencePolicy.preferred(data("FULL_TIME", "회사 내규에 따름", null), preferences)).isFalse();
        assertThat(CandidatePreferencePolicy.preferred(data("FULL_TIME", "월 500만원", null), preferences)).isFalse();
    }

    @Test
    void 파견과_잦은출장_회피는_명시적_조건만_확인하며_없는정보를_적합으로_추측하지_않는다() throws Exception {
        // given
        var preferences = mapper.readTree("{\"avoidDispatch\":true,\"avoidFrequentTravel\":true}");

        // when / then
        assertThat(CandidatePreferencePolicy.preferred(data(null, null, List.of("파견 없음", "출장 없음")), preferences)).isTrue();
        assertThat(CandidatePreferencePolicy.preferred(data(null, null, List.of("고객사 파견 근무", "잦은 출장")), preferences)).isFalse();
        assertThat(CandidatePreferencePolicy.preferred(data(null, null, null), preferences)).isFalse();
    }

    private JobData data(String employmentType, String salary, List<String> workConditions) {
        return new JobData("자체 예시", "자체 공고", null, null, employmentType, null, null, salary,
                null, null, null, true, null, null, null, null, workConditions,
                workConditions == null ? DetailAvailability.UNAVAILABLE : DetailAvailability.PARTIAL,
                DetailSource.USER_PROVIDED, null);
    }
}
