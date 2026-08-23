import type { Metadata } from 'next';
import { ManageSession } from '@/domain/auth/components/manage-session';

export const metadata: Metadata = {
  title: '기록 관리 | Places & Plates',
  description: '인증된 관리자 전용 기록 관리 화면입니다.',
};

export default function ManagePage() {
  return (
    <div className="manage-page">
      <ManageSession />
    </div>
  );
}
