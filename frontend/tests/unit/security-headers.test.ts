import assert from 'node:assert/strict';
import test from 'node:test';

import nextConfig, { contentSecurityPolicy, securityHeaders } from '../../next.config';

test('all frontend routes receive the expected security headers', async () => {
  assert.equal(typeof nextConfig.headers, 'function');

  const rules = await nextConfig.headers!();
  assert.equal(rules.length, 1);
  assert.equal(rules[0]?.source, '/:path*');
  assert.deepEqual(rules[0]?.headers, securityHeaders);

  const headers = new Map(securityHeaders.map(({ key, value }) => [key, value]));
  assert.equal(headers.get('X-Frame-Options'), 'DENY');
  assert.equal(headers.get('X-Content-Type-Options'), 'nosniff');
  assert.equal(headers.get('Referrer-Policy'), 'strict-origin-when-cross-origin');
  assert.equal(headers.get('Cross-Origin-Opener-Policy'), 'same-origin-allow-popups');
  assert.match(headers.get('Permissions-Policy') ?? '', /camera=\(\)/);
});

test('content security policy blocks embedding and preserves required map and upload origins', () => {
  assert.match(contentSecurityPolicy, /frame-ancestors 'none'/);
  assert.match(contentSecurityPolicy, /object-src 'none'/);
  assert.match(contentSecurityPolicy, /form-action 'self'/);
  assert.match(contentSecurityPolicy, /https:\/\/\*\.googleapis\.com/);
  assert.match(contentSecurityPolicy, /https:\/\/\*\.gstatic\.com/);
  assert.match(contentSecurityPolicy, /https:\/\/\*\.supabase\.co/);
});
