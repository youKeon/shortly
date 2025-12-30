import { useState } from 'react';
import { motion } from 'framer-motion';
import { ArrowRight, Link as LinkIcon, Loader2, Copy, Check } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card } from '@/components/ui/card';
import { urlService } from '@/api/urlService';
import type { ShortenUrlResponse } from '@/types';
import { toast, Toaster } from 'sonner';

export function UrlShortener() {
  const [url, setUrl] = useState('');
  const [result, setResult] = useState<ShortenUrlResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!url) return;

    // URL validation
    try {
      new URL(url);
      setError('');
    } catch {
      setError('올바른 URL을 입력해주세요. (예: https://example.com)');
      return;
    }

    setLoading(true);

    try {
      const response = await urlService.shortenUrl({ originalUrl: url });
      setResult(response);
      setUrl('');
      toast.success('URL이 성공적으로 단축되었습니다!');
    } catch (err: any) {
      console.error('URL 단축 실패:', err);
      const errorMessage = err.response?.data?.message || 'URL 단축에 실패했습니다.';
      setError(errorMessage);
      toast.error(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const copyToClipboard = () => {
    if (!result) return;
    const shortUrl = urlService.getRedirectUrl(result.shortCode);
    navigator.clipboard.writeText(shortUrl);
    setCopied(true);
    toast.success('URL이 클립보드에 복사되었습니다.');
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="w-full max-w-2xl mx-auto">
      <Toaster position="top-center" />

      <motion.form
        onSubmit={handleSubmit}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="relative mb-12"
      >
        <div className="flex flex-col sm:flex-row gap-4 items-center">
          <div className="relative w-full">
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
              <LinkIcon className="w-5 h-5" />
            </div>
            <Input
              type="text"
              placeholder="https://shortly.com"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              className="pl-10 h-14 text-lg bg-background border-input shadow-sm transition-all focus:ring-2 focus:ring-primary/20"
              disabled={loading}
            />
          </div>
          <Button
            type="submit"
            size="lg"
            className="h-14 px-8 min-w-[140px] font-semibold text-lg shadow-lg transition-all hover:translate-y-[-2px] bg-blue-500 hover:bg-blue-600 text-white"
            disabled={loading || !url}
          >
            {loading ? (
              <Loader2 className="w-5 h-5 animate-spin" />
            ) : (
              <>
                Shortly Now <ArrowRight className="ml-2 w-5 h-5" />
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

      {result && (
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.3 }}
        >
          <Card className="p-6 shadow-lg">
            <div className="space-y-4">
              <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
                <Check className="w-4 h-4 text-green-600" />
                <span>단축 완료!</span>
              </div>

              <div className="flex items-center gap-3 bg-blue-50 p-4 rounded-lg border border-blue-200">
                <a
                  href={urlService.getRedirectUrl(result.shortCode)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex-1 text-blue-600 font-semibold text-lg hover:text-blue-700 transition-colors break-all"
                >
                  {urlService.getRedirectUrl(result.shortCode)}
                </a>
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={copyToClipboard}
                  title="클립보드에 복사"
                  className="shrink-0 hover:bg-blue-100"
                >
                  {copied ? (
                    <Check className="h-5 w-5 text-green-600" />
                  ) : (
                    <Copy className="h-5 w-5 text-blue-600" />
                  )}
                </Button>
              </div>
            </div>
          </Card>
        </motion.div>
      )}
    </div>
  );
}
