import type { Metadata } from 'next';
import Link from 'next/link';

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
        <p className="login-status">인증 연결 전</p>
        <h2 id="login-form-title">관리자 로그인</h2>
        <p className="login-guidance">로그인·세션 기능은 다음 개발 단계에서 연결됩니다.</p>
        <div className="login-fields" aria-label="로그인 입력 미리보기">
          <label htmlFor="login-email">이메일</label>
          <input id="login-email" type="email" placeholder="name@example.com" disabled />
          <label htmlFor="login-password">비밀번호</label>
          <input id="login-password" type="password" placeholder="비밀번호" disabled />
          <button type="button" disabled>로그인 준비 중</button>
        </div>
        <Link href="/">홈으로 돌아가기 <span aria-hidden="true">→</span></Link>
      </section>
    </div>
  );
}
