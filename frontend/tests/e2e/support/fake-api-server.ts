import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';

const host = '127.0.0.1';
const port = 3211;
const apiOrigin = `http://${host}:${port}`;
const webOrigin = 'http://127.0.0.1:3210';
const draftId = '11111111-1111-4111-8111-111111111111';
const batchId = '22222222-2222-4222-8222-222222222222';
const itemId = '33333333-3333-4333-8333-333333333333';
const photoId = '44444444-4444-4444-8444-444444444444';
const placeId = '55555555-5555-4555-8555-555555555555';
const now = '2026-08-30T12:00:00Z';
const expiresAt = '2030-01-01T00:00:00Z';
const pixelPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  'base64',
);

interface E2eState {
  category: 'RESTAURANT' | 'DESTINATION';
  draft: Record<string, unknown>;
  photoReady: boolean;
  published: boolean;
}

let state = createState();

const server = createServer(async (request, response) => {
  setCorsHeaders(response);
  if (request.method === 'OPTIONS') {
    response.writeHead(204);
    response.end();
    return;
  }

  const url = new URL(request.url ?? '/', apiOrigin);
  const path = url.pathname;

  if (request.method === 'GET' && path === '/__e2e/health') {
    sendJson(response, 200, { status: 'UP' });
    return;
  }
  if (request.method === 'POST' && path === '/__e2e/reset') {
    state = createState();
    sendJson(response, 200, { reset: true });
    return;
  }
  if (request.method === 'GET' && path === '/api/v1/auth/csrf') {
    sendJson(response, 200, { headerName: 'X-XSRF-TOKEN', token: 'e2e-csrf-token' });
    return;
  }
  if (request.method === 'GET' && path === '/api/v1/auth/session') {
    sendJson(response, 200, {
      userId: '66666666-6666-4666-8666-666666666666',
      email: 'e2e-admin@example.test',
      role: 'ADMIN',
    });
    return;
  }
  if (request.method === 'GET' && path === '/api/v1/manage/drafts') {
    sendJson(response, 200, state.photoReady ? [state.draft] : []);
    return;
  }
  if (request.method === 'POST' && path === '/api/v1/manage/photo-uploads') {
    const body = await readJson(request);
    state.category = body.category === 'DESTINATION' ? 'DESTINATION' : 'RESTAURANT';
    state.draft.category = state.category;
    sendJson(response, 200, uploadBatch('PENDING', true));
    return;
  }
  if (request.method === 'POST' && path === '/storage/v1/upload/resumable') {
    const uploadedBytes = (await readBody(request)).byteLength;
    response.writeHead(201, {
      Location: `${apiOrigin}/storage/v1/upload/resumable/${itemId}`,
      'Tus-Resumable': '1.0.0',
      'Upload-Offset': String(uploadedBytes),
    });
    response.end();
    return;
  }
  if (path === `/storage/v1/upload/resumable/${itemId}` && request.method === 'HEAD') {
    response.writeHead(200, { 'Tus-Resumable': '1.0.0', 'Upload-Offset': '4' });
    response.end();
    return;
  }
  if (path === `/storage/v1/upload/resumable/${itemId}` && request.method === 'PATCH') {
    const currentOffset = Number(request.headers['upload-offset'] ?? 0);
    const uploadedBytes = (await readBody(request)).byteLength;
    response.writeHead(204, {
      'Tus-Resumable': '1.0.0',
      'Upload-Offset': String(currentOffset + uploadedBytes),
    });
    response.end();
    return;
  }
  if (request.method === 'POST' && path.includes(`/photo-uploads/${batchId}/items/${itemId}/`)) {
    const action = path.split('/').at(-1);
    if (action === 'sanitize') {
      state.photoReady = true;
      sendJson(response, 200, {
        jobId: '77777777-7777-4777-8777-777777777777',
        uploadItemId: itemId,
        photoId,
        status: 'COMPLETED',
        failureCode: null,
        message: '사진 정제가 완료되었습니다.',
        variants: [
          { type: 'THUMBNAIL', width: 320, height: 240, byteSize: 128 },
          { type: 'MAP_CARD', width: 960, height: 720, byteSize: 256 },
          { type: 'PUBLIC_DETAIL', width: 1600, height: 1200, byteSize: 512 },
        ],
      });
      return;
    }
    sendJson(response, 200, uploadItem(action === 'complete' ? 'PROCESSING' : 'UPLOADING', false));
    return;
  }
  if (request.method === 'GET' && path === `/api/v1/manage/drafts/${draftId}`) {
    sendJson(response, 200, state.draft);
    return;
  }
  if (request.method === 'PATCH' && path === `/api/v1/manage/drafts/${draftId}`) {
    const body = await readJson(request);
    state.draft = { ...state.draft, ...body, updatedAt: new Date().toISOString() };
    sendJson(response, 200, state.draft);
    return;
  }
  if (request.method === 'GET' && path === `/api/v1/manage/drafts/${draftId}/photos`) {
    sendJson(response, 200, state.photoReady ? [draftPhoto()] : []);
    return;
  }
  if (request.method === 'PUT' && path === `/api/v1/manage/drafts/${draftId}/photos`) {
    const body = await readJson(request);
    const edit = Array.isArray(body.photos) ? body.photos[0] : undefined;
    sendJson(response, 200, [draftPhoto(edit?.altText ?? null, edit?.cover ?? true)]);
    return;
  }
  if (request.method === 'GET' && path === `/api/v1/manage/drafts/${draftId}/photos/${photoId}/thumbnail`) {
    sendImage(response);
    return;
  }
  if (request.method === 'PUT' && path === `/api/v1/manage/drafts/${draftId}/place`) {
    const body = await readJson(request);
    const place = {
      id: placeId,
      source: body.source ?? 'MANUAL',
      googlePlaceId: body.googlePlaceId ?? null,
      name: body.name,
      placeType: body.placeType ?? null,
      formattedAddress: body.formattedAddress ?? null,
      latitude: body.latitude ?? null,
      longitude: body.longitude ?? null,
      googleMapsUrl: body.googleMapsUrl ?? null,
      refreshedAt: now,
    };
    state.draft = { ...state.draft, place, updatedAt: new Date().toISOString() };
    sendJson(response, 200, state.draft);
    return;
  }
  if (request.method === 'GET' && path === `/api/v1/manage/drafts/${draftId}/publication-readiness`) {
    const draft = state.draft;
    const ready = Boolean(
      state.photoReady
      && draft.title
      && draft.summary
      && draft.publicVisitYear
      && draft.publicVisitMonth
      && draft.place,
    );
    sendJson(response, 200, {
      ready,
      checks: [
        { code: 'REQUIRED_FIELDS', label: '필수 기록 입력', passed: ready },
        { code: 'PLACE_CONNECTED', label: '장소 연결', passed: Boolean(draft.place) },
        { code: 'PHOTO_SAFE', label: '사진 보호 처리', passed: state.photoReady },
      ],
    });
    return;
  }
  if (request.method === 'POST' && path === `/api/v1/manage/drafts/${draftId}/publication`) {
    state.published = true;
    state.draft = {
      ...state.draft,
      visibility: 'PUBLIC',
      status: 'PUBLISHED',
      updatedAt: new Date().toISOString(),
    };
    sendJson(response, 200, {
      id: draftId,
      visibility: 'PUBLIC',
      status: 'PUBLISHED',
      publishedAt: new Date().toISOString(),
    });
    return;
  }
  if (request.method === 'GET' && path === '/api/v1/public/posts') {
    sendJson(response, 200, publicPostList(url.searchParams.get('category')));
    return;
  }
  if (request.method === 'GET' && path === '/api/v1/map/posts') {
    sendJson(response, 200, mapPostList(url.searchParams.get('category')));
    return;
  }
  if (request.method === 'GET' && (
    path === `/api/v1/public/posts/${draftId}/cover`
    || path === `/api/v1/public/posts/${draftId}/photos/${photoId}`
  )) {
    sendImage(response);
    return;
  }

  sendJson(response, 404, { code: 'E2E_NOT_FOUND', message: `${request.method} ${path}` });
});

