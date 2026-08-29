export function resolvePublicPhotoAltText(altText: string, fallback: string): string {
  const normalizedAltText = altText.trim();
  return normalizedAltText || fallback;
}
