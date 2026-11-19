import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { canUsePersonalApi, personalError } from '../../shared/api/localApi';
import {
  addLink,
  deleteDocument,
  deleteLink,
  emptyPreferences,
  emptyProfile,
  getConsent,
  getDocuments,
  getLinks,
  getPreferences,
  getProfile,
  getProfileAnalysis,
  profileKeys,
  saveConsent,
  savePreferences,
  saveProfile,
  startProfileAnalysis,
  uploadDocument,
  validateUpload,
  waitForProfileAnalysis,
} from './api';
import type {
  ConsentPurpose,
  DocumentItem,
  DocumentType,
  PortfolioLink,
  Preferences,
  ProfileAnalysis,
  ProfileContent,
  ProfileData,
} from './api';
import { ProfileEditor } from './ProfileEditor';
import { AiAvailability } from '../../shared/api/AiAvailability';

const documentLabels = {
  NOT_ANALYZED: '미분석',
  ANALYZING: '분석 중',
  ANALYZED: '분석 완료 · 프로필 확정 별도',
  FAILED: '분석 실패',
};
interface SetupData {
  documents: DocumentItem[];
  links: PortfolioLink[];
  profile: ProfileData;
  preferences: Preferences;
  purposes: ConsentPurpose[];
}

function rememberAnalysisId(id: number | null) {
  try {
    if (id === null) sessionStorage.removeItem('jobmate.profileAnalysisId');
    else sessionStorage.setItem('jobmate.profileAnalysisId', String(id));
  } catch {
    /* task ID 저장이 차단되어도 현재 화면은 동작한다. */
  }
}

