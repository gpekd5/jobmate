package com.jobmate.job.infrastructure.repository.query;

import com.jobmate.job.application.command.JobSearchCondition;
import com.jobmate.job.domain.entity.Job;
import com.jobmate.job.domain.entity.JobSort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

// 검색 조건과 날짜 null-last 정렬을 DB 조회에서 일관되게 적용한다.
public class JobSearchRepositoryImpl implements JobSearchRepository {

    private final SimpleJpaRepository<Job, Long> repository;

    public JobSearchRepositoryImpl(EntityManager entityManager) {
        this.repository = new SimpleJpaRepository<>(Job.class, entityManager);
    }

    @Override
    public Page<Job> search(JobSearchCondition condition, Instant now) {
        Specification<Job> specification = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (condition.keyword() != null && !condition.keyword().isBlank()) {
                String keyword = "%" + escapeLike(condition.keyword().strip()) + "%";
                predicates.add(builder.or(builder.like(root.get("title"), keyword, '\\'),
                        builder.like(root.get("companyName"), keyword, '\\')));
            }
            if (condition.region() != null && !condition.region().isBlank()) {
                predicates.add(builder.equal(root.get("region"), condition.region().strip()));
            }
            if (condition.jobRole() != null && !condition.jobRole().isBlank()) {
                predicates.add(builder.equal(root.get("jobRole"), condition.jobRole().strip()));
            }
            if (condition.source() != null) {
                predicates.add(builder.equal(root.get("source"), condition.source()));
            }
            if (condition.activeOnly()) {
                predicates.add(builder.isTrue(root.get("active")));
                predicates.add(builder.or(builder.isNull(root.get("deadlineAt")),
                        builder.greaterThan(root.get("deadlineAt"), now)));
            }
            if (query != null && query.getResultType() != Long.class) {
                String sortField = condition.sort() == JobSort.DEADLINE ? "deadlineAt" : "postedAt";
                var nullLast = builder.selectCase().when(builder.isNull(root.get(sortField)), 1).otherwise(0);
                var primarySort = condition.sort() == JobSort.DEADLINE
                        ? builder.asc(root.get(sortField)) : builder.desc(root.get(sortField));
                query.orderBy(builder.asc(nullLast), primarySort, builder.desc(root.get("id")));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return repository.findAll(specification, PageRequest.of(condition.page(), condition.size()));
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
