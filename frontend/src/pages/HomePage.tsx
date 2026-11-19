import { useCallback, useEffect, useState } from 'react';
import type { MouseEvent } from 'react';
import { JobList } from '../features/jobs/JobList';
import { JobDetail } from '../features/jobs/JobDetail';

function currentLocation() {
  return {
    pathname: window.location.pathname,
    search: window.location.search,
    hash: window.location.hash,
  };
}

export function HomePage() {
  const [location, setLocation] = useState(currentLocation);
  const navigate = useCallback((href: string) => {
    window.history.pushState(null, '', href);
    setLocation(currentLocation());
    window.scrollTo({ top: 0 });
  }, []);

  useEffect(() => {
    function onPopState() {
      setLocation(currentLocation());
    }
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  useEffect(() => {
    if (location.hash === '#jobs')
      document.getElementById('jobs')?.scrollIntoView();
  }, [location]);

  function onNavigate(event: MouseEvent<HTMLAnchorElement>, href: string) {
    if (
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey
    )
      return;
    event.preventDefault();
    navigate(href);
  }

  const detailMatch = /^\/jobs\/([1-9]\d*)\/?$/.exec(location.pathname);
  const jobId = detailMatch ? Number(detailMatch[1]) : null;
  const isHome = location.pathname === '/';

  return (
    <>
      <a className="skip-link" href="#main-content">
        본문 바로가기
      </a>
      <header className="site-header">
        <div className="header-inner">
          <a
            className="brand"
            href="/"
            onClick={(event) => onNavigate(event, '/')}
            aria-label="JobMate 홈"
          >
            <span className="brand-mark" aria-hidden="true">
              j.
            </span>
            JobMate
          </a>
          <a
            className="header-nav"
            href="/#jobs"
            onClick={(event) => onNavigate(event, '/#jobs')}
          >
            공고 탐색 <span aria-hidden="true">↗</span>
          </a>
        </div>
      </header>
      <div id="main-content" className="page-shell">
        {isHome ? (
          <main>
            <section
              className="recommendation-intro"
              aria-labelledby="home-title"
            >
              <div className="hero-copy">
                <p className="hero-eyebrow">
                  <span aria-hidden="true">✦</span> YOUR NEXT CHAPTER
                </p>
                <h1 id="home-title">
                  나의 다음 기회,
                  <br />
                  <span>더 나답게.</span>
                </h1>
                <p className="hero-description">
                  경험과 가능성을 연결하는 채용 탐색.
                  <br />
                  먼저 공고를 둘러보고, 다음 기회를 찾아보세요.
                </p>
                <a className="hero-link" href="#jobs">
                  전체 공고 둘러보기 <span aria-hidden="true">↓</span>
                </a>
              </div>
              <aside className="recommendation-preview">
                <span className="coming-soon">준비 중</span>
                <h2>나를 이해하는 맞춤 추천</h2>
                <p>
                  이력서·프로필을 바탕으로 한 추천과 AI 적합도 분석은 후속
                  기능입니다.
                </p>
                <div className="honest-state">
                  <span aria-hidden="true">○</span>
                  <div>
                    <strong>현재는 미분석 상태</strong>
                    <span>점수 없이, 공고 원문부터 탐색하세요.</span>
                  </div>
                </div>
              </aside>
            </section>
            <JobList
              key={location.search}
              search={location.search}
              navigate={navigate}
              onNavigate={onNavigate}
            />
          </main>
        ) : jobId !== null && Number.isSafeInteger(jobId) ? (
          <JobDetail
            key={jobId}
            jobId={jobId}
            search={location.search}
            onNavigate={onNavigate}
          />
        ) : (
          <main className="message-panel">
            <h1>페이지를 찾을 수 없어요</h1>
            <a
              className="button-primary"
              href="/"
              onClick={(event) => onNavigate(event, '/')}
            >
              공고 탐색으로 돌아가기
            </a>
          </main>
        )}
      </div>
      <footer className="site-footer">
        <span className="footer-brand">JobMate</span>
        <p>나의 경험에서, 다음 기회로.</p>
        <small>공고 자동 수집·프로필·AI 추천·읽음 관리는 준비 중입니다.</small>
      </footer>
    </>
  );
}
