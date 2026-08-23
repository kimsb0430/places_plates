import type { Metadata } from 'next';
import Link from 'next/link';
import { LoginForm } from '@/domain/auth/components/login-form';

export const metadata: Metadata = {
  title: '로그인 | Places & Plates',
  description: 'Places & Plates 기록 관리 로그인 화면입니다.',
};

export default function LoginPage() {
  return (
    <div className="login-page">
      <section className="login-copy" aria-labelledby="login-title">
        <p className="overline">PRIVATE ARCHIVE ACCESS</p>
        <h1 id="login-title">기록을 관리하는<br />공간으로 들어갑니다.</h1>
        <p>공개 페이지는 누구나 볼 수 있지만 기록 작성과 사진 관리는 소유자만 할 수 있습니다.</p>
      </section>
      <section className="login-card" aria-labelledby="login-form-title">
        <p className="login-status">보호된 세션 로그인</p>
        <h2 id="login-form-title">관리자 로그인</h2>
        <p className="login-guidance">등록된 관리자 계정으로 로그인하면 기록 관리 공간으로 이동합니다.</p>
        <LoginForm />
        <Link href="/">홈으로 돌아가기 <span aria-hidden="true">→</span></Link>
      </section>
    </div>
  );
}
