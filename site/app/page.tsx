'use client';

import { useMemo, useState } from 'react';

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

export default function Home() {
  const [category, setCategory] = useState<Category>('all');
  const [view, setView] = useState<ViewMode>('list');
  const [selected, setSelected] = useState<Post | null>(null);

  const filtered = useMemo(() => category === 'all' ? posts : posts.filter((post) => post.category === category), [category]);
  const counts = { all: posts.length, food: posts.filter((post) => post.category === 'food').length, travel: posts.filter((post) => post.category === 'travel').length };

  return (
    <main className="site-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="Places and Plates 홈"><span className="brand-mark">P</span><span>Places <i>&amp;</i> Plates</span></a>
        <nav className="main-nav" aria-label="주요 메뉴"><a className="active" href="#archive">기록</a><a href="#map-panel">지도</a><a href="#journeys">여행</a></nav>
        <button className="round-button" type="button" aria-label="검색">⌕</button>
      </header>

      <section className="hero" id="top">
        <div className="hero-copy">
          <p className="overline">MY TRAVEL &amp; DINING ARCHIVE</p>
          <h1>먹고, 걷고,<br />오래 기억하는 곳들.</h1>
          <p className="hero-description">여행에서 만난 풍경과 한 끼를 사진, 지도 그리고 그날의 문장으로 남깁니다.</p>
          <div className="hero-stats" aria-label="기록 통계"><span><b>42</b>places</span><span><b>18</b>plates</span><span><b>7</b>cities</span></div>
        </div>
        <div className="hero-collage" aria-label="최근 기록 사진 모음">
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
            <FilterTab label="전체" count={counts.all} active={category === 'all'} onClick={() => setCategory('all')} />
            <FilterTab label="맛집" count={counts.food} active={category === 'food'} onClick={() => setCategory('food')} icon="●" tone="food" />
            <FilterTab label="여행지" count={counts.travel} active={category === 'travel'} onClick={() => setCategory('travel')} icon="●" tone="travel" />
          </div>
          <div className="view-tabs" aria-label="보기 방식"><button className={view === 'list' ? 'active' : ''} aria-pressed={view === 'list'} onClick={() => setView('list')} type="button">☷ <span>리스트</span></button><button className={view === 'map' ? 'active' : ''} aria-pressed={view === 'map'} onClick={() => setView('map')} type="button">⌖ <span>지도</span></button></div>
        </div>

        <div className={`explore-grid ${view === 'map' ? 'map-focus' : ''}`}>
          <section className="post-grid" aria-label="게시물 목록">
            {filtered.map((post) => (
              <article className="post-card" key={post.id} onClick={() => setSelected(post)} tabIndex={0} onKeyDown={(event) => { if (event.key === 'Enter') setSelected(post); }}>
                <div className={`post-photo ${post.palette}`}><span className={`category-badge ${post.category}`}>{post.category === 'food' ? '맛집' : '여행지'}</span><span className="photo-count">▣ {post.photoCount}</span><span className="watermark">Places &amp; Plates</span></div>
                <div className="post-content"><p className="location">{post.city} · {post.country}</p><h3>{post.title}</h3><p className="post-note">{post.note}</p><div className="post-meta"><span>{post.date}</span><span>{post.meta}</span></div></div>
              </article>
            ))}
          </section>

          <aside className="map-card" id="map-panel" aria-label="게시물 지도 목업">
            <div className="map-topline"><span><b>현재 지도 영역</b> {filtered.length}개</span><button type="button">↗ 크게 보기</button></div>
            <div className="mock-map"><span className="district d1">KYOTO</span><span className="district d2">OSAKA</span><span className="district d3">NARA</span><button className="cluster food c1" type="button">{category === 'travel' ? 0 : 5}</button><button className="cluster travel c2" type="button">{category === 'food' ? 0 : 12}</button><button className="cluster mixed c3" type="button">{filtered.length}</button><button className="pin p1" type="button" aria-label="교토 게시물">●</button><button className="pin p2" type="button" aria-label="오사카 게시물">●</button></div>
            <div className="map-legend"><span><i className="food-dot" />맛집 {category === 'travel' ? 0 : counts.food}</span><span><i className="travel-dot" />여행지 {category === 'food' ? 0 : counts.travel}</span></div>
            <p className="privacy-note">촬영 메타데이터는 제거되며 업로드 원본은 서버에 보관되지 않습니다.</p>
          </aside>
        </div>
      </section>

      <section className="journey-strip" id="journeys"><div><p className="overline">FEATURED JOURNEY</p><h2>Kyoto, Spring 2026</h2><p>비가 자주 내렸고, 그래서 더 천천히 걸었던 나흘.</p></div><button type="button">여행 기록 보기 <span>→</span></button></section>
      <footer><span>Places &amp; Plates</span><span>사진과 기억이 머무는 개인 여행 아카이브</span><span>© 2026</span></footer>

      {selected && (
        <div className="detail-backdrop" role="presentation" onClick={() => setSelected(null)}>
          <article className="detail-sheet" role="dialog" aria-modal="true" aria-label={`${selected.title} 미리보기`} onClick={(event) => event.stopPropagation()}>
            <button className="detail-close" type="button" onClick={() => setSelected(null)} aria-label="닫기">×</button>
            <div className={`detail-photo ${selected.palette}`}><span className="watermark">Places &amp; Plates</span></div>
            <div className="detail-body"><p className="overline">{selected.category === 'food' ? 'PLATE' : 'PLACE'} · {selected.city.toUpperCase()}</p><h2>{selected.title}</h2><p className="detail-lead">{selected.note}</p><div className="detail-facts"><span><small>방문 시기</small>{selected.date}</span><span><small>기록</small>{selected.meta}</span><span><small>태그</small>{selected.tag}</span></div><p className="detail-story">그날의 공기와 소리를 잊지 않으려고 사진 몇 장과 짧은 문장을 남겼습니다. 장소 정보는 지도와 연결되지만 촬영 위치와 카메라 정보는 제거되며, 업로드 원본은 서버에 보관되지 않습니다.</p><button className="primary-button" type="button">전체 기록 읽기 <span>→</span></button></div>
          </article>
        </div>
      )}
    </main>
  );
}

function FilterTab({ label, count, active, onClick, icon, tone }: { label: string; count: number; active: boolean; onClick: () => void; icon?: string; tone?: string }) {
  return <button className={active ? 'active' : ''} role="tab" aria-selected={active} onClick={onClick} type="button">{icon && <i className={tone}>{icon}</i>}{label}<span>{count}</span></button>;
}
