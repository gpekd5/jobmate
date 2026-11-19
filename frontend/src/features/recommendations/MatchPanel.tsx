import { useEffect, useRef, useState } from 'react';
import { canUsePersonalApi, personalError } from '../../shared/api/localApi';
import { analyzeMatch, getMatch, matchLabels, waitForMatch } from './api';
import type { MatchDetail, RequirementMatch } from './api';
import { formatDate } from '../jobs/api';

const categoryLabels: Record<RequirementMatch['category'], string> = {
  MUST: '필수 자격',
  PREFERRED: '우대 사항',
  RESPONSIBILITY: '담당업무',
  WORK_CONDITION: '근무조건',
};
const comparisonLabels: Record<RequirementMatch['match'], string> = {
  MATCH: '일치',
  PARTIAL: '부분 일치',
  GAP: '부족',
  UNKNOWN: '미확인',
};

export function MatchPanel({ jobId }: { jobId: number }) {
  const [match, setMatch] = useState<MatchDetail | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const operationRef = useRef<AbortController | null>(null);
  const local = canUsePersonalApi();

  useEffect(() => {
    if (!local) return;
    const controller = new AbortController();
    getMatch(jobId, controller.signal)
      .then(async (match) => {
        if (!controller.signal.aborted) setMatch(match);
        if (match.status === 'PENDING') {
          const result = await waitForMatch(jobId, controller.signal);
          if (!controller.signal.aborted) setMatch(result);
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted)
          setError(
            error instanceof Error && error.message === 'POLLING_EXPIRED'
              ? '분석이 진행 중입니다. 자동 조회를 멈췄습니다. 결과 다시 확인을 눌러 주세요.'
              : personalError(error),
          );
      });
    return () => controller.abort();
  }, [jobId, attempt, local]);
  useEffect(() => () => operationRef.current?.abort(), []);

  async function analyze() {
    const controller = new AbortController();
    operationRef.current?.abort();
    operationRef.current = controller;
    setBusy(true);
    setError('');
    try {
      const task = await analyzeMatch(jobId, controller.signal);
      if (!controller.signal.aborted)
        setMatch((previous) =>
          previous
            ? { ...previous, status: task.status, score: null }
            : previous,
        );
      const result =
        task.status === 'PENDING'
          ? await waitForMatch(jobId, controller.signal)
          : await getMatch(jobId, controller.signal);
      if (!controller.signal.aborted) setMatch(result);
    } catch (error: unknown) {
      if (!controller.signal.aborted)
        setError(
          error instanceof Error && error.message === 'POLLING_EXPIRED'
            ? '분석이 계속 진행 중입니다. 자동 조회를 멈췄습니다. 결과 다시 확인을 눌러 주세요.'
            : personalError(error),
        );
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  if (!local)
    return (
      <section className="analysis-notice">
        <h2>AI 적합도</h2>
        <p>개인 추천·분석은 이 컴퓨터의 localhost에서만 사용할 수 있습니다.</p>
      </section>
    );
  return (
    <section className="analysis-notice match-panel">
      <p className="eyebrow">AI MATCHING</p>
      <h2>내 경험과의 적합도</h2>
      <p className="form-help">
        채용 요건과 경험의 일치 정도이며 합격 확률이 아닙니다. 미확인 정보와
        실제 부족한 요건을 구분합니다.
      </p>
      {error && (
        <p className="inline-notice error-notice" role="alert">
          {error}
        </p>
      )}
      {!match && !error && <p role="status">분석 상태를 확인하는 중입니다.</p>}
      {match && (
        <>
          <div className="match-score" role="status">
            <strong>
              {match.status === 'READY' && match.score !== null
                ? `${match.score}%`
                : matchLabels[match.status]}
            </strong>
            <span>{match.summary}</span>
          </div>
          <p className="form-help">
            확인 근거 {match.evidenceCount}개 · 평가 범위:{' '}
            {match.evaluatedCategories.length
              ? match.evaluatedCategories
                  .map((category) => categoryLabels[category])
                  .join(' · ')
              : '평가 가능한 범주 없음'}
            . 미확인 범주는 점수 계산에서 제외됩니다.
          </p>
          {match.analyzedAt && (
            <p className="form-help">
              분석일 {formatDate(match.analyzedAt)} · 프로필 v
              {match.profileVersion ?? '미확인'} · 공고 v
              {match.jobVersion ?? '미확인'} · 정책{' '}
              {match.scoringVersion ?? '미확인'}
            </p>
          )}
          {match.errorCode && (
            <p className="form-help">분석 오류 코드: {match.errorCode}</p>
          )}
          {[
            ['strengths', '일치 근거'],
            ['gaps', '부족한 요건'],
            ['unknowns', '확인이 필요한 요건'],
          ].map(([key, title]) => {
            const values = match[key as 'strengths' | 'gaps' | 'unknowns'];
            return values.length > 0 ? (
              <div className="match-findings" key={key}>
                <h3>{title}</h3>
                <ul>
                  {values.map((value, index) => (
                    <li key={index}>{value}</li>
                  ))}
                </ul>
              </div>
            ) : null;
          })}
          {match.requirements.map((item, index) => (
            <article className="requirement-result" key={index}>
              <div className="card-badges">
                <span className="badge">{categoryLabels[item.category]}</span>
                <span
                  className={`badge ${item.match === 'GAP' ? 'gap-badge' : 'badge-neutral'}`}
                >
                  {comparisonLabels[item.match]}
                </span>
              </div>
              <h3>{item.text}</h3>
              <p>{item.reason}</p>
              <p className="form-help">
                근거:{' '}
                {item.evidence.length
                  ? item.evidence.join(' · ')
                  : '확인 가능한 근거 없음'}
              </p>
            </article>
          ))}
        </>
      )}
      <div className="form-actions">
        <button
          className="button-quiet"
          disabled={busy}
          onClick={() => {
            setError('');
            setAttempt((value) => value + 1);
          }}
        >
          결과 다시 확인
        </button>
        {(!match ||
          !['READY', 'PENDING', 'BUDGET_EXHAUSTED'].includes(match.status)) && (
          <button
            className="button-primary"
            disabled={busy}
            onClick={() => void analyze()}
          >
            {busy
              ? '분석 요청 처리 중…'
              : match?.status === 'FAILED'
                ? 'AI 분석 다시 시도'
                : 'AI 적합도 분석하기'}
          </button>
        )}
      </div>
      <p className="form-help">
        직접 분석은 하루 최대 3건이며 남은 예산 범위에서만 실행됩니다. OpenAI
        전송 동의와 확정 프로필이 필요합니다.{' '}
        <a href="/profile">프로필·동의 설정 →</a>
      </p>
    </section>
  );
}
