const PUBLIC_IMAGE_CSP = "default-src 'none'; frame-ancestors 'none'; sandbox";

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
      cache: 'no-store',
      headers: { Accept: 'image/*' },
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

  return new Response(upstreamResponse.body, {
    status: 200,
    headers: publicImageHeaders(contentType, filename),
  });
}

export function publicImageHeaders(contentType: string, filename: string): Headers {
  return new Headers({
    'Cache-Control': 'public, max-age=3600, s-maxage=3600',
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
