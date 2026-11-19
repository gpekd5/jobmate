package com.jobmate.job.domain.policy;

import com.jobmate.common.exception.CustomException;
import com.jobmate.job.domain.entity.DetailAvailability;
import com.jobmate.job.domain.entity.JobData;
import com.jobmate.job.domain.exception.JobErrorCode;
import java.util.stream.Stream;

// 상세 확보 상태와 실제 보관한 섹션의 정합성을 보장한다.
public final class JobContentPolicy {

    private JobContentPolicy() {
    }

    public static void validate(JobData data) {
        long knownSections = Stream.of(data.responsibilities(), data.requiredQualifications(),
                        data.preferredQualifications(), data.skills(), data.workConditions())
                .filter(section -> section != null).count();
        if ((data.detailAvailability() == DetailAvailability.AVAILABLE && knownSections != 5)
                || (data.detailAvailability() == DetailAvailability.PARTIAL
                && (knownSections == 0 || knownSections == 5))) {
            throw new CustomException(JobErrorCode.INVALID_JOB_INPUT);
        }
        if (data.detailAvailability() == DetailAvailability.RESTRICTED
                || data.detailAvailability() == DetailAvailability.UNAVAILABLE) {
            boolean hasDetail = Stream.of(data.responsibilities(), data.requiredQualifications(),
                            data.preferredQualifications(), data.skills(), data.workConditions())
                    .anyMatch(section -> section != null);
            if (hasDetail || data.detailExtractedAt() != null) {
                throw new CustomException(JobErrorCode.RESTRICTED_JOB_CONTENT);
            }
        }
    }
}
