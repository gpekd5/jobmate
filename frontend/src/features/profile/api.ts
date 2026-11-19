import {
  isRecord,
  jsonBody,
  personalRequest,
  abortableDelay,
} from '../../shared/api/localApi.ts';

export type DocumentType = 'RESUME' | 'PORTFOLIO';
export type DocumentStatus =
  'NOT_ANALYZED' | 'ANALYZING' | 'ANALYZED' | 'FAILED';
export interface DocumentItem {
  documentId: number;
  type: DocumentType;
  fileName: string;
  analysisStatus: DocumentStatus;
  uploadedAt: string;
}
export interface PortfolioLink {
  linkId: number;
  url: string;
  description?: string;
}
export interface ProfileEntry {
  name: string;
  context:
    | 'WORK'
    | 'TEAM_PROJECT'
    | 'PERSONAL_PROJECT'
    | 'TRAINING_PROJECT'
    | 'LEARNING'
    | 'UNKNOWN';
  summary: string;
  evidence: string;
  confidence: 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN';
}
export interface ProfileContent {
  workExperiences: ProfileEntry[];
  skills: ProfileEntry[];
  projects: ProfileEntry[];
  education: ProfileEntry[];
  certifications: ProfileEntry[];
}
export interface ProfileData extends ProfileContent {
  profileVersion: number;
  confirmed: boolean;
}
export interface Preferences {
  desiredRoles: string[];
  regions: string[];
  employmentTypes: string[];
  minSalary: number | null;
  avoidDispatch: boolean;
  avoidFrequentTravel: boolean;
}
export type ConsentPurpose = 'PROFILE_EXTRACTION' | 'JOB_MATCHING';
export interface Consent {
  provider: 'OPENAI';
  purposes: ConsentPurpose[];
  consented: boolean;
}
export interface ProfileAnalysis {
  analysisId: number;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  result: ProfileContent | null;
  errorCode?: string | null;
  createdAt?: string;
  completedAt?: string | null;
}

export const emptyProfile: ProfileContent = {
  workExperiences: [],
  skills: [],
  projects: [],
  education: [],
  certifications: [],
};
export const emptyPreferences: Preferences = {
  desiredRoles: [],
  regions: [],
  employmentTypes: [],
  minSalary: null,
  avoidDispatch: false,
  avoidFrequentTravel: false,
};
export const profileKeys = [
  'workExperiences',
  'skills',
  'projects',
  'education',
  'certifications',
] as const;

export function isProfileContent(value: unknown): value is ProfileContent {
  return (
    isRecord(value) &&
    profileKeys.every(
      (key) =>
        Array.isArray(value[key]) &&
        value[key].every(
          (entry) =>
            isRecord(entry) &&
            ['name', 'summary', 'evidence'].every(
              (field) => typeof entry[field] === 'string',
            ) &&
            [
              'WORK',
              'TEAM_PROJECT',
              'PERSONAL_PROJECT',
              'TRAINING_PROJECT',
              'LEARNING',
              'UNKNOWN',
            ].includes(String(entry.context)) &&
            ['HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'].includes(
              String(entry.confidence),
            ),
        ),
    )
  );
}
function isProfile(value: unknown): value is ProfileData {
  return (
    isProfileContent(value) &&
    'profileVersion' in value &&
    Number.isSafeInteger(value.profileVersion) &&
    'confirmed' in value &&
    typeof value.confirmed === 'boolean'
  );
}
function isDocument(value: unknown): value is DocumentItem {
  return (
    isRecord(value) &&
    Number.isSafeInteger(value.documentId) &&
    ['RESUME', 'PORTFOLIO'].includes(String(value.type)) &&
    typeof value.fileName === 'string' &&
    ['NOT_ANALYZED', 'ANALYZING', 'ANALYZED', 'FAILED'].includes(
      String(value.analysisStatus),
    )
  );
}
function isLink(value: unknown): value is PortfolioLink {
  return (
    isRecord(value) &&
    Number.isSafeInteger(value.linkId) &&
    typeof value.url === 'string'
  );
}
function isPreferences(value: unknown): value is Preferences {
  return (
    isRecord(value) &&
    ['desiredRoles', 'regions', 'employmentTypes'].every(
      (key) =>
        Array.isArray(value[key]) &&
        value[key].every((entry) => typeof entry === 'string'),
    ) &&
    typeof value.avoidDispatch === 'boolean' &&
    typeof value.avoidFrequentTravel === 'boolean' &&
    (value.minSalary === null || typeof value.minSalary === 'number')
  );
}
function isConsent(value: unknown): value is Consent {
  return (
    isRecord(value) &&
    value.provider === 'OPENAI' &&
    typeof value.consented === 'boolean' &&
    Array.isArray(value.purposes) &&
    value.purposes.every(
      (purpose) =>
        purpose === 'PROFILE_EXTRACTION' || purpose === 'JOB_MATCHING',
    )
  );
}
function isAnalysis(value: unknown): value is ProfileAnalysis {
  return (
    isRecord(value) &&
    Number.isSafeInteger(value.analysisId) &&
    ['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'].includes(
      String(value.status),
    ) &&
    (value.result === null ||
      value.result === undefined ||
      isProfileContent(value.result))
  );
}
const noContent = (value: unknown): value is undefined => value === undefined;

