import { useState } from 'react';
import { jobSources, sourceLabels } from './types';
import type { JobFilters as Filters } from './types';
import type { FormEvent } from 'react';

interface JobFiltersProps {
  filters: Filters;
  onApply: (filters: Filters) => void;
}

export function JobFilters({ filters, onApply }: JobFiltersProps) {
  const [draft, setDraft] = useState(filters);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onApply({ ...draft, page: 0 });
  }

  return (
    <form
      className="filter-form"
      onSubmit={submit}
      aria-label="공고 검색 및 필터"
    >
      <div className="search-field">
        <label htmlFor="keyword">어떤 일을 찾고 있나요?</label>
        <div className="search-row">
          <input
            id="keyword"
            name="keyword"
            type="search"
            maxLength={200}
            placeholder="공고명 또는 회사명 검색"
            value={draft.keyword}
            onChange={(event) =>
              setDraft({ ...draft, keyword: event.target.value })
            }
          />
          <button className="button-primary" type="submit">
            검색
          </button>
        </div>
      </div>
      <details className="filter-details">
        <summary>
          지역·직무·출처 필터 <span aria-hidden="true">⌄</span>
        </summary>
        <div className="filter-grid">
          <label htmlFor="region">
            지역
            <input
              id="region"
              name="region"
              maxLength={100}
              placeholder="예: 서울 (정확히 일치)"
              value={draft.region}
              onChange={(event) =>
                setDraft({ ...draft, region: event.target.value })
              }
            />
          </label>
          <label htmlFor="jobRole">
            직무
            <input
              id="jobRole"
              name="jobRole"
              maxLength={100}
              placeholder="예: BACKEND (정확히 일치)"
              value={draft.jobRole}
              onChange={(event) =>
                setDraft({ ...draft, jobRole: event.target.value })
              }
            />
          </label>
          <label htmlFor="source">
            출처
            <select
              id="source"
              name="source"
              value={draft.source}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  source:
                    jobSources.find(
                      (source) => source === event.target.value,
                    ) ?? '',
                })
              }
            >
              <option value="">전체 출처</option>
              {jobSources.map((source) => (
                <option key={source} value={source}>
                  {sourceLabels[source]}
                </option>
              ))}
            </select>
          </label>
          <label htmlFor="sort">
            정렬
            <select
              id="sort"
              name="sort"
              value={draft.sort}
              onChange={(event) =>
                setDraft({
                  ...draft,
                  sort:
                    event.target.value === 'DEADLINE' ? 'DEADLINE' : 'NEWEST',
                })
              }
            >
              <option value="NEWEST">최신순</option>
              <option value="DEADLINE">마감 임박순</option>
            </select>
          </label>
          <label htmlFor="size">
            페이지당 공고
            <select
              id="size"
              name="size"
              value={draft.size}
              onChange={(event) =>
                setDraft({ ...draft, size: Number(event.target.value) })
              }
            >
              <option value={20}>20개</option>
              <option value={50}>50개</option>
              <option value={100}>100개</option>
            </select>
          </label>
          <label className="checkbox-label" htmlFor="activeOnly">
            <input
              id="activeOnly"
              name="activeOnly"
              type="checkbox"
              checked={draft.activeOnly}
              onChange={(event) =>
                setDraft({ ...draft, activeOnly: event.target.checked })
              }
            />
            모집 중인 공고만
          </label>
        </div>
        <div className="filter-actions">
          <button
            className="button-quiet"
            type="button"
            onClick={() =>
              onApply({
                keyword: '',
                region: '',
                jobRole: '',
                source: '',
                activeOnly: true,
                sort: 'NEWEST',
                page: 0,
                size: 20,
              })
            }
          >
            초기화
          </button>
          <button className="button-primary" type="submit">
            필터 적용
          </button>
        </div>
      </details>
      {(filters.keyword ||
        filters.region ||
        filters.jobRole ||
        filters.source ||
        !filters.activeOnly ||
        filters.sort !== 'NEWEST') && (
        <p className="applied-filters">
          적용 중:{' '}
          {[
            filters.keyword && `검색 ${filters.keyword}`,
            filters.region && `지역 ${filters.region}`,
            filters.jobRole && `직무 ${filters.jobRole}`,
            filters.source && sourceLabels[filters.source],
            !filters.activeOnly && '마감 공고 포함',
            filters.sort === 'DEADLINE' && '마감 임박순',
          ]
            .filter(Boolean)
            .join(' · ')}
        </p>
      )}
    </form>
  );
}
