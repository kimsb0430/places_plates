'use client';

import { useCallback, useEffect, useRef, useState, type MouseEvent } from 'react';
import { getPublicPhotoUrl } from '../api/public-post-api';
import { resolvePublicPhotoAltText } from '../public-photo-alt';
import type { PublicPostPhoto } from '../types';
import { ProtectedPublicImage } from './protected-public-image';

interface PublicPhotoGalleryProps {
  title: string;
  category: 'RESTAURANT' | 'DESTINATION';
  photos: PublicPostPhoto[];
}

export function PublicPhotoGallery({ title, category, photos }: PublicPhotoGalleryProps) {
  const primaryPhoto = photos.find((photo) => photo.cover) ?? photos[0];
  const secondaryPhotos = photos.filter((photo) => photo.id !== primaryPhoto?.id);
  const [selectedPhoto, setSelectedPhoto] = useState<PublicPostPhoto | null>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const triggerRef = useRef<HTMLElement | null>(null);
  const closeLightbox = useCallback(() => {
    setSelectedPhoto(null);
    window.setTimeout(() => triggerRef.current?.focus(), 0);
  }, []);
  const openLightbox = useCallback((photo: PublicPostPhoto) => {
    triggerRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setSelectedPhoto(photo);
  }, []);

  useEffect(() => {
    if (!selectedPhoto) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    closeButtonRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeLightbox();
      if (event.key === 'Tab') {
        event.preventDefault();
        closeButtonRef.current?.focus();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [closeLightbox, selectedPhoto]);

  if (!primaryPhoto) {
    return (
      <section className="public-detail-gallery" aria-label={`${title} 사진`}>
        <div className="public-detail-photo-placeholder" aria-label="표시할 공개 사진이 없습니다.">
          <span>{category === 'RESTAURANT' ? 'PLATE' : 'PLACE'}</span>
        </div>
      </section>
    );
  }

  return (
    <>
      <section className="public-detail-gallery" aria-label={`${title} 사진`}>
        <PhotoButton className="public-detail-primary-photo" photo={primaryPhoto}
          fallbackAlt={`${title} 대표 사진`} sizes="(max-width: 980px) 100vw, 72vw" preload
          onOpen={() => openLightbox(primaryPhoto)} />
        {secondaryPhotos.length > 0 && (
          <div className="public-detail-secondary-photos">
            {secondaryPhotos.map((photo, index) => (
              <PhotoButton key={photo.id} className="public-detail-secondary-photo" photo={photo}
                fallbackAlt={`${title} 사진 ${index + 2}`}
                sizes="(max-width: 640px) 100vw, (max-width: 980px) 50vw, 33vw"
                onOpen={() => openLightbox(photo)} />
            ))}
          </div>
        )}
        <p className="public-detail-gallery-hint">사진을 선택하면 공개용 고해상도 이미지로 크게 볼 수 있습니다.</p>
      </section>

      {selectedPhoto && (
        <div className="public-photo-lightbox" onMouseDown={(event) => handleBackdrop(event, closeLightbox)}>
          <section role="dialog" aria-modal="true" aria-label={`${title} 사진 크게 보기`}>
            <button ref={closeButtonRef} type="button" onClick={closeLightbox} aria-label="사진 크게 보기 닫기">
              닫기 <span aria-hidden="true">×</span>
            </button>
            <div className="public-photo-lightbox-image">
              <ProtectedPublicImage src={getPublicPhotoUrl(selectedPhoto.path)}
                alt={resolvePublicPhotoAltText(selectedPhoto.altText, `${title} 확대 사진`)}
                sizes="100vw" preload shieldClassName="public-detail-photo-shield" />
            </div>
            <p>EXIF 제거 및 워터마크 처리가 끝난 공개용 사진입니다.</p>
          </section>
        </div>
      )}
    </>
  );
}

interface PhotoButtonProps {
  className: string;
  photo: PublicPostPhoto;
  fallbackAlt: string;
  sizes: string;
  preload?: boolean;
  onOpen: () => void;
}

function PhotoButton({ className, photo, fallbackAlt, sizes, preload = false, onOpen }: PhotoButtonProps) {
  const alt = resolvePublicPhotoAltText(photo.altText, fallbackAlt);
  return (
    <figure className={className}>
      <button type="button" onClick={onOpen} aria-label={`${alt} 크게 보기`}>
        <ProtectedPublicImage src={getPublicPhotoUrl(photo.path)} alt={alt} sizes={sizes}
          preload={preload} shieldClassName="public-detail-photo-shield" />
        <span className="public-detail-photo-open" aria-hidden="true">크게 보기</span>
      </button>
    </figure>
  );
}

function handleBackdrop(event: MouseEvent<HTMLDivElement>, close: () => void) {
  if (event.target === event.currentTarget) close();
}
