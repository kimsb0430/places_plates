import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { resolvePublicPhotoAltText } from '../../src/domain/post/public-photo-alt';

const homePageSource = readSource('src/app/page.tsx');
const publicNavigationSource = readSource('src/shared/ui/public-navigation.tsx');
const globalStylesSource = readSource('src/app/globals.css');
const loginFormSource = readSource('src/domain/auth/components/login-form.tsx');
const mapCategorySource = readSource('src/domain/map/components/map-category-tabs.tsx');
const mapExplorerSource = readSource('src/domain/map/components/google-map-explorer.tsx');
const mapSplitSource = readSource('src/domain/map/components/map-split-explorer.tsx');
const publicCategorySource = readSource('src/domain/post/components/public-post-tabs.tsx');
const publicPostIndexSource = readSource('src/domain/post/components/public-post-index.tsx');
const publicPhotoGallerySource = readSource('src/domain/post/components/public-photo-gallery.tsx');
const managedPublicPostActionsSource = readSource('src/domain/post/components/managed-public-post-actions.tsx');

test('공개 사진 설명이 비어 있으면 기록 맥락을 담은 대체 문구를 사용한다', () => {
  assert.equal(resolvePublicPhotoAltText('  따뜻한 국물이 담긴 그릇  ', '기본 설명'), '따뜻한 국물이 담긴 그릇');
  assert.equal(resolvePublicPhotoAltText('   ', '오니기리본고 대표 사진'), '오니기리본고 대표 사진');
});

test('페이지 이동형 카테고리는 탭 위젯이 아닌 현재 페이지 링크로 노출한다', () => {
  for (const source of [publicCategorySource, mapCategorySource]) {
    assert.doesNotMatch(source, /role="tablist"/);
    assert.doesNotMatch(source, /role="tab"/);
    assert.match(source, /aria-current=\{isSelected \? 'page' : undefined\}/);
  }
});

test('홈은 실제 공개 API와 기록 진입점을 사용하고 대표 게시물 영웅 카드를 노출하지 않는다', () => {
  assert.match(homePageSource, /getPublicPosts\(undefined, 'LATEST'\)/);
  assert.match(homePageSource, /<h1>나의 기록<\/h1>/);
  assert.match(homePageSource, /<Link className="hero-write-card" href="\/manage">기록하기<\/Link>/);
  assert.match(homePageSource, /<div className="hero-write-space">/);
  assert.match(globalStylesSource, /\.hero-write-space \{ min-height:472px; display:flex; align-items:flex-end; \}/);
  assert.match(globalStylesSource, /\.hero-write-card \{ width:min\(430px,100%\); min-height:270px;/);
  assert.doesNotMatch(homePageSource, /hero-actions/);
  assert.doesNotMatch(homePageSource, /hero-primary-action/);
  assert.doesNotMatch(homePageSource, /hero-secondary-action/);
  assert.doesNotMatch(homePageSource, /ADD A NEW MEMORY/);
  assert.doesNotMatch(homePageSource, /새로운 장소와/);
  assert.doesNotMatch(homePageSource, /featured/);
  assert.doesNotMatch(homePageSource, /hero-live-photo/);
  assert.doesNotMatch(homePageSource, /Kyoto, Spring 2026/);
  assert.doesNotMatch(homePageSource, /const posts:/);
});

test('공개 기록 카드 링크는 기록 제목을 포함한 접근 가능한 이름을 갖는다', () => {
  assert.match(publicPostIndexSource, /aria-label=\{`\$\{post\.title\} 기록 읽기`\}/);
});

test('주요 메뉴는 기록과 지도만 제공하고 여행 앵커를 제거한다', () => {
  assert.match(publicNavigationSource, /label: '기록'/);
  assert.match(publicNavigationSource, /label: '지도'/);
  assert.doesNotMatch(publicNavigationSource, /label: '여행'/);
  assert.doesNotMatch(publicNavigationSource, /#journeys/);
});

test('로그인한 관리자는 공개 상세에서 CSRF 보호 삭제를 실행할 수 있다', () => {
  assert.match(managedPublicPostActionsSource, /getAdministratorSession\(\)/);
  assert.match(managedPublicPostActionsSource, /deleteManagedPublishedPost\(postId\)/);
  assert.match(managedPublicPostActionsSource, /window\.confirm/);
  assert.match(managedPublicPostActionsSource, /이 기록 삭제/);
});

test('공개 사진 확대 화면은 대화상자 이름과 키보드 닫기 계약을 갖는다', () => {
  assert.match(publicPhotoGallerySource, /event\.key === 'Escape'/);
  assert.match(publicPhotoGallerySource, /aria-modal="true"/);
  assert.match(publicPhotoGallerySource, /aria-label="사진 크게 보기 닫기"/);
  assert.match(publicPhotoGallerySource, /공개용 고해상도 이미지/);
});

test('폼과 지도 조작 요소는 오류·대상·포커스 상태를 이름으로 연결한다', () => {
  assert.match(loginFormSource, /aria-describedby=\{errorMessage \? 'login-error' : undefined\}/);
  assert.match(loginFormSource, /aria-invalid=\{Boolean\(errorMessage\)\}/);
  assert.match(mapSplitSource, /aria-controls="public-record-map"/);
  assert.match(mapSplitSource, /지도에서 선택/);
  assert.match(mapExplorerSource, /id="public-record-map"/);
  assert.match(mapExplorerSource, /role="region"/);
});

test('모든 기본 폼 컨트롤에 보이는 키보드 포커스가 적용된다', () => {
  assert.match(globalStylesSource, /:where\(a,button,input,select,textarea,/);
  assert.match(globalStylesSource, /outline:3px solid var\(--color-accent\)/);
});

function readSource(path: string): string {
  return readFileSync(new URL(`../../${path}`, import.meta.url), 'utf8');
}