export function getDocuments(signal: AbortSignal) {
  return personalRequest(
    '/api/v1/me/documents',
    signal,
    (value): value is DocumentItem[] =>
      Array.isArray(value) && value.every(isDocument),
  );
}
export function getLinks(signal: AbortSignal) {
  return personalRequest(
    '/api/v1/me/portfolio-links',
    signal,
    (value): value is PortfolioLink[] =>
      Array.isArray(value) && value.every(isLink),
  );
}
export function getProfile(signal: AbortSignal) {
  return personalRequest('/api/v1/me/profile', signal, isProfile);
}
export function saveProfile(profile: ProfileData, signal: AbortSignal) {
  return personalRequest('/api/v1/me/profile', signal, isProfile, {
    method: 'PUT',
    ...jsonBody({ ...profile, confirmed: true }),
  });
}
export function getPreferences(signal: AbortSignal) {
  return personalRequest('/api/v1/me/preferences', signal, isPreferences);
}
export function savePreferences(preferences: Preferences, signal: AbortSignal) {
  return personalRequest('/api/v1/me/preferences', signal, isPreferences, {
    method: 'PUT',
    ...jsonBody({
      ...preferences,
      desiredRoles: preferences.desiredRoles.filter(Boolean),
      regions: preferences.regions.filter(Boolean),
      employmentTypes: preferences.employmentTypes.filter(Boolean),
    }),
  });
}
export function getConsent(signal: AbortSignal) {
  return personalRequest('/api/v1/me/ai-consent', signal, isConsent);
}
export function saveConsent(purposes: ConsentPurpose[], signal: AbortSignal) {
  return personalRequest('/api/v1/me/ai-consent', signal, isConsent, {
    method: 'PUT',
    ...jsonBody({
      provider: 'OPENAI',
      purposes,
      consented: purposes.length > 0,
    }),
  });
}
export function validateUpload(
  file: Pick<File, 'name' | 'size'>,
): string | null {
  return file.size === 0
    ? '빈 파일은 업로드할 수 없습니다.'
    : file.size > 10 * 1024 * 1024
      ? '파일은 10 MiB 이하여야 합니다.'
      : !/\.(pdf|docx)$/i.test(file.name)
        ? 'PDF 또는 DOCX 파일만 업로드할 수 있습니다.'
        : null;
}
export function uploadDocument(
  file: File,
  type: DocumentType,
  signal: AbortSignal,
) {
  const error = validateUpload(file);
  if (error) throw new Error(error);
  const body = new FormData();
  body.set('file', file);
  body.set('type', type);
  return personalRequest('/api/v1/me/documents', signal, isDocument, {
    method: 'POST',
    body,
  });
}
export function deleteDocument(id: number, signal: AbortSignal) {
  return personalRequest(`/api/v1/me/documents/${id}`, signal, noContent, {
    method: 'DELETE',
  });
}
export function addLink(url: string, description: string, signal: AbortSignal) {
  return personalRequest('/api/v1/me/portfolio-links', signal, isLink, {
    method: 'POST',
    ...jsonBody({ url, description }),
  });
}
export function deleteLink(id: number, signal: AbortSignal) {
  return personalRequest(
    `/api/v1/me/portfolio-links/${id}`,
    signal,
    noContent,
    { method: 'DELETE' },
  );
}
export function startProfileAnalysis(
  documentIds: number[],
  portfolioLinkIds: number[],
  signal: AbortSignal,
) {
  return personalRequest('/api/v1/me/profile/analyses', signal, isAnalysis, {
    method: 'POST',
    ...jsonBody({
      documentIds,
      portfolioLinkIds,
      analysisMode: 'EXTERNAL_LLM',
    }),
  });
}
export function getProfileAnalysis(id: number, signal: AbortSignal) {
  return personalRequest(
    `/api/v1/me/profile/analyses/${id}`,
    signal,
    isAnalysis,
  );
}
export async function waitForProfileAnalysis(
  id: number,
  signal: AbortSignal,
): Promise<ProfileAnalysis> {
  for (let attempt = 0; attempt < 40; attempt++) {
    const analysis = await getProfileAnalysis(id, signal);
    if (analysis.status === 'SUCCEEDED' || analysis.status === 'FAILED')
      return analysis;
    await abortableDelay(3000, signal);
  }
  throw new Error('POLLING_EXPIRED');
}
