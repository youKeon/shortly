import { useCallback, useMemo, useState, type FormEvent } from 'react';
import { TextField } from '@shared/components/TextField';
import { Button } from '@shared/components/Button';
import { isValidUrl } from '@shared/utils/url';

interface ShortenUrlFormProps {
  onSubmit: (url: string) => Promise<void> | void;
  pending?: boolean;
  error?: string | null;
}

export function ShortenUrlForm({ onSubmit, pending = false, error = null }: ShortenUrlFormProps) {
  const [value, setValue] = useState('');
  const [touched, setTouched] = useState(false);

  const validationMessage = useMemo(() => {
    if (!touched && !error) return null;
    if (!value) {
      return 'Enter a URL to shorten.';
    }

    if (!isValidUrl(value)) {
      return 'Use a valid https:// or http:// URL.';
    }

    return error;
  }, [touched, value, error]);

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      setTouched(true);

      if (!value || !isValidUrl(value) || pending) {
        return;
      }

      await onSubmit(value.trim());
      setValue('');
      setTouched(false);
    },
    [onSubmit, pending, value]
  );

  return (
    <form className="shorten-form" onSubmit={handleSubmit}>
      <div className="shorten-form__eyebrow">Paste a link to get started</div>
      <TextField
        label="Shorten a long link"
        name="originalUrl"
        placeholder="https://example.com/my-product"
        value={value}
        onChange={(event) => setValue(event.target.value)}
        onBlur={() => setTouched(true)}
        error={validationMessage}
        autoComplete="off"
        autoCorrect="off"
        spellCheck={false}
        required
      />
      <Button type="submit" block disabled={pending} aria-busy={pending}>
        {pending ? 'Shortening...' : 'Shorten URL'}
      </Button>
    </form>
  );
}
