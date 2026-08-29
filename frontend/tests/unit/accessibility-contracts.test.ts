import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { resolvePublicPhotoAltText } from '../../src/domain/post/public-photo-alt';

const homePageSource = readSource('src/app/page.tsx');
const globalStylesSource = readSource('src/app/globals.css');
const loginFormSource = readSource('src/domain/auth/components/login-form.tsx');
const mapCategorySource = readSource('src/domain/map/components/map-category-tabs.tsx');
const mapExplorerSource = readSource('src/domain/map/components/google-map-explorer.tsx');
const mapSplitSource = readSource('src/domain/map/components/map-split-explorer.tsx');
const publicCategorySource = readSource('src/domain/post/components/public-post-tabs.tsx');

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

test('홈 탭과 미리보기 대화상자는 키보드 이동 및 포커스 복귀 계약을 갖는다', () => {
  assert.match(homePageSource, /event\.key === 'ArrowRight'/);
  assert.match(homePageSource, /event\.key === 'ArrowLeft'/);
  assert.match(homePageSource, /event\.key === 'Escape'/);
  assert.match(homePageSource, /aria-modal="true"/);
  assert.match(homePageSource, /dialogTriggerRef\.current\?\.focus\(\)/);
  assert.match(homePageSource, /aria-haspopup="dialog"/);
  assert.match(homePageSource, /event\.key !== 'Enter' && event\.key !== ' '/);
  assert.match(homePageSource, /element\.inert = true/);
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
