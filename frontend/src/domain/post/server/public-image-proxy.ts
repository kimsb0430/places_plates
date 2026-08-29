const PUBLIC_IMAGE_CSP = "default-src 'none'; frame-ancestors 'none'; sandbox";
const PUBLIC_IMAGE_REVALIDATE_SECONDS = 3600;
const PUBLIC_IMAGE_STALE_SECONDS = 86400;

interface PublicImageProxyOptions {
  upstreamPath: string;
  filename: string;
  fetcher?: typeof fetch;
}

export async function proxyPublicImage({
  upstreamPath,
  filename,
  fetcher = fetch,
}: PublicImageProxyOptions): Promise<Response> {
  const apiBaseUrl = (
    process.env.BACKEND_API_BASE_URL?.trim()
    || process.env.NEXT_PUBLIC_API_BASE_URL?.trim()
    || 'http://localhost:8080'
  ).replace(/\/$/, '');

  let upstreamResponse: Response;

  try {
    upstreamResponse = await fetcher(`${apiBaseUrl}${upstreamPath}`, {
      headers: { Accept: 'image/*' },
      next: { revalidate: PUBLIC_IMAGE_REVALIDATE_SECONDS },
    });
  } catch {
    return imageProxyError(502, '공개 사진 서버에 연결할 수 없습니다.');
  }

  if (!upstreamResponse.ok) {
    return imageProxyError(upstreamResponse.status === 404 ? 404 : 502, '공개 사진을 불러오지 못했습니다.');
  }

  const contentType = upstreamResponse.headers.get('Content-Type')?.split(';', 1)[0]?.trim();
  if (!contentType?.startsWith('image/')) {
    return imageProxyError(502, '공개 사진 응답 형식이 올바르지 않습니다.');
  }

  const headers = publicImageHeaders(contentType, filename);
  const contentLength = upstreamResponse.headers.get('Content-Length');
  if (contentLength && /^\d+$/.test(contentLength)) {
    headers.set('Content-Length', contentLength);
  }

  return new Response(upstreamResponse.body, {
    status: 200,
    headers,
  });
}

export function publicImageHeaders(contentType: string, filename: string): Headers {
  return new Headers({
    'Cache-Control': `public, max-age=${PUBLIC_IMAGE_REVALIDATE_SECONDS}, s-maxage=${PUBLIC_IMAGE_REVALIDATE_SECONDS}, stale-while-revalidate=${PUBLIC_IMAGE_STALE_SECONDS}`,
    'Content-Disposition': `inline; filename="${filename}"`,
    'Content-Security-Policy': PUBLIC_IMAGE_CSP,
    'Content-Type': contentType,
    'Cross-Origin-Resource-Policy': 'same-origin',
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'DENY',
  });
}

function imageProxyError(status: number, message: string): Response {
  return Response.json(
    { code: 'PUBLIC_IMAGE_PROXY_FAILED', message },
    { status, headers: { 'Cache-Control': 'no-store' } },
  );
}
