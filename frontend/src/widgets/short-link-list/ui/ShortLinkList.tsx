import { useMemo, useState } from 'react';
import type { ShortLink } from '@shared/types/shortLink';
import { formatRelativeTime } from '@shared/utils/date';
import { toast } from 'sonner';
import { Clipboard } from 'lucide-react';

interface ShortLinkListProps {
  items: ShortLink[];
}

export function ShortLinkList({ items }: ShortLinkListProps) {
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [copyCount, setCopyCount] = useState<Record<string, number>>({});

  const sortedItems = useMemo(() => {
    return [...items].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }, [items]);

  const handleCopy = async (value: string, id: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopiedId(id);
      window.setTimeout(() => setCopiedId(null), 2000);
      setCopyCount((prev) => ({
        ...prev,
        [id]: (prev[id] ?? 0) + 1
      }));
      toast.success('Link copied to clipboard', {
        description: value
      });
    } catch (error) {
      console.error('Failed to copy to clipboard', error);
      toast.error('Copy failed', { description: 'Try again or copy manually.' });
    }
  };

  if (!items.length) {
    return (
      <div className="short-link-list__empty">
        <h3>No links yet</h3>
        <p>Submit a URL to generate your first short link and it will appear here.</p>
      </div>
    );
  }

  return (
    <ul className="short-link-list" aria-live="polite">
      {sortedItems.map((item) => {
        const copies = copyCount[item.id] ?? 0;

        return (
          <li key={item.id} className="short-link-card">
            <div className="short-link-card__meta short-link-card__meta--accent">
              <span className="short-link-card__label">Short link</span>
              <a className="short-link-card__short" href={item.shortUrl} target="_blank" rel="noreferrer">
                {item.shortUrl}
              </a>
            </div>
            <div className="short-link-card__meta short-link-card__meta--muted">
              <span className="short-link-card__label">Created</span>
              <time dateTime={item.createdAt}>{formatRelativeTime(item.createdAt)}</time>
              {copies > 0 && <span className="short-link-card__badge">Copied x{copies}</span>}
            </div>
            <button
              type="button"
              className="short-link-card__icon"
              onClick={() => handleCopy(item.shortUrl, item.id)}
              aria-label="Copy short link"
            >
              <Clipboard size={18} aria-hidden />
            </button>
          </li>
        );
      })}
    </ul>
  );
}