export function ProfilePage() {
  const [data, setData] = useState<SetupData | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [file, setFile] = useState<File | null>(null);
  const [fileType, setFileType] = useState<DocumentType>('RESUME');
  const [linkUrl, setLinkUrl] = useState('');
  const [linkDescription, setLinkDescription] = useState('');
  const [selectedDocuments, setSelectedDocuments] = useState<number[]>([]);
  const [selectedLinks, setSelectedLinks] = useState<number[]>([]);
  const [purposes, setPurposes] = useState<ConsentPurpose[]>([]);
  const [preferences, setPreferences] = useState<Preferences>(emptyPreferences);
  const [editor, setEditor] = useState<ProfileContent>(emptyProfile);
  const [analysis, setAnalysis] = useState<ProfileAnalysis | null>(null);
  const [pendingDelete, setPendingDelete] = useState<{
    kind: 'document' | 'link';
    id: number;
  } | null>(null);
  const [profileReviewed, setProfileReviewed] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);
  const local = canUsePersonalApi();

  useEffect(() => {
    if (!local) return;
    const controller = new AbortController();
    Promise.all([
      getDocuments(controller.signal),
      getLinks(controller.signal),
      getProfile(controller.signal),
      getPreferences(controller.signal),
      getConsent(controller.signal),
    ])
      .then(([documents, links, profile, preferences, consent]) => {
        if (controller.signal.aborted) return;
        const activePurposes = consent.consented ? consent.purposes : [];
        setData({
          documents,
          links,
          profile,
          preferences,
          purposes: activePurposes,
        });
        setEditor(profile);
        setPreferences(preferences);
        setPurposes(activePurposes);
        setError('');
        try {
          const id = Number(
            sessionStorage.getItem('jobmate.profileAnalysisId'),
          );
          if (Number.isSafeInteger(id) && id > 0)
            void getProfileAnalysis(id, controller.signal)
              .then((analysis) => {
                if (controller.signal.aborted) return;
                setAnalysis(analysis);
                if (analysis.status === 'SUCCEEDED' && analysis.result) {
                  setEditor(analysis.result);
                  setProfileReviewed(false);
                  setNotice(
                    '저장된 분석 초안을 불러왔습니다. 다시 검토한 뒤 확정해 주세요.',
                  );
                }
              })
              .catch(() => {
                if (!controller.signal.aborted) rememberAnalysisId(null);
              });
        } catch {
          /* 원문·프로필은 브라우저 저장소에 저장하지 않는다. */
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setError(personalError(error));
      });
    return () => controller.abort();
  }, [loadAttempt, local]);

  useEffect(() => () => controllerRef.current?.abort(), []);

  async function perform(action: (signal: AbortSignal) => Promise<void>) {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await action(controller.signal);
    } catch (error: unknown) {
      if (!controller.signal.aborted)
        setError(
          error instanceof Error && error.message === 'POLLING_EXPIRED'
            ? '분석이 아직 진행 중입니다. 자동 상태 조회를 멈췄습니다. 진행 상태 다시 확인 버튼을 사용해 주세요.'
            : personalError(error),
        );
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  async function refreshDocuments(signal: AbortSignal) {
    const documents = await getDocuments(signal);
    if (!signal.aborted)
      setData((previous) => (previous ? { ...previous, documents } : previous));
  }

  function submitUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    if (!file) {
      setError('업로드할 파일을 선택해 주세요.');
      return;
    }
    const validation = validateUpload(file);
    if (validation) {
      setError(validation);
      return;
    }
    void perform(async (signal) => {
      await uploadDocument(file, fileType, signal);
      await refreshDocuments(signal);
      setFile(null);
      form.reset();
      setNotice(
        '파일을 저장했습니다. AI는 호출하지 않았습니다. 분석할 문서를 직접 선택해 주세요.',
      );
      setSelectedDocuments([]);
    });
  }

  async function finishAnalysis(result: ProfileAnalysis, signal: AbortSignal) {
    setAnalysis(result);
    if (result.status === 'SUCCEEDED' && result.result) {
      setEditor(result.result);
      setProfileReviewed(false);
      setNotice(
        'AI 초안을 불러왔습니다. 내용과 출처를 수정·검토한 뒤 직접 확정해 주세요.',
      );
    }
    if (result.status === 'FAILED')
      setError(
        `프로필 분석에 실패했습니다. 원본은 유지됩니다. ${result.errorCode ? `오류 코드: ${result.errorCode}` : ''}`,
      );
    await refreshDocuments(signal);
  }

  function analyze() {
    void perform(async (signal) => {
      const task = await startProfileAnalysis(
        selectedDocuments,
        selectedLinks,
        signal,
      );
      setAnalysis(task);
      rememberAnalysisId(task.analysisId);
      await finishAnalysis(
        task.status === 'SUCCEEDED' || task.status === 'FAILED'
          ? task
          : await waitForProfileAnalysis(task.analysisId, signal),
        signal,
      );
    });
  }

  function togglePurpose(purpose: ConsentPurpose) {
    setPurposes((previous) =>
      previous.includes(purpose)
        ? previous.filter((item) => item !== purpose)
        : [...previous, purpose],
    );
  }
  function toggleId(id: number, previous: number[]) {
    return previous.includes(id)
      ? previous.filter((item) => item !== id)
      : [...previous, id];
  }

  if (!local)
    return (
      <main className="message-panel">
        <h1>개인정보 기능은 localhost 전용입니다</h1>
        <p>
          로그인 도입 전에는 이 컴퓨터의 localhost 또는 127.0.0.1에서만 사용해
          주세요. 공개 주소에서는 이력서·프로필 API를 요청하지 않습니다.
        </p>
      </main>
    );
  if (!data)
    return (
      <main className="profile-page">
        <h1>내 프로필 설정</h1>
        {error ? (
          <div className="message-panel" role="alert">
            <p>{error}</p>
            <button
              className="button-primary"
              onClick={() => {
                setError('');
                setLoadAttempt((attempt) => attempt + 1);
              }}
            >
              다시 불러오기
            </button>
          </div>
        ) : (
          <p role="status">프로필 설정을 불러오는 중입니다.</p>
        )}
      </main>
    );

  return (
    <main className="profile-page">
      <header className="profile-heading">
        <p className="eyebrow">MY EXPERIENCE</p>
        <h1>
          경험을 정리하고,
          <br />
          다음 기회를 연결하세요.
        </h1>
        <p>파일 저장 → 명시적 AI 분석 → 초안 검토 → 프로필 확정</p>
        <span className="badge">
          로컬 단일 사용자 · 개인정보 외부 공개 금지
        </span>
      </header>
      {error && (
        <div className="inline-notice error-notice" role="alert">
          {error}
        </div>
      )}
      {notice && (
        <div className="inline-notice" role="status">
          {notice}
        </div>
      )}
      <section className="setup-section">
        <h2>1. 이력서와 포트폴리오</h2>
        <p className="form-help">
          PDF·DOCX, 파일당 최대 10 MiB. 파일 업로드만으로 AI를 호출하지
          않습니다. 새 이력서는 활성 이력서를 교체하지만 기존 확정 프로필은 새
          내용을 검토·확정하기 전까지 유지됩니다.
        </p>
        <form onSubmit={submitUpload} className="stack-form">
          <label>
            문서 종류
            <select
              value={fileType}
              onChange={(event) =>
                setFileType(
                  event.target.value === 'PORTFOLIO' ? 'PORTFOLIO' : 'RESUME',
                )
              }
              disabled={busy}
            >
              <option value="RESUME">이력서 (필수)</option>
              <option value="PORTFOLIO">포트폴리오 (선택)</option>
            </select>
          </label>
          <label>
            PDF 또는 DOCX 파일
            <input
              type="file"
              accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
              disabled={busy}
              onChange={(event) => {
                const next = event.target.files?.[0] ?? null;
                setFile(next);
                if (next) setError(validateUpload(next) ?? '');
              }}
            />
          </label>
          <button
            className="button-primary"
            disabled={busy || !file}
            type="submit"
          >
            파일 저장 · AI 호출 없음
          </button>
        </form>
        <div className="document-list">
          {data.documents.length === 0 ? (
            <p className="muted">아직 등록한 문서가 없습니다.</p>
          ) : (
            data.documents.map((document) => (
              <article className="document-item" key={document.documentId}>
                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={selectedDocuments.includes(document.documentId)}
                    onChange={() =>
                      setSelectedDocuments((previous) =>
                        toggleId(document.documentId, previous),
                      )
                    }
                    disabled={busy}
                  />
                  <span>
                    {document.type === 'RESUME' ? '이력서' : '포트폴리오'} ·{' '}
                    {document.fileName}
                  </span>
                </label>
                <div className="document-meta">
                  <span className="badge">
                    {documentLabels[document.analysisStatus]}
                  </span>
                  <button
                    className="button-quiet"
                    disabled={busy}
                    onClick={() =>
                      setPendingDelete({
                        kind: 'document',
                        id: document.documentId,
                      })
                    }
                  >
                    문서 삭제
                  </button>
                </div>
              </article>
            ))
          )}
        </div>
        <details className="profile-details">
          <summary>선택: GitHub 프로젝트 링크 등록</summary>
          <p className="form-help">
            공개 GitHub 프로젝트의 README만 분석 대상으로 선택할 수 있습니다.
            링크 등록만으로 가져오거나 AI에 전송하지 않습니다. 전체 계정·비공개
            저장소는 조회하지 않습니다.
          </p>
          <form
            className="stack-form"
            onSubmit={(event) => {
              event.preventDefault();
              void perform(async (signal) => {
                await addLink(linkUrl, linkDescription, signal);
                const links = await getLinks(signal);
                setData((previous) =>
                  previous ? { ...previous, links } : previous,
                );
                setLinkUrl('');
                setLinkDescription('');
                setNotice(
                  '링크를 등록했습니다. AI 분석은 실행하지 않았습니다.',
                );
              });
            }}
          >
            <label>
              공개 프로젝트 URL
              <input
                type="url"
                required
                placeholder="https://github.com/owner/repo"
                value={linkUrl}
                onChange={(event) => setLinkUrl(event.target.value)}
                disabled={busy}
              />
            </label>
            <label>
              프로젝트 설명 (선택)
              <input
                value={linkDescription}
                maxLength={1000}
                onChange={(event) => setLinkDescription(event.target.value)}
                disabled={busy}
              />
            </label>
            <button className="button-quiet" disabled={busy}>
              링크 등록
            </button>
          </form>
          {data.links.map((link) => (
            <article className="document-item" key={link.linkId}>
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={selectedLinks.includes(link.linkId)}
                  onChange={() =>
                    setSelectedLinks((previous) =>
                      toggleId(link.linkId, previous),
                    )
                  }
                  disabled={busy}
                />
                <span>{link.url}</span>
              </label>
              <button
                className="button-quiet"
                disabled={busy}
                onClick={() =>
                  setPendingDelete({ kind: 'link', id: link.linkId })
                }
              >
                링크 삭제
              </button>
            </article>
          ))}
        </details>
        {pendingDelete && (
          <div className="delete-confirm" role="alert">
            <p>
              선택한{' '}
              {pendingDelete.kind === 'document'
                ? '문서의 원본·추출문·분석과 연결된 프로필 데이터'
                : '링크와 연결된 분석 데이터'}
              를 삭제합니다. 삭제한 자료는 다시 사용할 수 없습니다.
            </p>
            <div className="form-actions">
              <button
                className="button-quiet"
                disabled={busy}
                onClick={() => setPendingDelete(null)}
              >
                취소
              </button>
              <button
                className="button-primary"
                disabled={busy}
                onClick={() =>
                  void perform(async (signal) => {
                    if (pendingDelete.kind === 'document')
                      await deleteDocument(pendingDelete.id, signal);
                    else await deleteLink(pendingDelete.id, signal);
                    setPendingDelete(null);
                    rememberAnalysisId(null);
                    setAnalysis(null);
                    setSelectedDocuments([]);
                    setSelectedLinks([]);
                    setLoadAttempt((attempt) => attempt + 1);
                    setNotice(
                      '선택한 자료를 삭제했습니다. 프로필 상태를 다시 확인해 주세요.',
                    );
                  })
                }
              >
                삭제 확인
              </button>
            </div>
          </div>
        )}
      </section>
      <section className="setup-section">
        <h2>2. 외부 AI 전송 동의</h2>
        <p className="form-help">
          공급자: OpenAI. 프로필 분석에는 선택한 문서·공개 프로젝트의 개인정보를
          최소화한 내용이 전송됩니다. 공고 적합도에는 확정 프로필과 허용된 채용
          요건이 전송됩니다. 각 목적에 따로 동의할 수 있으며 철회하면 이후 해당
          AI 요청을 차단합니다. 저장된 결과의 삭제는 문서 삭제로 별도
          관리합니다. 원본은 비공개 저장소에, 분석 초안·확정 프로필과 추천
          결과는 서버 DB에 보관합니다.
        </p>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={purposes.includes('PROFILE_EXTRACTION')}
            onChange={() => togglePurpose('PROFILE_EXTRACTION')}
            disabled={busy}
          />
          문서·프로젝트를 OpenAI에 전송하여 프로필을 추출하는 데 동의합니다.
        </label>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={purposes.includes('JOB_MATCHING')}
            onChange={() => togglePurpose('JOB_MATCHING')}
            disabled={busy}
          />
          확정 프로필·공고를 OpenAI에 전송하여 적합도를 비교하는 데 동의합니다.
        </label>
        <button
          className="button-quiet"
          disabled={busy}
          onClick={() =>
            void perform(async (signal) => {
              const consent = await saveConsent(purposes, signal);
              setData((previous) =>
                previous
                  ? {
                      ...previous,
                      purposes: consent.consented ? consent.purposes : [],
                    }
                  : previous,
              );
              setNotice(
                '분석 목적별 동의를 저장했습니다. AI는 호출하지 않았습니다.',
              );
            })
          }
        >
          동의 설정 저장
        </button>
      </section>
      <section className="setup-section">
        <h2>3. 선택한 자료 AI 분석</h2>
        <AiAvailability />
        <p className="form-help">
          이력서를 포함해 분석할 자료를 위에서 선택해 주세요. 먼저 동의 설정을
          저장해야 합니다. 분석은 이 버튼을 눌렀을 때만 시작하며 기존 확정
          프로필을 자동 변경하지 않습니다.
        </p>
        <button
          className="button-primary"
          disabled={
            busy ||
            !data.purposes.includes('PROFILE_EXTRACTION') ||
            !data.documents.some(
              (document) =>
                document.type === 'RESUME' &&
                selectedDocuments.includes(document.documentId),
            )
          }
          onClick={analyze}
        >
          {busy && analysis && ['PENDING', 'RUNNING'].includes(analysis.status)
            ? '분석 상태 확인 중…'
            : '선택 자료 AI 분석하기'}
        </button>
        {analysis && (
          <p role="status" className="form-help">
            분석 #{analysis.analysisId} · {analysis.status}
          </p>
        )}
        {analysis &&
          ['PENDING', 'RUNNING'].includes(analysis.status) &&
          !busy && (
            <button
              className="button-quiet"
              onClick={() =>
                void perform(async (signal) => {
                  await finishAnalysis(
                    await getProfileAnalysis(analysis.analysisId, signal),
                    signal,
                  );
                })
              }
            >
              진행 상태 다시 확인
            </button>
          )}
      </section>
      <section className="setup-section">
        <h2>4. 검토·수정하고 프로필 확정</h2>
        <p className="form-help">
          현재 서버 프로필: {data.profile.confirmed ? '확정됨' : '미확정'} ·
          버전 {data.profile.profileVersion}. AI 결과는 초안입니다.
          실무·프로젝트·학습을 구분하고 개인정보·추측·잘못된 근거를 수정해
          주세요. 직접 입력도 가능합니다.
        </p>
        <ProfileEditor
          value={editor}
          onChange={(next) => {
            setEditor(next);
            setProfileReviewed(false);
          }}
          disabled={busy}
        />
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={profileReviewed}
            onChange={(event) => setProfileReviewed(event.target.checked)}
            disabled={busy}
          />
          내용·경험 구분·출처를 검토했으며 이 내용을 추천 기준으로 확정합니다.
        </label>
        <button
          className="button-primary"
          disabled={busy || !profileReviewed}
          onClick={() =>
            void perform(async (signal) => {
              if (
                profileKeys.some((key) =>
                  editor[key].some((entry) => !entry.name.trim()),
                )
              ) {
                setError(
                  '프로필 항목의 이름을 입력하거나 빈 항목을 삭제해 주세요.',
                );
                return;
              }
              const profile = await saveProfile(
                {
                  ...editor,
                  profileVersion: data.profile.profileVersion,
                  confirmed: true,
                },
                signal,
              );
              setData((previous) =>
                previous ? { ...previous, profile } : previous,
              );
              setEditor(profile);
              rememberAnalysisId(null);
              setAnalysis(null);
              setProfileReviewed(false);
              setNotice(
                `프로필 버전 ${profile.profileVersion}을 확정했습니다.`,
              );
            })
          }
        >
          검토한 프로필 확정
        </button>
      </section>
      <section className="setup-section">
        <h2>5. 희망 조건 (모두 선택)</h2>
        <p className="form-help">
          쉼표로 구분해 입력하세요. 비워 두면 해당 조건으로 공고를 제한하지
          않습니다. 공고 데이터와 같은 표기(예: BACKEND, 서울)를 사용해 주세요.
        </p>
        <form
          className="stack-form"
          onSubmit={(event) => {
            event.preventDefault();
            void perform(async (signal) => {
              const saved = await savePreferences(preferences, signal);
              const profile = await getProfile(signal);
              setPreferences(saved);
              setData((previous) =>
                previous
                  ? { ...previous, profile, preferences: saved }
                  : previous,
              );
              setNotice('희망 조건을 저장했습니다.');
            });
          }}
        >
          <label>
            희망 직무
            <input
              value={preferences.desiredRoles.join(', ')}
              onChange={(event) =>
                setPreferences({
                  ...preferences,
                  desiredRoles: event.target.value
                    .split(',')
                    .map((value) => value.trim()),
                })
              }
              disabled={busy}
            />
          </label>
          <label>
            희망 지역
            <input
              value={preferences.regions.join(', ')}
              onChange={(event) =>
                setPreferences({
                  ...preferences,
                  regions: event.target.value
                    .split(',')
                    .map((value) => value.trim()),
                })
              }
              disabled={busy}
            />
          </label>
          <label>
            희망 고용 형태
            <input
              placeholder="예: FULL_TIME, CONTRACT"
              value={preferences.employmentTypes.join(', ')}
              onChange={(event) =>
                setPreferences({
                  ...preferences,
                  employmentTypes: event.target.value
                    .split(',')
                    .map((value) => value.trim()),
                })
              }
              disabled={busy}
            />
          </label>
          <label>
            희망 최소 연봉 (원, KRW)
            <input
              type="number"
              inputMode="numeric"
              min={0}
              max={Number.MAX_SAFE_INTEGER}
              step={1}
              placeholder="예: 40000000 · 미입력 시 제한 없음"
              value={preferences.minSalary ?? ''}
              onChange={(event) => {
                const amount = event.currentTarget.valueAsNumber;
                setPreferences({
                  ...preferences,
                  minSalary:
                    event.currentTarget.value === ''
                      ? null
                      : Number.isFinite(amount)
                        ? amount
                        : preferences.minSalary,
                });
              }}
              disabled={busy}
            />
          </label>
          <div className="preference-options">
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={preferences.avoidDispatch}
                onChange={(event) =>
                  setPreferences({
                    ...preferences,
                    avoidDispatch: event.target.checked,
                  })
                }
                disabled={busy}
              />
              파견 근무를 피하고 싶어요
            </label>
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={preferences.avoidFrequentTravel}
                onChange={(event) =>
                  setPreferences({
                    ...preferences,
                    avoidFrequentTravel: event.target.checked,
                  })
                }
                disabled={busy}
              />
              잦은 출장을 피하고 싶어요
            </label>
          </div>
          <button className="button-quiet" disabled={busy}>
            희망 조건 저장
          </button>
        </form>
      </section>
    </main>
  );
}
