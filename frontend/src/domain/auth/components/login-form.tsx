'use client';

import { useEffect, useState, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import {
  AuthenticationApiError,
  getAdministratorSession,
  loginAdministrator,
} from '../api/authentication-api';

export function LoginForm() {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    let isActive = true;

    getAdministratorSession()
      .then(() => {
        if (isActive) {
          router.replace('/manage');
        }
      })
      .catch(() => undefined);

    return () => {
      isActive = false;
    };
  }, [router]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsSubmitting(true);
    setErrorMessage('');

    const formData = new FormData(event.currentTarget);
    const email = String(formData.get('email') ?? '').trim();
    const password = String(formData.get('password') ?? '');

    try {
      await loginAdministrator(email, password);
      router.replace('/manage');
    } catch (error) {
      setErrorMessage(
        error instanceof AuthenticationApiError
          ? error.message
          : '로그인 처리 중 문제가 발생했습니다.',
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form className="login-fields" onSubmit={handleSubmit}>
      <label htmlFor="login-email">이메일</label>
      <input
        id="login-email"
        name="email"
        type="email"
        placeholder="name@example.com"
        autoComplete="username"
        required
        disabled={isSubmitting}
      />
      <label htmlFor="login-password">비밀번호</label>
      <input
        id="login-password"
        name="password"
        type="password"
        placeholder="비밀번호"
        autoComplete="current-password"
        required
        disabled={isSubmitting}
      />
      {errorMessage && (
        <p className="field-message field-message-error" role="alert">{errorMessage}</p>
      )}
      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? '확인 중…' : '로그인'}
      </button>
    </form>
  );
}
