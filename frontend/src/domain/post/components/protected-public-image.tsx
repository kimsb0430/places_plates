'use client';

import Image from 'next/image';
import { deterPublicImageTransfer } from '../public-image-protection';

interface ProtectedPublicImageProps {
  src: string;
  alt: string;
  sizes: string;
  preload?: boolean;
  shieldClassName: string;
}

export function ProtectedPublicImage({
  src,
  alt,
  sizes,
  preload = false,
  shieldClassName,
}: ProtectedPublicImageProps) {
  return (
    <span
      className="protected-public-image"
      data-image-protection="context-menu-drag-copy"
      onContextMenu={deterPublicImageTransfer}
      onDragStart={deterPublicImageTransfer}
      onCopy={deterPublicImageTransfer}
    >
      <Image
        src={src}
        alt={alt}
        fill
        sizes={sizes}
        preload={preload}
        loading={preload ? undefined : 'lazy'}
        decoding="async"
        draggable={false}
        unoptimized
      />
      <span className={shieldClassName} aria-hidden="true" />
    </span>
  );
}
