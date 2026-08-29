'use client';

import Link from 'next/link';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type MouseEvent as ReactMouseEvent,
} from 'react';

type Category = 'all' | 'food' | 'travel';
type ViewMode = 'list' | 'map';

type Post = {
  id: number;
  category: Exclude<Category, 'all'>;
  city: string;
  country: string;
  title: string;
  note: string;
  date: string;
  meta: string;
  photoCount: number;
  palette: string;
  tag: string;
};

const posts: Post[] = [
  { id: 1, category: 'food', city: 'Kyoto', country: 'Japan', title: '기온의 늦은 점심', note: '비를 피해 들어간 작은 식당에서 만난 따뜻한 한 끼.', date: '2026년 4월', meta: '★ 4.8 · 재방문하고 싶어요', photoCount: 8, palette: 'meal-one', tag: '오코노미야키' },
  { id: 2, category: 'travel', city: 'Kyoto', country: 'Japan', title: '비 그친 뒤의 겐닌지', note: '젖은 돌길과 조용한 정원이 오래 기억에 남았다.', date: '2026년 4월', meta: '약 1시간 · 오전 추천', photoCount: 14, palette: 'place-one', tag: '사찰 · 정원' },
  { id: 3, category: 'food', city: 'Osaka', country: 'Japan', title: '시장 골목의 타코야키', note: '뜨거운 김 사이로 시작된 오사카의 첫 저녁.', date: '2026년 4월', meta: '★ 4.5 · 1,000엔 이하', photoCount: 5, palette: 'meal-two', tag: '길거리 음식' },
  { id: 4, category: 'travel', city: 'Nara', country: 'Japan', title: '아침의 나라 공원', note: '사람이 붐비기 전, 햇빛과 사슴만 있던 시간.', date: '2026년 4월', meta: '약 2시간 · 이른 아침 추천', photoCount: 21, palette: 'place-two', tag: '공원 · 산책' },
  { id: 5, category: 'food', city: 'Seoul', country: 'Korea', title: '을지로의 오래된 냉면집', note: '담백한 육수와 오래된 공간이 함께 남은 곳.', date: '2025년 8월', meta: '★ 4.7 · 대기 20분', photoCount: 7, palette: 'meal-three', tag: '냉면' },
  { id: 6, category: 'travel', city: 'Busan', country: 'Korea', title: '해 질 무렵의 흰여울', note: '바다를 따라 걷다 마주한 늦여름의 주황빛.', date: '2025년 7월', meta: '약 90분 · 일몰 추천', photoCount: 18, palette: 'place-three', tag: '해안 · 산책' },
];

const CATEGORIES: Category[] = ['all', 'food', 'travel'];

