import { useCallback, useEffect, useState } from 'react';
import { Moon, Sparkles, SunMedium } from 'lucide-react';
import { ShortenUrlForm } from '@features/shorten-url';
import { ShortLinkList } from '@widgets/short-link-list';
import { createShortLink } from '@shared/api/urlClient';
import type { ShortLink } from '@shared/types/shortLink';
import { toast } from 'sonner';

export function App() {
  const [links, setLinks] = useState<ShortLink[]>([]);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [theme, setTheme] = useState<Theme>(() => detectInitialTheme());
  const [isManualTheme, setIsManualTheme] = useState<boolean>(() => {
    if (typeof window === 'undefined') {
      return false;
    }
    return Boolean(localStorage.getItem('shortly-theme'));
  });

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  useEffect(() => {
    if (isManualTheme) {
      localStorage.setItem('shortly-theme', theme);
      return () => undefined;
    }

    localStorage.removeItem('shortly-theme');

    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const listener = (event: MediaQueryListEvent) => {
      setTheme(event.matches ? 'dark' : 'light');
    };

    media.addEventListener('change', listener);
    return () => media.removeEventListener('change', listener);
  }, [isManualTheme, theme]);

  const handleShorten = useCallback(async (url: string) => {
    setPending(true);
    setError(null);

    try {
      const result = await createShortLink(url);
      setLinks((prev) => [result, ...prev].slice(0, 8));
      toast.success('Short link ready', {
        description: result.shortUrl
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Unable to shorten URL.';
      setError(message);
      toast.error('Could not shorten URL', { description: message });
    } finally {
      setPending(false);
    }
  }, []);

  const toggleTheme = useCallback(() => {
    setIsManualTheme(true);
    setTheme((prev) => (prev === 'light' ? 'dark' : 'light'));
  }, []);

  return (
    <div className="app-shell">
      <header className="hero">
        <div className="hero__top">
          <div className="hero__pill">
            <Sparkles size={16} aria-hidden />
            Fast and reliable
          </div>
          <button type="button" className="theme-toggle" onClick={toggleTheme} aria-label="Toggle dark mode">
            {theme === 'dark' ? <SunMedium size={18} aria-hidden /> : <Moon size={18} aria-hidden />}
          </button>
        </div>
        <h1 className="hero__title">Shorten URLs</h1>
        <p className="hero__subtitle">Create reliable share-ready links with instant feedback and a polished workspace.</p>
      </header>
      <main className="workspace" aria-live="polite">
        <section className="panel">
          <ShortenUrlForm onSubmit={handleShorten} pending={pending} error={error} />
          <div className="divider" role="presentation" />
          <ShortLinkList items={links} />
        </section>
      </main>
    </div>
  );
}

type Theme = 'light' | 'dark';

function detectInitialTheme(): Theme {
  if (typeof window === 'undefined') {
    return 'light';
  }

  const stored = localStorage.getItem('shortly-theme') as Theme | null;
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }

  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  return prefersDark ? 'dark' : 'light';
}