server.listen(port, host);

for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.on(signal, () => {
    server.closeAllConnections();
    server.close();
    process.exit(0);
  });
}

function createState(): E2eState {
  return {
    category: 'RESTAURANT',
    photoReady: false,
    published: false,
    draft: {
      id: draftId,
      category: 'RESTAURANT',
      title: '',
      summary: null,
      content: null,
      publicVisitYear: null,
      publicVisitMonth: null,
      place: null,
      restaurantDetails: null,
      destinationDetails: null,
      visibility: 'PRIVATE',
      status: 'DRAFT',
      createdAt: now,
      updatedAt: now,
    },
  };
}

function uploadBatch(status: string, includeTicket: boolean): Record<string, unknown> {
  return {
    id: batchId,
    draftPostId: draftId,
    status: 'ACTIVE',
    expiresAt,
    items: [uploadItem(status, includeTicket)],
  };
}

function uploadItem(status: string, includeTicket: boolean): Record<string, unknown> {
  return {
    id: itemId,
    clientFileName: 'c38-e2e.jpg',
    mimeType: 'image/jpeg',
    byteSize: 4,
    uploadedBytes: status === 'PENDING' ? 0 : 4,
    status,
    attemptCount: 0,
    failureCode: null,
    expiresAt,
    uploadTicket: includeTicket ? {
      endpoint: `${apiOrigin}/storage/v1/upload/resumable`,
      token: 'e2e-upload-signature',
      bucketName: 'e2e-private-bucket',
      objectName: `temporary/${itemId}`,
    } : null,
  };
}

