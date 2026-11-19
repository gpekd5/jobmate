import { useEffect, useState } from 'react';
import { errorMessage, fetchJob, formatDate, safeOriginalUrl } from './api';
import { availabilityLabels, sourceLabels } from './types';
import type { JobDetailData, DetailSource } from './types';
import type { MouseEvent } from 'react';

type DetailState =
  | { status: 'loading' }
  | { status: 'failed'; message: string }
  | { status: 'ready'; data: JobDetailData };
const detailSourceLabels: Record<DetailSource, string> = {
  OFFICIAL_API: '공식 API',
  PERMITTED_CONTENT: '이용이 허용된 콘텐츠',
  USER_PROVIDED: '사용자 제공',
  UNKNOWN: '미확인',
};

function DetailSection({
  title,
  items,
}: {
  title: string;
  items: string[] | null;
}) {
  return (
    <section className="detail-section">
      <h2>{title}</h2>
      {items === null ? (
        <p className="unconfirmed">미확인 · 제공된 정보가 없습니다.</p>
      ) : items.length === 0 ? (
        <p className="muted">해당 없음으로 명시된 항목입니다.</p>
      ) : (
        <ul>
          {items.map((item, index) => (
            <li key={index}>{item}</li>
          ))}
        </ul>
      )}
    </section>
  );
}

interface JobDetailProps {
  jobId: number;
  search: string;
  onNavigate: (event: MouseEvent<HTMLAnchorElement>, href: string) => void;
}

export function JobDetail({ jobId, search, onNavigate }: JobDetailProps) {
  const [state, setState] = useState<DetailState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const backHref = `/${search}#jobs`;

  useEffect(() => {
    const controller = new AbortController();
    fetchJob(jobId, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setState({ status: 'ready', data });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted)
          setState({ status: 'failed', message: errorMessage(error) });
      });
    return () => controller.abort();
  }, [jobId, attempt]);

  function retry() {
    setState({ status: 'loading' });
    setAttempt((value) => value + 1);
  }

  return (
    <main className="detail-page">
      <a
        className="back-link"
        href={backHref}
        onClick={(event) => onNavigate(event, backHref)}
      >
        ← 공고 목록으로
      </a>
      {state.status === 'loading' && (
        <div className="message-panel" role="status">
          <span className="loading-dot" aria-hidden="true" />
          <h1>공고 상세를 불러오는 중이에요</h1>
        </div>
      )}
      {state.status === 'failed' && (
        <div className="message-panel error-panel" role="alert">
          <h1>공고를 불러올 수 없어요</h1>
          <p>{state.message}</p>
          <button className="button-primary" onClick={retry}>
            다시 시도
          </button>
        </div>
      )}
      {state.status === 'ready' && (
        <>
          <header className="detail-header">
            <div className="card-topline">
              <span className="source-label">
                {sourceLabels[state.data.source]}
              </span>
              <span
                className={`status-pill ${state.data.active ? 'is-active' : 'is-closed'}`}
              >
                {state.data.active ? '모집 중' : '마감'}
              </span>
            </div>
            <p className="company-name">{state.data.companyName}</p>
            <h1>{state.data.title}</h1>
            <p className="job-meta">
              {state.data.region ?? '지역 미확인'} ·{' '}
              {state.data.jobRole ?? '직무 미확인'}
            </p>
            <div className="card-badges">
              <span className="badge">미분석</span>
              <span className="badge badge-neutral">
                {availabilityLabels[state.data.detailAvailability]}
              </span>
            </div>
          </header>
          <div className="detail-layout">
            <div className="detail-body">
              <aside className="provenance-note">
                <strong>
                  {availabilityLabels[state.data.detailAvailability]}
                </strong>
                <p>
                  제공된 공고 정보만 표시합니다. 미확인 항목은 자격 요건으로
                  추측하지 않습니다. 최종 내용은 원문에서 확인해 주세요.
                </p>
              </aside>
              <DetailSection
                title="담당업무"
                items={state.data.responsibilities}
              />
              <DetailSection
                title="필수 지원자격"
                items={state.data.requiredQualifications}
              />
              <DetailSection
                title="우대사항"
                items={state.data.preferredQualifications}
              />
              <DetailSection title="기술 스택" items={state.data.skills} />
              <DetailSection
                title="근무조건"
                items={state.data.workConditions}
              />
              <section className="analysis-notice">
                <p className="eyebrow">AI MATCHING</p>
                <h2>아직 분석하지 않은 공고예요</h2>
                <p>
                  이력서·프로필 기반 맞춤 추천과 개별 AI 분석은 준비 중입니다.
                  현재는 적합도 점수나 추천 근거를 제공하지 않습니다.
                </p>
              </section>
            </div>
            <aside className="detail-sidebar">
              <section className="facts-panel">
                <h2>공고 정보</h2>
                <dl>
                  {[
                    ['근무지', state.data.region],
                    ['직무', state.data.jobRole],
                    ['고용 형태', state.data.employmentType],
                    ['경력', state.data.experienceText],
                    ['학력', state.data.educationText],
                    ['급여', state.data.salaryText],
                    ['게시일', formatDate(state.data.postedAt)],
                    ['마감일', formatDate(state.data.deadlineAt)],
                  ].map(([label, value]) => (
                    <div key={label}>
                      <dt>{label}</dt>
                      <dd>{value ?? '미확인'}</dd>
                    </div>
                  ))}
                </dl>
                <OriginalLink url={state.data.originalUrl} />
              </section>
              <section className="facts-panel provenance-panel">
                <h2>출처와 최신화</h2>
                <dl>
                  <div>
                    <dt>공고 출처</dt>
                    <dd>{sourceLabels[state.data.source]}</dd>
                  </div>
                  <div>
                    <dt>상세 정보 출처</dt>
                    <dd>{detailSourceLabels[state.data.detailSource]}</dd>
                  </div>
                  <div>
                    <dt>상세 확보일</dt>
                    <dd>{formatDate(state.data.detailExtractedAt)}</dd>
                  </div>
                  <div>
                    <dt>최근 수집일</dt>
                    <dd>{formatDate(state.data.lastCollectedAt)}</dd>
                  </div>
                  <div>
                    <dt>최근 변경일</dt>
                    <dd>{formatDate(state.data.updatedAt)}</dd>
                  </div>
                  <div>
                    <dt>공고 버전</dt>
                    <dd>{state.data.jobVersion}</dd>
                  </div>
                </dl>
              </section>
            </aside>
          </div>
        </>
      )}
    </main>
  );
}

function OriginalLink({ url }: { url: string | null }) {
  const href = safeOriginalUrl(url);
  return href ? (
    <a
      className="button-primary original-link"
      href={href}
      target="_blank"
      rel="noopener noreferrer"
    >
      원문 공고 보기 ↗<span className="sr-only"> (새 탭)</span>
    </a>
  ) : (
    <p className="muted">안전하게 열 수 있는 원문 링크가 없습니다.</p>
  );
}
