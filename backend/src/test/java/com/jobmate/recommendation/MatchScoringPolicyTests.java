package com.jobmate.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobmate.recommendation.application.model.MatchAnalysis.Requirement;
import com.jobmate.recommendation.domain.policy.MatchScoringPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchScoringPolicyTests {

    @Test
    void 알려진_범주만_가중치를_정규화하고_필수조건_불일치를_감점한다() {
        // given
        var requirements = List.of(item("RESPONSIBILITY", "API 설계 경험", "MATCH"),
                item("MUST", "Java 실무 경력", "GAP"), item("PREFERRED", "알수없는 우대조건", "UNKNOWN"));

        // when
        var score = MatchScoringPolicy.calculate(requirements);

        // then
        assertThat(score.value()).isEqualTo(50);
        assertThat(score.evaluatedCategories()).containsExactly("RESPONSIBILITY", "MUST");
        assertThat(score.evidenceCount()).isEqualTo(2);
    }

    @Test
    void 업무35_필수35_우대20_근무10의_검증된_가중치를_계산한다() {
        // given
        var requirements = List.of(item("RESPONSIBILITY", "Spring API 개발", "MATCH"),
                item("MUST", "Java 실무 경력", "PARTIAL"), item("PREFERRED", "제조업 이해 경험", "MATCH"),
                item("WORK_CONDITION", "서울 사무실 출근", "GAP"));

        // when
        var score = MatchScoringPolicy.calculate(requirements);

        // then
        assertThat(score.value()).isEqualTo(73);
    }

    @Test
    void 미확인_요건은_0점이_아니며_확인된_근거가_둘보다_적으면_숫자를_만들지_않는다() {
        // given
        var requirements = List.of(item("MUST", "상용 웹 운영 경험", "UNKNOWN"),
                item("RESPONSIBILITY", "Java API 구현 경험", "MATCH"));

        // when
        var score = MatchScoringPolicy.calculate(requirements);

        // then
        assertThat(score.value()).isNull();
        assertThat(score.evidenceCount()).isEqualTo(1);
    }

    @Test
    void 중복_요건이나_우대와_근무조건만으로는_숫자를_산출하지_않는다() {
        // given
        var duplicate = List.of(item("MUST", "Java API 개발", "MATCH"), item("RESPONSIBILITY", "Java API 개발", "MATCH"));
        var noCore = List.of(item("PREFERRED", "제조업 이해 경험", "MATCH"), item("WORK_CONDITION", "서울 근무 가능", "MATCH"));

        // when
        var first = MatchScoringPolicy.calculate(duplicate);
        var second = MatchScoringPolicy.calculate(noCore);

        // then
        assertThat(first.value()).isNull();
        assertThat(second.value()).isNull();
    }

    private Requirement item(String category, String text, String match) {
        return new Requirement(text, category, text, match, "자체 테스트 근거", List.of("자체 예시 프로필"));
    }
}
