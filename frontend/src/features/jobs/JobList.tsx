import { useEffect, useState } from 'react';
import { buildJobQuery, errorMessage, fetchJobs, readFilters } from './api';
import { JobCard } from './JobCard';
import { JobFilters } from './JobFilters';
import type { JobPage } from './types';
import type { MouseEvent } from 'react';

type ListState =
  | { status: 'loading' }
  | { status: 'failed'; message: string }
  | { status: 'ready'; data: JobPage };

interface JobListProps {
  search: string;
  navigate: (href: string) => void;
  onNavigate: (event: MouseEvent<HTMLAnchorElement>, href: string) => void;
}

export function JobList({ search, navigate, onNavigate }: JobListProps) {
  const [state, setState] = useState<ListState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const filters = readFilters(search);

  useEffect(() => {
    const controller = new AbortController();
    fetchJobs(readFilters(search), controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setState({ status: 'ready', data });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted)
          setState({ status: 'failed', message: errorMessage(error) });
      });
    return () => controller.abort();
  }, [search, attempt]);

  function retry() {
    setState({ status: 'loading' });
    setAttempt((value) => value + 1);
  }

  return (
    <section id="jobs" className="catalog" aria-labelledby="catalog-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">EXPLORE OPPORTUNITIES</p>
          <h2 id="catalog-title">전체 공고 탐색</h2>
        </div>
        <span className="section-note">AI 분석 여부와 관계없이</span>
      </div>
      <JobFilters
        key={search}
        filters={filters}
        onApply={(next) => navigate(`/?${buildJobQuery(next)}#jobs`)}
      />
      {state.status === 'loading' && (
        <div className="message-panel" role="status">
          <span className="loading-dot" aria-hidden="true" />
          <h3>공고를 불러오는 중이에요</h3>
          <p>최신 공고 정보를 확인하고 있습니다.</p>
        </div>
      )}
      {state.status === 'failed' && (
        <div className="message-panel error-panel" role="alert">
          <h3>잠시 연결이 어려워요</h3>
          <p>{state.message}</p>
          <button className="button-primary" onClick={retry}>
            다시 시도
          </button>
        </div>
      )}
      {state.status === 'ready' && (
        <>
          <div className="results-meta" role="status">
            <p>
              검색 결과{' '}
              <strong>
                {state.data.totalElements.toLocaleString('ko-KR')}개
              </strong>
            </p>
            <span>
              {filters.sort === 'DEADLINE' ? '마감 임박순' : '최신순'} ·{' '}
              {filters.activeOnly ? '모집 중' : '마감 포함'}
            </span>
          </div>
          {state.data.items.length === 0 ? (
            <div className="message-panel">
              <span className="empty-symbol" aria-hidden="true">
                ⌕
              </span>
              <h3>아직 표시할 공고가 없어요</h3>
              <p>
                다른 검색어나 필터를 사용해 보세요.
                <br />
                자동 공고 수집은 준비 중입니다.
              </p>
              <button
                className="button-quiet"
                onClick={() => navigate('/#jobs')}
              >
                검색 조건 초기화
              </button>
            </div>
          ) : (
            <div className="job-grid">
              {state.data.items.map((job) => (
                <JobCard
                  key={job.jobId}
                  job={job}
                  search={search}
                  onNavigate={onNavigate}
                />
              ))}
            </div>
          )}
          {(state.data.totalPages > 1 || state.data.page > 0) && (
            <nav className="pagination" aria-label="공고 페이지">
              <button
                className="button-quiet"
                disabled={state.data.page === 0}
                onClick={() =>
                  navigate(
                    `/?${buildJobQuery({ ...filters, page: state.data.page - 1 })}#jobs`,
                  )
                }
              >
                ← 이전
              </button>
              <span>
                {state.data.page + 1} /{' '}
                {Math.max(state.data.totalPages, state.data.page + 1)}
              </span>
              <button
                className="button-quiet"
                disabled={!state.data.hasNext}
                onClick={() =>
                  navigate(
                    `/?${buildJobQuery({ ...filters, page: state.data.page + 1 })}#jobs`,
                  )
                }
              >
                다음 →
              </button>
            </nav>
          )}
        </>
      )}
    </section>
  );
}
