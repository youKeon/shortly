const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';

const RESERVED_SEGMENTS = new Set(['', 'index.html', 'assets', '@vite']);

export function attemptShortCodeRedirect(): boolean {
  const { pathname } = window.location;
  const normalized = pathname.replace(/^\//, '');
  const [firstSegment] = normalized.split('/');

  if (!firstSegment || RESERVED_SEGMENTS.has(firstSegment) || firstSegment.includes('.')) {
    return false;
  }

  const target = `${API_BASE}/api/v1/urls/${firstSegment}`;

  console.log('target ::: ', target);

  window.location.replace(target);
  return true;
}
