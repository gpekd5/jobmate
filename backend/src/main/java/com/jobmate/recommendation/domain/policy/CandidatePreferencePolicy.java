package com.jobmate.recommendation.domain.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobmate.job.domain.entity.JobData;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

// 명확히 비교할 수 있는 희망조건만 우선 후보로 분류하고 미확인은 추가 탐색에 보존한다.
public final class CandidatePreferencePolicy {

    private static final Pattern ANNUAL_SALARY = Pattern.compile("^(?:연봉|연|annual)\\s*([0-9,]+)(만원|원)(?:\\s*(?:이상|부터))?$", Pattern.CASE_INSENSITIVE);

    private CandidatePreferencePolicy() {
    }

    public static boolean preferred(JobData job, JsonNode preferences) {
        if (preferences == null || !preferences.isObject()) return true;
        if (!matches(preferences.path("desiredRoles"), job.jobRole())
                || !matches(preferences.path("regions"), job.region())
                || !matches(preferences.path("employmentTypes"), job.employmentType())) return false;
        if (preferences.path("minSalary").isNumber() && preferences.path("minSalary").asLong() > 0) {
            Long salary = annualSalary(job.salaryText());
            if (salary == null || salary < preferences.path("minSalary").asLong()) return false;
        }
        String conditions = job.workConditions() == null ? "" : String.join(" ", job.workConditions()).toLowerCase(Locale.ROOT);
        if (preferences.path("avoidDispatch").asBoolean()
                && !containsAny(conditions, List.of("파견 없음", "파견없음", "비파견", "no dispatch", "non-dispatch"))) return false;
        return !preferences.path("avoidFrequentTravel").asBoolean()
                || containsAny(conditions, List.of("출장 없음", "출장없음", "잦은 출장 없음", "잦은출장없음", "no frequent travel", "no travel"));
    }

    private static boolean matches(JsonNode values, String value) {
        if (!values.isArray() || values.isEmpty()) return true;
        if (value == null) return false;
        for (JsonNode item : values) if (item.asText().equalsIgnoreCase(value.strip())) return true;
        return false;
    }

    private static boolean containsAny(String value, List<String> phrases) {
        return phrases.stream().anyMatch(value::contains);
    }

    private static Long annualSalary(String text) {
        if (text == null) return null;
        var matcher = ANNUAL_SALARY.matcher(text.strip());
        if (!matcher.matches()) return null;
        try {
            var amount = new BigDecimal(matcher.group(1).replace(",", ""));
            return amount.multiply(new BigDecimal(matcher.group(2).equals("만원") ? "10000" : "1")).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }
}
