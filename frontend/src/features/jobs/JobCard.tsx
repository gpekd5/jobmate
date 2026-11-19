import { formatDate } from './api';
import { availabilityLabels, sourceLabels } from './types';
import type { JobSummary } from './types';
import type { MouseEvent } from 'react';

interface JobCardProps {
  job: JobSummary;
  search: string;
  onNavigate: (event: MouseEvent<HTMLAnchorElement>, href: string) => void;
}

export function JobCard({ job, search, onNavigate }: JobCardProps) {
  const href = `/jobs/${job.jobId}${search}`;
  return (
    <article className="job-card">
      <div className="card-topline">
        <span className="source-label">{sourceLabels[job.source]}</span>
        <span
          className={`status-pill ${job.active ? 'is-active' : 'is-closed'}`}
        >
          {job.active ? '모집 중' : '마감'}
        </span>
      </div>
      <p className="company-name">{job.companyName}</p>
      <h3>
        <a
          className="job-title"
          href={href}
          onClick={(event) => onNavigate(event, href)}
        >
          {job.title}
          <span aria-hidden="true"> ↗</span>
        </a>
      </h3>
      <p className="job-meta">
        {job.region ?? '지역 미확인'} · {job.experienceText ?? '경력 미확인'}
      </p>
      <p className="deadline">
        마감일 <strong>{formatDate(job.deadlineAt)}</strong>
      </p>
      <div className="card-badges">
        <span className="badge">미분석</span>
        <span className="badge badge-neutral">
          {availabilityLabels[job.detailAvailability]}
        </span>
      </div>
      <p className="card-updated">
        최근 수집 {formatDate(job.lastCollectedAt)}
      </p>
    </article>
  );
}
