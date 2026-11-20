import {
  abortableDelay,
  isRecord,
  jsonBody,
  personalRequest,
} from '../../shared/api/localApi.ts';
import type { JobSource } from '../jobs/types.ts';

export type MatchStatus =
  | 'NOT_ANALYZED'
  | 'PENDING'
  | 'READY'
  | 'INSUFFICIENT_DATA'
  | 'BUDGET_EXHAUSTED'
  | 'FAILED';
export interface MatchSummary {
  status: MatchStatus;
  score: number | null;
  summary: string | null;
  strengths: string[];
  gaps: string[];
  unknowns: string[];
  analyzedAt: string | null;
}
export interface RecommendationItem {
  jobId: number;
  title: string;
  companyName: string;
  source: JobSource;
  region: string | null;
  originalUrl: string | null;
  match: MatchSummary;
  deadlineAt?: string | null;
  active?: boolean;
}
export interface RecommendationPage {
  items: RecommendationItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}
export interface RequirementMatch {
  category: 'MUST' | 'PREFERRED' | 'RESPONSIBILITY' | 'WORK_CONDITION';
  text: string;
  match: 'MATCH' | 'PARTIAL' | 'GAP' | 'UNKNOWN';
  reason: string;
  evidence: string[];
}
export interface MatchDetail extends MatchSummary {
  evidenceCount: number;
  evaluatedCategories: RequirementMatch['category'][];
  jobId: number;
  scoreMeaning?: string;
  requirements: RequirementMatch[];
  profileVersion?: number;
  jobVersion?: number;
  scoringVersion?: string;
  errorCode?: string | null;
}
export const matchLabels: Record<MatchStatus, string> = {
  NOT_ANALYZED: '미분석',
  PENDING: '분석 중',
  READY: '분석 완료',
  INSUFFICIENT_DATA: '분석 불충분',
  BUDGET_EXHAUSTED: '예산 소진',
  FAILED: '분석 실패',
};

function strings(value: unknown): value is string[] {
  return (
    Array.isArray(value) && value.every((item) => typeof item === 'string')
  );
}
function isMatchSummary(value: unknown): value is MatchSummary {
  return (
    isRecord(value) &&
    Object.keys(matchLabels).includes(String(value.status)) &&
    (value.score === null ||
      (typeof value.score === 'number' &&
        Number.isFinite(value.score) &&
        value.score >= 0 &&
        value.score <= 100)) &&
    (value.summary === null ||
      value.summary === undefined ||
      typeof value.summary === 'string') &&
    strings(value.strengths) &&
    strings(value.gaps) &&
    strings(value.unknowns)
  );
}
function isMatchDetail(value: unknown): value is MatchDetail {
  return (
    isMatchSummary(value) &&
    isRecord(value) &&
    Number.isSafeInteger(value.jobId) &&
    Number.isSafeInteger(value.evidenceCount) &&
    Number(value.evidenceCount) >= 0 &&
    Array.isArray(value.evaluatedCategories) &&
    value.evaluatedCategories.every((category) =>
      ['MUST', 'PREFERRED', 'RESPONSIBILITY', 'WORK_CONDITION'].includes(
        String(category),
      ),
    ) &&
    Array.isArray(value.requirements) &&
    value.requirements.every(
      (item) =>
        isRecord(item) &&
        ['MUST', 'PREFERRED', 'RESPONSIBILITY', 'WORK_CONDITION'].includes(
          String(item.category),
        ) &&
        ['MATCH', 'PARTIAL', 'GAP', 'UNKNOWN'].includes(String(item.match)) &&
        typeof item.text === 'string' &&
        typeof item.reason === 'string' &&
        strings(item.evidence),
    )
  );
}
function isRecommendationPage(value: unknown): value is RecommendationPage {
  return (
    isRecord(value) &&
    Array.isArray(value.items) &&
    value.items.every(
      (item) =>
        isRecord(item) &&
        Number.isSafeInteger(item.jobId) &&
        typeof item.title === 'string' &&
        typeof item.companyName === 'string' &&
        ['MANUAL', 'SARAMIN', 'WANTED', 'JOBKOREA'].includes(
          String(item.source),
        ) &&
        (item.region === null || typeof item.region === 'string') &&
        isMatchSummary(item.match),
    ) &&
    ['page', 'size', 'totalElements', 'totalPages'].every((key) =>
      Number.isSafeInteger(value[key]),
    ) &&
    typeof value.hasNext === 'boolean'
  );
}

export function getRecommendations(
  page: number,
  sort: 'MATCH_SCORE' | 'NEWEST' | 'DEADLINE',
  analysisStatus: 'ALL' | 'ANALYZED' | 'NOT_ANALYZED',
  signal: AbortSignal,
) {
  return personalRequest(
    `/api/v1/recommendations?page=${page}&size=20&sort=${sort}&onlyActive=true&analysisStatus=${analysisStatus}`,
    signal,
    isRecommendationPage,
  );
}
export function getMatch(jobId: number, signal: AbortSignal) {
  return personalRequest(`/api/v1/jobs/${jobId}/match`, signal, isMatchDetail);
}
export function refreshRecommendations(signal: AbortSignal) {
  return personalRequest(
    '/api/v1/recommendations/refresh',
    signal,
    (value): value is { jobId: number | null; status: string } =>
      isRecord(value) &&
      typeof value.status === 'string' &&
      (value.jobId === null || Number.isSafeInteger(value.jobId)),
    { method: 'POST', ...jsonBody({}) },
  );
}
export function analyzeMatch(jobId: number, signal: AbortSignal) {
  return personalRequest(
    `/api/v1/jobs/${jobId}/match/analyses`,
    signal,
    (
      value,
    ): value is { jobId: number; status: MatchStatus; requestId?: string } =>
      isRecord(value) &&
      value.jobId === jobId &&
      Object.keys(matchLabels).includes(String(value.status)),
    { method: 'POST', ...jsonBody({}) },
  );
}
export async function waitForMatch(jobId: number, signal: AbortSignal) {
  for (let attempt = 0; attempt < 40; attempt++) {
    const match = await getMatch(jobId, signal);
    if (match.status !== 'PENDING') return match;
    await abortableDelay(3000, signal);
  }
  throw new Error('POLLING_EXPIRED');
}
