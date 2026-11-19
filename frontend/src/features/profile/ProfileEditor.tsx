import type { ProfileContent, ProfileEntry } from './api';
import { profileKeys } from './api';

const labels = {
  workExperiences: '업무 경력',
  skills: '기술',
  projects: '프로젝트',
  education: '학력',
  certifications: '자격증',
};
const contextLabels: Record<ProfileEntry['context'], string> = {
  WORK: '실무',
  TEAM_PROJECT: '팀 프로젝트',
  PERSONAL_PROJECT: '개인 프로젝트',
  TRAINING_PROJECT: '교육 프로젝트',
  LEARNING: '학습',
  UNKNOWN: '미확인',
};
const confidenceLabels: Record<ProfileEntry['confidence'], string> = {
  HIGH: '높음',
  MEDIUM: '보통',
  LOW: '낮음',
  UNKNOWN: '미확인',
};
const blankFact: ProfileEntry = {
  name: '',
  context: 'UNKNOWN',
  summary: '',
  evidence: '',
  confidence: 'UNKNOWN',
};

export function ProfileEditor({
  value,
  onChange,
  disabled,
}: {
  value: ProfileContent;
  onChange: (profile: ProfileContent) => void;
  disabled: boolean;
}) {
  return (
    <div className="profile-editor">
      {profileKeys.map((key) => (
        <fieldset key={key} disabled={disabled}>
          <legend>{labels[key]}</legend>
          <p className="form-help">
            확인된 사실과 출처만 남겨 주세요. 빈 항목은 추측해 채우지 않습니다.
          </p>
          {value[key].map((entry, index) => {
            function update(patch: Partial<ProfileEntry>) {
              onChange({
                ...value,
                [key]: value[key].map((fact, position) =>
                  position === index ? { ...fact, ...patch } : fact,
                ),
              });
            }
            return (
              <div className="fact-entry" key={index}>
                <label>
                  {labels[key]} {index + 1} 이름
                  <input
                    value={entry.name}
                    maxLength={200}
                    onChange={(event) => update({ name: event.target.value })}
                  />
                </label>
                <div className="form-grid">
                  <label>
                    경험 구분
                    <select
                      value={entry.context}
                      onChange={(event) =>
                        update({
                          context: event.target
                            .value as ProfileEntry['context'],
                        })
                      }
                    >
                      {Object.entries(contextLabels).map(([context, label]) => (
                        <option key={context} value={context}>
                          {label}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    근거 신뢰도
                    <select
                      value={entry.confidence}
                      onChange={(event) =>
                        update({
                          confidence: event.target
                            .value as ProfileEntry['confidence'],
                        })
                      }
                    >
                      {Object.entries(confidenceLabels).map(
                        ([confidence, label]) => (
                          <option key={confidence} value={confidence}>
                            {label}
                          </option>
                        ),
                      )}
                    </select>
                  </label>
                </div>
                <label>
                  내용 요약
                  <textarea
                    rows={3}
                    value={entry.summary}
                    maxLength={1000}
                    onChange={(event) =>
                      update({ summary: event.target.value })
                    }
                  />
                </label>
                <label>
                  출처·확인 근거
                  <textarea
                    rows={2}
                    value={entry.evidence}
                    maxLength={1000}
                    onChange={(event) =>
                      update({ evidence: event.target.value })
                    }
                  />
                </label>
                <button
                  type="button"
                  className="button-quiet"
                  onClick={() =>
                    onChange({
                      ...value,
                      [key]: value[key].filter(
                        (_, position) => index !== position,
                      ),
                    })
                  }
                >
                  {labels[key]} {index + 1} 항목 삭제
                </button>
              </div>
            );
          })}
          <button
            type="button"
            className="button-quiet"
            onClick={() =>
              onChange({ ...value, [key]: [...value[key], { ...blankFact }] })
            }
          >
            + {labels[key]} 추가
          </button>
        </fieldset>
      ))}
    </div>
  );
}
