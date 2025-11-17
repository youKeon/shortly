import type { ShortLink } from '@shared/types/shortLink';

const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';
const SHORT_BASE = import.meta.env.VITE_SHORT_BASE ?? window.location.origin;

interface ShortenResponse {
  shortCode: string;
  originalUrl: string;
}

export async function createShortLink(originalUrl: string): Promise<ShortLink> {
  const response = await fetch(`${API_BASE}/api/v1/urls/shorten`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ originalUrl })
  });

  if (!response.ok) {
    const payload = await safeParseError(response);
    throw new Error(payload ?? 'Failed to shorten URL');
  }

  const data = (await response.json()) as ShortenResponse;
  const shortCode = data.shortCode.trim();
  const originalUrlFromResponse = data.originalUrl || originalUrl;

  return {
    id: `${shortCode}-${Date.now()}`,
    originalUrl: originalUrlFromResponse,
    shortCode,
    shortUrl: `${SHORT_BASE}/${shortCode}`,
    createdAt: new Date().toISOString()
  };
}

async function safeParseError(response: Response): Promise<string | null> {
  try {
    const payload = await response.json();
    if (typeof payload?.message === 'string') {
      return payload.message;
    }
    return null;
  } catch (error) {
    console.warn('Failed to parse error payload', error);
    return null;
  }
}
