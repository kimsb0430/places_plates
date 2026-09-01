'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import {
  deleteManagedPublishedPost,
  DraftPostApiError,
  getManagedPublishedPosts,
} from '../api/draft-post-api';
import type { DraftPost } from '../types';

type PublishedPostListState =
  | { status: 'loading' }
  | { status: 'ready'; posts: DraftPost[] }
  | { status: 'unavailable'; message: string };

export function PublishedPostList() {
  const [state, setState] = useState<PublishedPostListState>({ status: 'loading' });
  const [deletingId, setDeletingId] = useState<string | null>(null);

  useEffect(() => {
    let isActive = true;
    getManagedPublishedPosts()
      .then((posts) => {
        if (isActive) setState({ status: 'ready', posts });
      })
      .catch((error: unknown) => {
        if (!isActive) return;
        setState({
          status: 'unavailable',
          message: error instanceof DraftPostApiError
            ? error.message
            : '게시 완료 기록을 불러오지 못했습니다.',
        });
      });
    return () => {
      isActive = false;
    };
  }, []);

  async function handleDelete(post: DraftPost) {
    if (!window.confirm(`“${post.title}” 기록을 영구 삭제할까요? 공개 페이지와 지도에서 사라지고 사진도 함께 삭제됩니다.`)) {
      return;
    }
    setDeletingId(post.id);
    try {
      await deleteManagedPublishedPost(post.id);
      setState((current) => current.status === 'ready'
        ? { status: 'ready', posts: current.posts.filter((item) => item.id !== post.id) }
        : current);
    } catch (error: unknown) {
      window.alert(error instanceof DraftPostApiError ? error.message : '게시 기록을 삭제하지 못했습니다.');
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <section className="draft-list published-post-list" aria-labelledby="published-post-list-title">
      <div className="draft-list-heading">
        <div>
          <p className="login-status">PUBLISHED RECORDS</p>
          <h2 id="published-post-list-title">게시 완료 기록</h2>
          <p>공개 중인 기록의 내용과 사진을 수정하거나 영구 삭제할 수 있습니다.</p>
        </div>
        {state.status === 'ready' && <strong>{state.posts.length}</strong>}
      </div>

      {state.status === 'loading' && <p className="draft-list-message">게시 기록을 불러오는 중입니다.</p>}
      {state.status === 'unavailable' && <p className="draft-list-message" role="alert">{state.message}</p>}
      {state.status === 'ready' && state.posts.length === 0 && (
        <p className="draft-list-message">아직 게시 완료된 기록이 없습니다.</p>
      )}
      {state.status === 'ready' && state.posts.length > 0 && (
        <ul>
          {state.posts.map((post) => (
            <li key={post.id}>
              <Link className="managed-record-link" href={`/posts/${post.id}`}>
                <span>{categoryLabel(post.category)} · {visibilityLabel(post.visibility)}</span>
                <strong>{post.title}</strong>
                <small>{visitMonth(post)}</small>
                <i aria-hidden="true">↗</i>
              </Link>
              <div className="managed-record-actions" role="group" aria-label={`${post.title} 관리 작업`}>
                <Link
                  className="managed-record-edit"
                  href={`/manage/posts/${post.id}/edit`}
                  aria-label={`${post.title} 공개 기록 수정`}
                >
                  수정
                </Link>
                <button
                  className="managed-record-delete"
                  type="button"
                  aria-label={`${post.title} 공개 기록 삭제`}
                  disabled={deletingId === post.id}
                  onClick={() => void handleDelete(post)}
                >
                  {deletingId === post.id ? '삭제 중…' : '삭제'}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function categoryLabel(category: DraftPost['category']): string {
  return category === 'RESTAURANT' ? '맛집' : '여행지';
}

function visibilityLabel(visibility: DraftPost['visibility']): string {
  if (visibility === 'PUBLIC') return '전체 공개';
  if (visibility === 'UNLISTED') return '링크 공개';
  return '비공개';
}

function visitMonth(post: DraftPost): string {
  return post.publicVisitYear && post.publicVisitMonth
    ? `${post.publicVisitYear}년 ${post.publicVisitMonth}월`
    : '방문 월 미입력';
}
