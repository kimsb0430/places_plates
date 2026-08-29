import { proxyPublicImage } from '@/domain/post/server/public-image-proxy';

interface PublicPhotoRouteContext {
  params: Promise<{ postId: string; photoId: string }>;
}

export async function GET(_request: Request, context: PublicPhotoRouteContext): Promise<Response> {
  const { postId, photoId } = await context.params;
  return proxyPublicImage({
    upstreamPath: `/api/v1/public/posts/${encodeURIComponent(postId)}/photos/${encodeURIComponent(photoId)}`,
    filename: 'places-plates-photo.jpg',
  });
}
