import assert from 'node:assert/strict';
import test from 'node:test';
import nextConfig from '../../next.config';

test('Next 이미지 최적화 응답은 외부 브라우저 삽입과 프레임 표시를 억제한다', async () => {
  assert.ok(nextConfig.headers);
  const rules = await nextConfig.headers();
  const imageRule = rules.find((rule) => rule.source === '/_next/image');
  const headers = Object.fromEntries(
    imageRule?.headers.map((header) => [header.key, header.value]) ?? [],
  );

  assert.equal(headers['Cross-Origin-Resource-Policy'], 'same-origin');
  assert.equal(headers['X-Frame-Options'], 'DENY');
  assert.match(headers['Content-Security-Policy'] ?? '', /frame-ancestors 'none'/);
  assert.equal(headers['X-Content-Type-Options'], 'nosniff');
});
