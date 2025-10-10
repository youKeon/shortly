import { useCallback, useState } from 'react';
import { ShortenUrlForm } from '@features/shorten-url';
import { ShortLinkList } from '@widgets/short-link-list';
import { createShortLink } from '@shared/api/urlClient';
import type { ShortLink } from '@shared/types/shortLink';

export function App() {
  const [links, setLinks] = useState<ShortLink[]>([]);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleShorten = useCallback(async (url: string) => {
    setPending(true);
    setError(null);

    try {
      const result = await createShortLink(url);
      setLinks((prev) => [result, ...prev].slice(0, 5));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to shorten URL.');
    } finally {
      setPending(false);
    }
  }, []);

  return (
    <div className="app-shell">
      <header className="hero">
        <div className="hero__pill">Fast and reliable</div>
        <h1 className="hero__title">Shorten URLs in seconds</h1>
        <p className="hero__subtitle">
          Transform long links into short, shareable URLs with a modern and minimal interface.
        </p>
      </header>
      <main className="workspace" aria-live="polite">
        <section className="panel">
          <ShortenUrlForm onSubmit={handleShorten} pending={pending} error={error} />
          <div className="divider" role="presentation" />
          <ShortLinkList items={links} />
        </section>
      </main>
      <footer className="footer">
        <span className="footer__brand">bitly</span>
        <span className="footer__note">Built with React, TypeScript, and Spring Boot APIs.</span>
      </footer>
    </div>
  );
}
