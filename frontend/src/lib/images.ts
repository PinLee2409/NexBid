/**
 * Images uploaded through the seller form exist only as in-browser object
 * URLs until a real media service stores them. Next's optimizer cannot fetch
 * those, so anything rendering product imagery asks this first.
 */
export function isLocalImage(url: string): boolean {
  return url.startsWith("blob:") || url.startsWith("data:");
}
