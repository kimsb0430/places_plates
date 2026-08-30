import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import { GET } from '../../src/app/api/deployment/route';

const originalVercelCommitSha = process.env.VERCEL_GIT_COMMIT_SHA;
const originalAppCommitSha = process.env.APP_COMMIT_SHA;

afterEach(() => {
  restoreEnvironmentVariable('VERCEL_GIT_COMMIT_SHA', originalVercelCommitSha);
  restoreEnvironmentVariable('APP_COMMIT_SHA', originalAppCommitSha);
});

test('Vercel 배포 커밋을 상태 응답 헤더로 제공한다', async () => {
  const expectedCommitSha = 'a'.repeat(40);
  process.env.VERCEL_GIT_COMMIT_SHA = expectedCommitSha;
  delete process.env.APP_COMMIT_SHA;

  const response = await GET();

  assert.equal(response.status, 200);
  assert.equal(response.headers.get('x-places-plates-commit'), expectedCommitSha);
  assert.equal(response.headers.get('cache-control'), 'no-store, max-age=0');
  assert.deepEqual(await response.json(), { status: 'UP' });
});

test('유효한 배포 커밋이 없으면 로컬 상태임을 표시한다', async () => {
  process.env.VERCEL_GIT_COMMIT_SHA = 'invalid';
  delete process.env.APP_COMMIT_SHA;

  const response = await GET();

  assert.equal(response.headers.get('x-places-plates-commit'), 'local');
});

function restoreEnvironmentVariable(name: string, value: string | undefined): void {
  if (value === undefined) {
    delete process.env[name];
    return;
  }
  process.env[name] = value;
}