export default function Home() {
  const [category, setCategory] = useState<Category>('all');
  const [view, setView] = useState<ViewMode>('list');
  const [selected, setSelected] = useState<Post | null>(null);
  const dialogTriggerRef = useRef<HTMLButtonElement | null>(null);

  const filtered = useMemo(() => category === 'all' ? posts : posts.filter((post) => post.category === category), [category]);
  const counts = { all: posts.length, food: posts.filter((post) => post.category === 'food').length, travel: posts.filter((post) => post.category === 'travel').length };

  const handleOpenPreview = (post: Post, trigger: HTMLButtonElement) => {
    dialogTriggerRef.current = trigger;
    setSelected(post);
  };

  const handleClosePreview = useCallback(() => {
    setSelected(null);
  }, []);

  useEffect(() => {
    if (!selected) dialogTriggerRef.current?.focus();
  }, [selected]);

  const handleCategoryKeyDown = (event: ReactKeyboardEvent<HTMLButtonElement>, current: Category) => {
    const currentIndex = CATEGORIES.indexOf(current);
    let nextIndex: number | null = null;
    if (event.key === 'ArrowRight') nextIndex = (currentIndex + 1) % CATEGORIES.length;
    if (event.key === 'ArrowLeft') nextIndex = (currentIndex - 1 + CATEGORIES.length) % CATEGORIES.length;
    if (event.key === 'Home') nextIndex = 0;
    if (event.key === 'End') nextIndex = CATEGORIES.length - 1;
    if (nextIndex === null) return;
    event.preventDefault();
    const nextCategory = CATEGORIES[nextIndex];
    setCategory(nextCategory);
    document.getElementById(`archive-category-${nextCategory}`)?.focus();
  };

  return (
    <>
      <section className="hero" id="top">
        <div className="hero-copy">
          <p className="overline">MY TRAVEL &amp; DINING ARCHIVE</p>
          <h1>먹고, 걷고,<br />오래 기억하는 곳들.</h1>
          <p className="hero-description">여행에서 만난 풍경과 한 끼를 사진, 지도 그리고 그날의 문장으로 남깁니다.</p>
          <div className="hero-stats" aria-label="기록 통계"><span><b>42</b>places</span><span><b>18</b>plates</span><span><b>7</b>cities</span></div>
        </div>
        <div className="hero-collage" role="img" aria-label="교토와 부산의 최근 여행 및 맛집 기록을 표현한 사진 모음">
          <div className="collage-main meal-one"><span className="watermark">Places &amp; Plates</span></div>
          <div className="collage-small place-three"><span>BUSAN</span></div>
          <div className="collage-small place-one"><span>KYOTO</span></div>
          <div className="collage-caption"><b>Spring in Kyoto</b><small>2026 · 3박 4일 · 12개 장소</small></div>
        </div>
      </section>

      <section className="archive" id="archive">
        <div className="archive-heading"><div><p className="overline">BROWSE THE ARCHIVE</p><h2>나의 장소</h2></div><p>한 장면씩 천천히 보거나,<br />지도 위에서 한눈에 둘러보세요.</p></div>
        <div className="toolbar">
          <div className="category-tabs" role="tablist" aria-label="게시물 카테고리">
            <FilterTab category="all" label="전체" count={counts.all} active={category === 'all'} onClick={() => setCategory('all')} onKeyDown={handleCategoryKeyDown} />
            <FilterTab category="food" label="맛집" count={counts.food} active={category === 'food'} onClick={() => setCategory('food')} onKeyDown={handleCategoryKeyDown} icon="●" tone="food" />
            <FilterTab category="travel" label="여행지" count={counts.travel} active={category === 'travel'} onClick={() => setCategory('travel')} onKeyDown={handleCategoryKeyDown} icon="●" tone="travel" />
          </div>
          <div className="view-tabs" role="group" aria-label="보기 방식"><button className={view === 'list' ? 'active' : ''} aria-pressed={view === 'list'} onClick={() => setView('list')} type="button"><span aria-hidden="true">☷</span> <span>리스트</span></button><button className={view === 'map' ? 'active' : ''} aria-pressed={view === 'map'} onClick={() => setView('map')} type="button"><span aria-hidden="true">⌖</span> <span>지도</span></button></div>
        </div>

        <div
          id="archive-category-panel"
          className={`explore-grid ${view === 'map' ? 'map-focus' : ''}`}
          role="tabpanel"
          aria-labelledby={`archive-category-${category}`}
        >
          <section className="post-grid" aria-label="게시물 목록">
            {filtered.map((post) => (
              <article className="post-card" key={post.id}>
                <div className={`post-photo ${post.palette}`}><span className={`category-badge ${post.category}`}>{post.category === 'food' ? '맛집' : '여행지'}</span><span className="photo-count">▣ {post.photoCount}</span><span className="watermark">Places &amp; Plates</span></div>
                <div className="post-content"><p className="location">{post.city} · {post.country}</p><h3>{post.title}</h3><p className="post-note">{post.note}</p><div className="post-meta"><span>{post.date}</span><span>{post.meta}</span></div></div>
                <button
                  className="post-card-open"
                  type="button"
                  aria-haspopup="dialog"
                  aria-label={`${post.title} 기록 미리보기 열기`}
                  onClick={(event) => handleOpenPreview(post, event.currentTarget)}
                  onKeyDown={(event) => {
                    if (event.key !== 'Enter' && event.key !== ' ') return;
                    event.preventDefault();
                    handleOpenPreview(post, event.currentTarget);
                  }}
                />
              </article>
            ))}
          </section>

          <aside className="map-card" id="map-panel" aria-label="게시물 지도 목업">
            <div className="map-topline"><span><b>현재 지도 영역</b> {filtered.length}개</span><Link href="/map">↗ 크게 보기</Link></div>
            <div className="mock-map"><span className="district d1">KYOTO</span><span className="district d2">OSAKA</span><span className="district d3">NARA</span><button className="cluster food c1" type="button" aria-label={`맛집 기록 ${category === 'travel' ? 0 : 5}개 묶음`}>{category === 'travel' ? 0 : 5}</button><button className="cluster travel c2" type="button" aria-label={`여행지 기록 ${category === 'food' ? 0 : 12}개 묶음`}>{category === 'food' ? 0 : 12}</button><button className="cluster mixed c3" type="button" aria-label={`전체 기록 ${filtered.length}개 묶음`}>{filtered.length}</button><button className="pin p1" type="button" aria-label="교토 기록 지도에서 선택">●</button><button className="pin p2" type="button" aria-label="오사카 기록 지도에서 선택">●</button></div>
            <div className="map-legend"><span><i className="food-dot" />맛집 {category === 'travel' ? 0 : counts.food}</span><span><i className="travel-dot" />여행지 {category === 'food' ? 0 : counts.travel}</span></div>
            <p className="privacy-note">촬영 메타데이터는 제거되며 업로드 원본은 서버에 보관되지 않습니다.</p>
          </aside>
        </div>
      </section>

      <section className="journey-strip" id="journeys"><div><p className="overline">FEATURED JOURNEY</p><h2>Kyoto, Spring 2026</h2><p>비가 자주 내렸고, 그래서 더 천천히 걸었던 나흘.</p></div><Link href="/posts?category=DESTINATION">여행 기록 보기 <span aria-hidden="true">→</span></Link></section>

      {selected && (
        <PostPreviewDialog
          post={selected}
          onClose={handleClosePreview}
        />
      )}
    </>
  );
}

