import { NextResponse } from 'next/server';

const COMMIT_SHA_PATTERN = /^[0-9a-f]{40}$/i;
const DEPLOYMENT_COMMIT_HEADER = 'X-Places-Plates-Commit';

function resolveCommitSha(): string {
  const candidates = [
    process.env.VERCEL_GIT_COMMIT_SHA,
    process.env.APP_COMMIT_SHA,
  ];

  return (
    candidates
      .map((candidate) => candidate?.trim() ?? '')
      .find((candidate) => COMMIT_SHA_PATTERN.test(candidate)) ?? 'local'
  );
}

export async function GET(): Promise<NextResponse> {
  return NextResponse.json(
    { status: 'UP' },
    {
      headers: {
        'Cache-Control': 'no-store, max-age=0',
        [DEPLOYMENT_COMMIT_HEADER]: resolveCommitSha(),
      },
    },
  );
}
