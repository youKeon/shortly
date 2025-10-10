import { useState } from 'react';
import type { ShortLink } from '@shared/types/shortLink';
import { Button } from '@shared/components/Button';

interface ShortLinkListProps {
  items: ShortLink[];
}

export function ShortLinkList({ items }: ShortLinkListProps) {
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const handleCopy = async (value: string, id: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopiedId(id);
      window.setTimeout(() => setCopiedId(null), 2000);
    } catch (error) {
      console.error('Failed to copy to clipboard', error);
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
    <ul className="short-link-list">
      {items.map((item) => (
        <li key={item.id} className="short-link-card">
          <div className="short-link-card__meta">
            <span className="short-link-card__label">Original</span>
            <a className="short-link-card__original" href={item.originalUrl} target="_blank" rel="noreferrer">
              {truncateUrl(item.originalUrl)}
            </a>
          </div>
          <div className="short-link-card__meta short-link-card__meta--accent">
            <span className="short-link-card__label">Short link</span>
            <a className="short-link-card__short" href={item.shortUrl} target="_blank" rel="noreferrer">
              {item.shortUrl}
            </a>
          </div>
          <Button
            type="button"
            variant={copiedId === item.id ? 'secondary' : 'primary'}
            className="short-link-card__button"
            onClick={() => handleCopy(item.shortUrl, item.id)}
          >
            {copiedId === item.id ? 'Copied!' : 'Copy'}
          </Button>
        </li>
      ))}
    </ul>
  );
}

function truncateUrl(value: string) {
  if (value.length <= 54) {
    return value;
  }
  return `${value.slice(0, 40)}...${value.slice(-10)}`;
}
