import React, { useState } from 'react';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { ArrowRight, Link as LinkIcon, Loader2 } from 'lucide-react';
import { motion } from 'motion/react';

interface ShortenerFormProps {
  onShorten: (url: string) => Promise<void>;
  isLoading: boolean;
}

export function ShortenerForm({ onShorten, isLoading }: ShortenerFormProps) {
  const [url, setUrl] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!url) return;

    // Basic URL validation
    try {
      new URL(url);
      setError('');
    } catch {
      setError('올바른 URL을 입력해주세요. (예: https://example.com)');
      return;
    }

    await onShorten(url);
    setUrl('');
  };

  return (
    <div className="w-full max-w-2xl mx-auto mb-12">
      <motion.form
        onSubmit={handleSubmit}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="relative"
      >
        <div className="flex flex-col sm:flex-row gap-4 items-center">
          <div className="relative w-full">
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
              <LinkIcon className="w-5 h-5" />
            </div>
            <Input
              type="text"
              placeholder="URL"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              className="pl-10 h-14 text-lg bg-background border-input shadow-sm transition-all focus:ring-2 focus:ring-primary/20"
              disabled={isLoading}
            />
          </div>
          <Button
            type="submit"
            size="lg"
            className="h-14 px-8 min-w-[140px] font-semibold text-lg shadow-md transition-all hover:translate-y-[-2px]"
            disabled={isLoading || !url}
          >
            {isLoading ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <>
                단축하기 <ArrowRight className="ml-2 w-5 h-5" />
              </>
            )}
          </Button>
        </div>
        {error && (
          <motion.p
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            className="text-destructive text-sm mt-2 ml-1"
          >
            {error}
          </motion.p>
        )}
      </motion.form>
    </div>
  );
}
