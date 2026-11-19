package com.jobmate.recommendation.domain.policy;

import com.jobmate.recommendation.application.model.MatchAnalysis.Requirement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// UNKNOWN은 미확인이므로 0점으로 포함하지 않고 검증된 항목과 범주만 정규화한다.
public final class MatchScoringPolicy {

    public static final String VERSION = "v1";
    private static final Map<String, Integer> WEIGHTS = Map.of(
            "RESPONSIBILITY", 35, "MUST", 35, "PREFERRED", 20, "WORK_CONDITION", 10);

    private MatchScoringPolicy() {
    }

    public static Score calculate(List<Requirement> requirements) {
        var unique = new HashSet<String>();
        var known = requirements.stream().filter(item -> !item.match().equals("UNKNOWN"))
                .filter(item -> unique.add(item.text().strip().toLowerCase(Locale.ROOT)))
                .toList();
        if (known.size() < 2 || known.stream().noneMatch(item ->
                item.category().equals("MUST") || item.category().equals("RESPONSIBILITY"))) {
            return new Score(null, known.size(), List.of());
        }
        Map<String, List<Requirement>> groups = new LinkedHashMap<>();
        for (String category : List.of("RESPONSIBILITY", "MUST", "PREFERRED", "WORK_CONDITION")) {
            var items = known.stream().filter(item -> item.category().equals(category)).toList();
            if (!items.isEmpty()) groups.put(category, items);
        }
        double weighted = 0;
        int denominator = 0;
        for (var group : groups.entrySet()) {
            int weight = WEIGHTS.get(group.getKey());
            double average = group.getValue().stream().mapToDouble(item -> switch (item.match()) {
                case "MATCH" -> 1;
                case "PARTIAL" -> 0.5;
                default -> 0;
            }).average().orElse(0);
            weighted += weight * average;
            denominator += weight;
        }
        return new Score((int) Math.round(weighted * 100 / denominator), known.size(), List.copyOf(groups.keySet()));
    }

    public record Score(Integer value, int evidenceCount, List<String> evaluatedCategories) {
    }
}
