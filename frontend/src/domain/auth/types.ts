export interface AdministratorSession {
  userId: string;
  email: string;
  role: 'ADMIN';
}

export interface CsrfTokenResponse {
  headerName: string;
  token: string;
}

export interface ApiErrorResponse {
  code?: string;
  message?: string;
}
