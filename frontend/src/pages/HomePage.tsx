import { useCallback, useEffect, useState } from 'react';
import type { MouseEvent } from 'react';
import { JobList } from '../features/jobs/JobList';
import { JobDetail } from '../features/jobs/JobDetail';
import { ProfilePage } from '../features/profile/ProfilePage';
import { Recommendations } from '../features/recommendations/Recommendations';

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
          <nav className="main-nav" aria-label="주요 메뉴">
            <a
              className="header-nav"
              href="/profile"
              onClick={(event) => onNavigate(event, '/profile')}
            >
              내 프로필
            </a>
            <a
              className="header-nav"
              href="/#jobs"
              onClick={(event) => onNavigate(event, '/#jobs')}
            >
              공고 탐색 <span aria-hidden="true">↗</span>
            </a>
          </nav>
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
                  <br />내 경험을 정리하고, 맞춤 기회를 찾아보세요.
                </p>
                <a className="hero-link" href="#jobs">
                  전체 공고 둘러보기 <span aria-hidden="true">↓</span>
                </a>
              </div>
              <aside className="recommendation-preview">
                <span className="coming-soon">내 경험에서 시작하는 추천</span>
                <h2>업로드와 AI 분석은 별개예요</h2>
                <p>
                  이력서를 저장한 뒤, 동의한 자료만 분석합니다. 결과를
                  검토·확정하면 나와 공고의 적합도를 비교할 수 있어요.
                </p>
                <a
                  className="hero-link"
                  href="/profile"
                  onClick={(event) => onNavigate(event, '/profile')}
                >
                  이력서·프로필 설정하기 →
                </a>
              </aside>
            </section>
            <Recommendations onNavigate={onNavigate} />
            <JobList
              key={location.search}
              search={location.search}
              navigate={navigate}
              onNavigate={onNavigate}
            />
          </main>
        ) : location.pathname === '/profile' ||
          location.pathname === '/setup' ? (
          <ProfilePage />
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
        <small>
          AI 적합도는 합격 확률이 아닙니다. 개인 기능은 localhost 전용입니다.
        </small>
      </footer>
    </>
  );
}
