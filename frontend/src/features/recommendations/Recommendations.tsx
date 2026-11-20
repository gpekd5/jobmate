import { useEffect, useRef, useState } from 'react';
import type { MouseEvent } from 'react';
import { canUsePersonalApi, personalError } from '../../shared/api/localApi';
import {
  getRecommendations,
  matchLabels,
  refreshRecommendations,
  waitForMatch,
} from './api';
import type { RecommendationPage } from './api';
import { sourceLabels } from '../jobs/types';
import { formatDate } from '../jobs/api';
import { AiAvailability } from '../../shared/api/AiAvailability';

type FeedState =
  | { status: 'loading' }
  | { status: 'failed'; message: string }
  | { status: 'ready'; data: RecommendationPage };

export function Recommendations({
  onNavigate,
}: {
  onNavigate: (event: MouseEvent<HTMLAnchorElement>, href: string) => void;
}) {
  const [state, setState] = useState<FeedState>({ status: 'loading' });
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<'MATCH_SCORE' | 'NEWEST' | 'DEADLINE'>(
    'MATCH_SCORE',
  );
  const [analysisStatus, setAnalysisStatus] = useState<
    'ALL' | 'ANALYZED' | 'NOT_ANALYZED'
  >('ALL');
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [operationError, setOperationError] = useState('');
  const [notice, setNotice] = useState('');
  const operationRef = useRef<AbortController | null>(null);
  const local = canUsePersonalApi();

  useEffect(() => {
    if (!local) return;
    const controller = new AbortController();
    getRecommendations(page, sort, analysisStatus, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setState({ status: 'ready', data });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted)
          setState({ status: 'failed', message: personalError(error) });
      });
    return () => controller.abort();
  }, [page, sort, analysisStatus, attempt, local]);
  useEffect(() => () => operationRef.current?.abort(), []);

  function reload() {
    setState({ status: 'loading' });
    setAttempt((value) => value + 1);
  }
  async function refresh() {
    const controller = new AbortController();
    operationRef.current?.abort();
    operationRef.current = controller;
    setBusy(true);
    setOperationError('');
    setNotice('');
    try {
      const task = await refreshRecommendations(controller.signal);
      setNotice(
        task.status === 'UNCHANGED'
          ? '새로 분석할 공고가 없습니다. 저장된 결과를 표시합니다.'
          : '추천 분석을 요청했습니다. 이력서 원문을 다시 분석하지 않습니다.',
      );
      if (task.jobId !== null && task.status === 'PENDING')
        await waitForMatch(task.jobId, controller.signal);
      if (!controller.signal.aborted) reload();
    } catch (error: unknown) {
      if (!controller.signal.aborted)
        setOperationError(
          error instanceof Error && error.message === 'POLLING_EXPIRED'
            ? '분석이 진행 중입니다. 자동 조회를 멈췄으니 결과 다시 확인을 눌러 주세요.'
            : personalError(error),
        );
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  return (
    <section
      className="recommendation-feed"
      aria-labelledby="recommendation-title"
    >
      <div className="section-heading">
        <div>
          <p className="eyebrow">FOR YOUR NEXT CHAPTER</p>
          <h2 id="recommendation-title">나를 위한 기회</h2>
        </div>
        <a
          className="button-quiet"
          href="/profile"
          onClick={(event) => onNavigate(event, '/profile')}
        >
          내 프로필 설정 →
        </a>
      </div>
      <p className="form-help">
        확정 프로필과 공고 요건을 비교한 적합도이며 합격 확률이 아닙니다. 미분석
        공고도 숨기지 않습니다.
      </p>
      {!local ? (
        <div className="message-panel">
          <h3>개인 추천은 localhost 전용입니다</h3>
          <p>
            이 컴퓨터의 localhost에서 프로필과 추천을 확인해 주세요. 전체 공고는
            아래에서 탐색할 수 있습니다.
          </p>
        </div>
      ) : (
        <>
          <AiAvailability />
          <div className="recommendation-controls">
            <label>
              추천 정렬
              <select
                value={sort}
                onChange={(event) => {
                  setState({ status: 'loading' });
                  setPage(0);
                  setSort(event.target.value as typeof sort);
                }}
              >
                <option value="MATCH_SCORE">적합도 높은 순</option>
                <option value="NEWEST">최신순</option>
                <option value="DEADLINE">마감 임박순</option>
              </select>
            </label>
            <label>
              AI 분석 상태
              <select
                value={analysisStatus}
                onChange={(event) => {
                  setState({ status: 'loading' });
                  setPage(0);
                  setAnalysisStatus(
                    event.target.value as typeof analysisStatus,
                  );
                }}
              >
                <option value="ALL">전체</option>
                <option value="ANALYZED">분석 완료</option>
                <option value="NOT_ANALYZED">미분석 후보</option>
              </select>
            </label>
          </div>
          {state.status === 'loading' && (
            <div className="message-panel" role="status">
              <span className="loading-dot" aria-hidden="true" />
              <h3>추천 결과를 불러오는 중입니다</h3>
              <p>조회만으로 AI 분석을 다시 호출하지 않습니다.</p>
            </div>
          )}
          {state.status === 'failed' && (
            <div className="message-panel" role="alert">
              <h3>추천을 확인할 수 없어요</h3>
              <p>{state.message}</p>
              <div className="form-actions">
                <a
                  className="button-primary"
                  href="/profile"
                  onClick={(event) => onNavigate(event, '/profile')}
                >
                  프로필·동의 설정
                </a>
                <button className="button-quiet" onClick={reload}>
                  결과 다시 확인
                </button>
              </div>
            </div>
          )}
          {state.status === 'ready' && (
            <>
              <div className="results-meta">
                <p>
                  추천·탐색 후보 <strong>{state.data.totalElements}개</strong>
                </p>
                <span>미산출 점수는 표시하지 않음</span>
              </div>
              {state.data.items.length === 0 ? (
                <div className="message-panel">
                  <h3>아직 표시할 추천이 없어요</h3>
                  <p>
                    선호 조건과 분석 상태 필터를 확인하거나 아래 전체 공고를
                    탐색해 주세요.
                  </p>
                </div>
              ) : (
                <div className="job-grid">
                  {state.data.items.map((job) => (
                    <article
                      className="job-card recommendation-card"
                      key={job.jobId}
                    >
                      <div className="card-topline">
                        <span className="source-label">
                          {sourceLabels[job.source]}
                        </span>
                        <span className="badge">
                          {matchLabels[job.match.status]}
                        </span>
                      </div>
                      <p className="company-name">{job.companyName}</p>
                      <h3>
                        <a
                          className="job-title"
                          href={`/jobs/${job.jobId}`}
                          onClick={(event) =>
                            onNavigate(event, `/jobs/${job.jobId}`)
                          }
                        >
                          {job.title} ↗
                        </a>
                      </h3>
                      <p className="job-meta">
                        {job.region ?? '지역 미확인'} ·{' '}
                        {job.active === false
                          ? '마감'
                          : job.active === true
                            ? '모집 중'
                            : '모집 상태 미확인'}
                      </p>
                      <p className="deadline">
                        마감일{' '}
                        <strong>{formatDate(job.deadlineAt ?? null)}</strong>
                      </p>
                      <div className="recommendation-score">
                        {job.match.status === 'READY' &&
                        job.match.score !== null ? (
                          <>
                            <strong>
                              {job.match.score}
                              <span>%</span>
                            </strong>
                            <span>채용 요건 적합도</span>
                          </>
                        ) : (
                          <strong className="no-score">
                            {matchLabels[job.match.status]}
                          </strong>
                        )}
                      </div>
                      {job.match.summary && (
                        <p className="match-card-summary">
                          {job.match.summary}
                        </p>
                      )}
                      {job.match.strengths
                        .slice(0, 2)
                        .map((strength, index) => (
                          <p className="match-card-evidence" key={index}>
                            일치 · {strength}
                          </p>
                        ))}
                      {[
                        ...job.match.gaps.map((value) => `부족 · ${value}`),
                        ...job.match.unknowns.map(
                          (value) => `미확인 · ${value}`,
                        ),
                      ]
                        .slice(0, 2)
                        .map((value, index) => (
                          <p className="match-card-unknown" key={index}>
                            {value}
                          </p>
                        ))}
                      <p className="card-updated">
                        분석일 {formatDate(job.match.analyzedAt)}
                      </p>
                    </article>
                  ))}
                </div>
              )}
              {state.data.totalPages > 1 && (
                <nav className="pagination" aria-label="추천 페이지">
                  <button
                    className="button-quiet"
                    disabled={page === 0}
                    onClick={() => {
                      setState({ status: 'loading' });
                      setPage((page) => page - 1);
                    }}
                  >
                    ← 이전
                  </button>
                  <span>
                    {page + 1} / {state.data.totalPages}
                  </span>
                  <button
                    className="button-quiet"
                    disabled={!state.data.hasNext}
                    onClick={() => {
                      setState({ status: 'loading' });
                      setPage((page) => page + 1);
                    }}
                  >
                    다음 →
                  </button>
                </nav>
              )}
            </>
          )}
          {operationError && (
            <p className="inline-notice error-notice" role="alert">
              {operationError}
            </p>
          )}
          {notice && (
            <p className="inline-notice" role="status">
              {notice}
            </p>
          )}
          <div className="recommendation-actions">
            <button className="button-quiet" disabled={busy} onClick={reload}>
              결과 다시 확인
            </button>
            <button
              className="button-primary"
              disabled={busy}
              onClick={() => void refresh()}
            >
              {busy ? '추천 분석 상태 확인 중…' : '새 공고 추천 분석'}
            </button>
          </div>
          <p className="form-help">
            추천 분석은 남은 예산과 회차·일일 한도 내에서만 실행됩니다. 개별
            공고는 상세에서 하루 최대 3건 직접 분석할 수 있습니다.
          </p>
        </>
      )}
    </section>
  );
}