function FilterTab({ category, label, count, active, onClick, onKeyDown, icon, tone }: { category: Category; label: string; count: number; active: boolean; onClick: () => void; onKeyDown: (event: ReactKeyboardEvent<HTMLButtonElement>, category: Category) => void; icon?: string; tone?: string }) {
  return <button id={`archive-category-${category}`} className={active ? 'active' : ''} role="tab" aria-selected={active} aria-controls="archive-category-panel" tabIndex={active ? 0 : -1} onClick={onClick} onKeyDown={(event) => onKeyDown(event, category)} type="button">{icon && <i className={tone} aria-hidden="true">{icon}</i>}{label}<span>{count}</span></button>;
}

function PostPreviewDialog({ post, onClose }: { post: Post; onClose: () => void }) {
  const dialogRef = useRef<HTMLElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    const backdrop = dialogRef.current?.closest('.detail-backdrop');
    const main = dialogRef.current?.closest('main');
    const backgroundElements = [
      document.querySelector<HTMLElement>('.topbar'),
      document.querySelector<HTMLElement>('.site-footer'),
      ...Array.from(main?.children ?? []).filter(
        (element): element is HTMLElement => element instanceof HTMLElement && element !== backdrop,
      ),
    ].filter((element): element is HTMLElement => Boolean(element));
    const backgroundStates = backgroundElements.map((element) => ({
      element,
      inert: element.inert,
      ariaHidden: element.getAttribute('aria-hidden'),
    }));
    document.body.style.overflow = 'hidden';
    backgroundElements.forEach((element) => {
      element.inert = true;
      element.setAttribute('aria-hidden', 'true');
    });
    closeButtonRef.current?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const focusableElements = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      ));
      if (focusableElements.length === 0) return;
      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];
      if (event.shiftKey && document.activeElement === firstElement) {
        event.preventDefault();
        lastElement.focus();
      } else if (!event.shiftKey && document.activeElement === lastElement) {
        event.preventDefault();
        firstElement.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
      backgroundStates.forEach(({ element, inert, ariaHidden }) => {
        element.inert = inert;
        if (ariaHidden === null) element.removeAttribute('aria-hidden');
        else element.setAttribute('aria-hidden', ariaHidden);
      });
    };
  }, [onClose]);

  const handleBackdropMouseDown = (event: ReactMouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) onClose();
  };

  return (
    <div className="detail-backdrop" onMouseDown={handleBackdropMouseDown}>
      <article
        ref={dialogRef}
        className="detail-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby={`post-preview-title-${post.id}`}
      >
        <button ref={closeButtonRef} className="detail-close" type="button" onClick={onClose} aria-label={`${post.title} 미리보기 닫기`}>×</button>
        <div className={`detail-photo ${post.palette}`} role="img" aria-label={`${post.title} 대표 이미지 목업`}><span className="watermark">Places &amp; Plates</span></div>
        <div className="detail-body"><p className="overline">{post.category === 'food' ? 'PLATE' : 'PLACE'} · {post.city.toUpperCase()}</p><h2 id={`post-preview-title-${post.id}`}>{post.title}</h2><p className="detail-lead">{post.note}</p><div className="detail-facts"><span><small>방문 시기</small>{post.date}</span><span><small>기록</small>{post.meta}</span><span><small>태그</small>{post.tag}</span></div><p className="detail-story">그날의 공기와 소리를 잊지 않으려고 사진 몇 장과 짧은 문장을 남겼습니다. 장소 정보는 지도와 연결되지만 촬영 위치와 카메라 정보는 제거되며, 업로드 원본은 서버에 보관되지 않습니다.</p><Link className="primary-button" href="/posts">전체 공개 기록 읽기 <span aria-hidden="true">→</span></Link></div>
      </article>
    </div>
  );
}
