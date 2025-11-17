const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';

const RESERVED_SEGMENTS = new Set(['', 'index.html', 'assets', '@vite']);

export function attemptShortCodeRedirect(): boolean {
  const { pathname } = window.location;
  const normalized = pathname.replace(/^\//, '');
  const [firstSegment] = normalized.split('/');

  if (!firstSegment || RESERVED_SEGMENTS.has(firstSegment) || firstSegment.includes('.')) {
    return false;
  }

  // 리디렉션 API 사용 (HTTP 302)
  const target = `${API_BASE}/r/${firstSegment}`;

  console.log('Redirecting to:', target);

  window.location.replace(target);
  return true;
}
