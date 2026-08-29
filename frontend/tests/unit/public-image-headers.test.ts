import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { getProtectedPublicImageUrl } from '../../src/domain/post/api/public-post-api';
import { proxyPublicImage } from '../../src/domain/post/server/public-image-proxy';

const POST_ID = '11111111-1111-4111-8111-111111111111';
const PHOTO_ID = '22222222-2222-4222-8222-222222222222';

test('같은 출처 이미지 프록시는 외부 삽입과 프레임 표시를 억제한다', async () => {
  const fetcher: typeof fetch = async (input, init) => {
    assert.equal(input, 'http://localhost:8080/api/v1/public/posts/post-id/cover');
    assert.equal(init?.headers && new Headers(init.headers).get('Accept'), 'image/*');
    assert.equal((init as RequestInit & { next?: { revalidate?: number } })?.next?.revalidate, 3600);
    return new Response(new Uint8Array([1, 2, 3]), {
      headers: { 'Content-Length': '3', 'Content-Type': 'image/jpeg' },
    });
  };
  const response = await proxyPublicImage({
    upstreamPath: '/api/v1/public/posts/post-id/cover',
    filename: 'places-plates-cover.jpg',
    fetcher,
  });

  assert.equal(response.status, 200);
  assert.equal(response.headers.get('Cross-Origin-Resource-Policy'), 'same-origin');
  assert.equal(response.headers.get('X-Frame-Options'), 'DENY');
  assert.match(response.headers.get('Content-Security-Policy') ?? '', /frame-ancestors 'none'/);
  assert.equal(response.headers.get('X-Content-Type-Options'), 'nosniff');
  assert.equal(response.headers.get('Content-Length'), '3');
  assert.match(response.headers.get('Cache-Control') ?? '', /s-maxage=3600/);
  assert.match(response.headers.get('Cache-Control') ?? '', /stale-while-revalidate=86400/);
  assert.deepEqual(new Uint8Array(await response.arrayBuffer()), new Uint8Array([1, 2, 3]));
});

test('백엔드 공개 사진 경로는 브라우저에 같은 출처 프록시 경로로 노출한다', () => {
  assert.equal(
    getProtectedPublicImageUrl(`/api/v1/public/posts/${POST_ID}/cover`),
    `/api/public-images/posts/${POST_ID}/cover`,
  );
  assert.equal(
    getProtectedPublicImageUrl(`/api/v1/public/posts/${POST_ID}/photos/${PHOTO_ID}`),
    `/api/public-images/posts/${POST_ID}/photos/${PHOTO_ID}`,
  );
  assert.throws(
    () => getProtectedPublicImageUrl('https://unexpected.example/image.jpg'),
    /공개 사진 경로가 올바르지 않습니다/,
  );
});

test('보호 이미지는 Vercel 이미지 최적화 경로를 만들지 않는다', () => {
  const componentSource = readFileSync(
    new URL('../../src/domain/post/components/protected-public-image.tsx', import.meta.url),
    'utf8',
  );

  assert.match(componentSource, /<Image[\s\S]*?\bunoptimized\b[\s\S]*?\/>/);
  assert.match(componentSource, /preload=\{preload\}/);
  assert.match(componentSource, /loading=\{preload \? undefined : 'lazy'\}/);
  assert.match(componentSource, /decoding="async"/);
});

test('공개 목록은 첫 번째 대표 사진만 미리 불러온다', () => {
  const indexSource = readFileSync(
    new URL('../../src/domain/post/components/public-post-index.tsx', import.meta.url),
    'utf8',
  );

  assert.match(indexSource, /posts\.map\(\(post, index\)/);
  assert.match(indexSource, /preload=\{index === 0\}/);
});
