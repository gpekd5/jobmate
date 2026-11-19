export type JobSource = 'MANUAL' | 'SARAMIN' | 'WANTED' | 'JOBKOREA';
export type DetailAvailability =
  'AVAILABLE' | 'PARTIAL' | 'UNAVAILABLE' | 'RESTRICTED';
export type DetailSource =
  'OFFICIAL_API' | 'PERMITTED_CONTENT' | 'USER_PROVIDED' | 'UNKNOWN';

export interface JobSummary {
  jobId: number;
  source: JobSource;
  companyName: string;
  title: string;
  region: string | null;
  employmentType: string | null;
  experienceText: string | null;
  postedAt: string | null;
  deadlineAt: string | null;
  active: boolean;
  originalUrl: string | null;
  detailAvailability: DetailAvailability;
  lastCollectedAt: string | null;
  jobVersion: number;
  updatedAt: string;
}

export interface JobDetailData extends JobSummary {
  externalJobId: string | null;
  jobRole: string | null;
  educationText: string | null;
  salaryText: string | null;
  responsibilities: string[] | null;
  requiredQualifications: string[] | null;
  preferredQualifications: string[] | null;
  skills: string[] | null;
  workConditions: string[] | null;
  detailSource: DetailSource;
  detailExtractedAt: string | null;
}

export interface JobPage {
  items: JobSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface JobFilters {
  keyword: string;
  region: string;
  jobRole: string;
  source: JobSource | '';
  activeOnly: boolean;
  sort: 'NEWEST' | 'DEADLINE';
  page: number;
  size: number;
}

export const jobSources: JobSource[] = [
  'MANUAL',
  'SARAMIN',
  'WANTED',
  'JOBKOREA',
];
export const sourceLabels: Record<JobSource, string> = {
  MANUAL: '개발용 직접 등록',
  SARAMIN: '사람인',
  WANTED: '원티드',
  JOBKOREA: '잡코리아',
};
export const availabilityLabels: Record<DetailAvailability, string> = {
  AVAILABLE: '상세 확보',
  PARTIAL: '상세 일부 확보',
  UNAVAILABLE: '상세 미확보',
  RESTRICTED: '상세 이용 제한',
};
