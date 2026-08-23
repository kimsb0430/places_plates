import type {
  AdministratorSession,
  ApiErrorResponse,
  CsrfTokenResponse,
} from '../types';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

export class AuthenticationApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'AuthenticationApiError';
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      credentials: 'include',
      headers: {
        ...init?.headers,
        Accept: 'application/json',
      },
    });
  } catch {
    throw new AuthenticationApiError(
      0,
      'AUTH_SERVER_UNAVAILABLE',
      '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.',
    );
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({})) as ApiErrorResponse;
    throw new AuthenticationApiError(
      response.status,
      errorBody.code ?? 'AUTH_UNKNOWN_ERROR',
      errorBody.message ?? '로그인 처리 중 문제가 발생했습니다.',
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

async function getCsrfToken(): Promise<CsrfTokenResponse> {
  return request<CsrfTokenResponse>('/api/v1/auth/csrf');
}

export async function loginAdministrator(
  email: string,
  password: string,
): Promise<AdministratorSession> {
  const csrfToken = await getCsrfToken();
  return request<AdministratorSession>('/api/v1/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [csrfToken.headerName]: csrfToken.token,
    },
    body: JSON.stringify({ email, password }),
  });
}

export async function getAdministratorSession(): Promise<AdministratorSession> {
  return request<AdministratorSession>('/api/v1/auth/session');
}

export async function logoutAdministrator(): Promise<void> {
  const csrfToken = await getCsrfToken();
  await request<void>('/api/v1/auth/logout', {
    method: 'POST',
    headers: {
      [csrfToken.headerName]: csrfToken.token,
    },
  });
}
