import { jobSources } from './types.ts';
import type {
  JobDetailData,
  JobFilters,
  JobPage,
  JobSummary,
} from './types.ts';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}

function isSummary(value: unknown): value is JobSummary {
  return (
    isRecord(value) &&
    Number.isSafeInteger(value.jobId) &&
    Number(value.jobId) > 0 &&
    typeof value.companyName === 'string' &&
    typeof value.title === 'string' &&
    jobSources.some((source) => source === value.source) &&
    typeof value.active === 'boolean' &&
    ['AVAILABLE', 'PARTIAL', 'UNAVAILABLE', 'RESTRICTED'].includes(
      String(value.detailAvailability),
    ) &&
    [
      'region',
      'employmentType',
      'experienceText',
      'postedAt',
      'deadlineAt',
      'originalUrl',
      'lastCollectedAt',
    ].every((key) => isNullableString(value[key])) &&
    Number.isSafeInteger(value.jobVersion) &&
    typeof value.updatedAt === 'string'
  );
}

function isJobPage(value: unknown): value is JobPage {
  return (
    isRecord(value) &&
    Array.isArray(value.items) &&
    value.items.every(isSummary) &&
    ['page', 'totalElements', 'totalPages'].every(
      (key) => Number.isSafeInteger(value[key]) && Number(value[key]) >= 0,
    ) &&
    Number.isSafeInteger(value.size) &&
    Number(value.size) > 0 &&
    typeof value.hasNext === 'boolean'
  );
}

function isJobDetail(value: unknown): value is JobDetailData {
  if (!isSummary(value) || !isRecord(value)) return false;
  return (
    [
      'externalJobId',
      'jobRole',
      'educationText',
      'salaryText',
      'detailExtractedAt',
    ].every((key) => isNullableString(value[key])) &&
    [
      'responsibilities',
      'requiredQualifications',
      'preferredQualifications',
      'skills',
      'workConditions',
    ].every((key) => {
      const items = value[key];
      return (
        items === null ||
        (Array.isArray(items) &&
          items.every((item) => typeof item === 'string'))
      );
    }) &&
    ['OFFICIAL_API', 'PERMITTED_CONTENT', 'USER_PROVIDED', 'UNKNOWN'].includes(
      String(value.detailSource),
    )
  );
}

export class JobsApiError extends Error {
  status: number;

  constructor(status: number) {
    super(
      status === 404
        ? '공고를 찾을 수 없습니다. 삭제되었거나 주소가 변경되었을 수 있어요.'
        : status === 400
          ? '검색 조건을 확인해 주세요.'
          : status === 401 || status === 403
            ? '이 공고에 접근할 권한이 없습니다.'
            : '공고를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
    );
    this.name = 'JobsApiError';
    this.status = status;
  }
}

async function request<T>(
  url: string,
  signal: AbortSignal,
  validate: (value: unknown) => value is T,
): Promise<T> {
  const response = await fetch(url, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(10000)]),
  });
  if (!response.ok) throw new JobsApiError(response.status);
  const body: unknown = await response.json();
  if (!isRecord(body) || body.success !== true || !validate(body.data)) {
    throw new Error(
      '공고 응답 형식이 올바르지 않습니다. 잠시 후 다시 시도해 주세요.',
    );
  }
  return body.data;
}

export function readFilters(search: string): JobFilters {
  const query = new URLSearchParams(search);
  const source = query.get('source');
  const page = Number(query.get('page') ?? 0);
  const size = Number(query.get('size') ?? 20);
  return {
    keyword: query.get('keyword') ?? '',
    region: query.get('region') ?? '',
    jobRole: query.get('jobRole') ?? '',
    source: jobSources.find((value) => value === source) ?? '',
    activeOnly: query.get('activeOnly') !== 'false',
    sort: query.get('sort') === 'DEADLINE' ? 'DEADLINE' : 'NEWEST',
    page: Number.isSafeInteger(page) && page >= 0 ? page : 0,
    size: [20, 50, 100].includes(size) ? size : 20,
  };
}

export function buildJobQuery(filters: JobFilters): string {
  const query = new URLSearchParams();
  for (const key of ['keyword', 'region', 'jobRole', 'source'] as const) {
    const value = filters[key].trim();
    if (value) query.set(key, value);
  }
  query.set('activeOnly', String(filters.activeOnly));
  query.set('sort', filters.sort);
  query.set('page', String(filters.page));
  query.set('size', String(filters.size));
  return query.toString();
}

export function fetchJobs(
  filters: JobFilters,
  signal: AbortSignal,
): Promise<JobPage> {
  return request(`/api/v1/jobs?${buildJobQuery(filters)}`, signal, isJobPage);
}

export function fetchJob(
  jobId: number,
  signal: AbortSignal,
): Promise<JobDetailData> {
  return request(`/api/v1/jobs/${jobId}`, signal, isJobDetail);
}

export function safeOriginalUrl(value: string | null): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return ['http:', 'https:'].includes(url.protocol) &&
      !url.username &&
      !url.password
      ? url.href
      : null;
  } catch {
    return null;
  }
}

export function errorMessage(error: unknown): string {
  if (error instanceof Error && error.name === 'TimeoutError')
    return '응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.';
  return error instanceof JobsApiError ||
    (error instanceof Error && error.message.startsWith('공고 응답 형식'))
    ? error.message
    : '서버에 연결할 수 없습니다. 연결 상태를 확인하고 다시 시도해 주세요.';
}

export function formatDate(value: string | null): string {
  if (!value || Number.isNaN(Date.parse(value))) return '미확인';
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value));
}