function draftPhoto(altText: string | null = null, cover = true): Record<string, unknown> {
  return {
    id: photoId,
    displayOrder: 0,
    cover,
    altText,
    processingStatus: 'READY',
    thumbnailPath: `/api/v1/manage/drafts/${draftId}/photos/${photoId}/thumbnail`,
  };
}

function publicPostList(category: string | null): Record<string, unknown> {
  const visible = state.published && (!category || category === state.category);
  return {
    counts: {
      all: state.published ? 1 : 0,
      restaurant: state.published && state.category === 'RESTAURANT' ? 1 : 0,
      destination: state.published && state.category === 'DESTINATION' ? 1 : 0,
    },
    posts: visible ? [{
      id: draftId,
      category: state.category,
      title: state.draft.title,
      summary: state.draft.summary,
      publicVisitYear: state.draft.publicVisitYear,
      publicVisitMonth: state.draft.publicVisitMonth,
      publishedAt: now,
      cover: {
        path: `/api/v1/public/posts/${draftId}/cover`,
        altText: 'C38 E2E 대표 사진',
        width: 960,
        height: 720,
      },
    }] : [],
  };
}

function mapPostList(category: string | null): Record<string, unknown> {
  const place = state.draft.place as Record<string, unknown> | null;
  const visible = state.published && place && (!category || category === state.category);
  return {
    counts: {
      all: state.published ? 1 : 0,
      restaurant: state.published && state.category === 'RESTAURANT' ? 1 : 0,
      destination: state.published && state.category === 'DESTINATION' ? 1 : 0,
    },
    posts: visible ? [{
      id: draftId,
      category: state.category,
      title: state.draft.title,
      placeName: place.name,
      latitude: place.latitude,
      longitude: place.longitude,
      publicVisitYear: state.draft.publicVisitYear,
      publicVisitMonth: state.draft.publicVisitMonth,
    }] : [],
  };
}

function setCorsHeaders(response: ServerResponse): void {
  response.setHeader('Access-Control-Allow-Origin', webOrigin);
  response.setHeader('Access-Control-Allow-Credentials', 'true');
  response.setHeader(
    'Access-Control-Allow-Headers',
    'Accept, Content-Type, Tus-Resumable, Upload-Length, Upload-Metadata, Upload-Offset, X-Signature, X-XSRF-TOKEN',
  );
  response.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, POST, PATCH, PUT, DELETE, OPTIONS');
  response.setHeader('Access-Control-Expose-Headers', 'Location, Tus-Resumable, Upload-Offset');
}

function sendJson(response: ServerResponse, status: number, body: unknown): void {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify(body));
}

function sendImage(response: ServerResponse): void {
  response.writeHead(200, {
    'Content-Type': 'image/png',
    'Content-Length': String(pixelPng.byteLength),
    'Cache-Control': 'no-store',
  });
  response.end(pixelPng);
}

async function readJson(request: IncomingMessage): Promise<Record<string, unknown>> {
  const body = await readBody(request);
  return body.byteLength ? JSON.parse(body.toString('utf8')) : {};
}

async function readBody(request: IncomingMessage): Promise<Buffer> {
  const chunks: Buffer[] = [];
  for await (const chunk of request) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  return Buffer.concat(chunks);
}
