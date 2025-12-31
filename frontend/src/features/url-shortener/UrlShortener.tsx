import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ArrowRight, Link as LinkIcon, Loader2, Copy, Check, CheckCircle, XCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card } from '@/components/ui/card';
import { Toast } from '@/components/Toast';
import type { ToastType } from '@/components/Toast';
import { urlService } from '@/api/urlService';
import type { ShortenUrlResponse } from '@/types';

type ValidationState = 'empty' | 'valid' | 'invalid';

export function UrlShortener() {
  const [url, setUrl] = useState('');
  const [customCode, setCustomCode] = useState('');
  const [showCustomCode, setShowCustomCode] = useState(false);
  const [customCodeValidation, setCustomCodeValidation] = useState<ValidationState>('empty');
  const [result, setResult] = useState<ShortenUrlResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: ToastType; visible: boolean }>({
    message: '',
    type: 'success',
    visible: false,
  });

  const showToast = (message: string, type: ToastType) => {
    setToast({ message, type, visible: true });
  };

  const hideToast = () => {
    setToast(prev => ({ ...prev, visible: false }));
  };

  const validateCustomCode = (code: string): ValidationState => {
    if (!code) return 'empty';

    const pattern = /^[a-zA-Z0-9_-]+$/;
    const isValidLength = code.length >= 3 && code.length <= 20;
    const isValidPattern = pattern.test(code);

    return isValidLength && isValidPattern ? 'valid' : 'invalid';
  };

  const handleCustomCodeChange = (value: string) => {
    setCustomCode(value);
    setCustomCodeValidation(validateCustomCode(value));
  };

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
      const response = await urlService.shortenUrl({
        originalUrl: url,
        customCode: customCode || undefined
      });
      setResult(response);
      setUrl('');
      setCustomCode('');
      setCustomCodeValidation('empty');
      setShowCustomCode(false);
    } catch (err: any) {
      console.error('URL 단축 실패:', err);
      const errorMessage = err.response?.data?.message || 'URL 단축에 실패했습니다.';
      setError(errorMessage);
      showToast(errorMessage, 'error');
    } finally {
      setLoading(false);
    }
  };

  const copyToClipboard = () => {
    if (!result) return;
    const shortUrl = urlService.getRedirectUrl(result.shortCode);
    navigator.clipboard.writeText(shortUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <>
      <Toast
        message={toast.message}
        type={toast.type}
        isVisible={toast.visible}
        onClose={hideToast}
      />

      <div className="w-full max-w-2xl mx-auto">
        <motion.form
          onSubmit={handleSubmit}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: "easeOut" }}
          className="relative mb-12"
        >
          <div className="flex flex-col gap-4">
            <div className="flex flex-col sm:flex-row gap-4 items-center">
              <div className="relative w-full group">
                <div className="absolute left-4 top-1/2 -translate-y-1/2 text-muted-foreground group-focus-within:text-primary transition-colors z-10">
                  <LinkIcon className="w-5 h-5" />
                </div>
                <Input
                  type="text"
                  placeholder="https://shortly.com"
                  value={url}
                  onChange={(e) => setUrl(e.target.value)}
                  className="pl-12 h-16 text-lg bg-background/80 backdrop-blur-sm border-2 border-border/50 shadow-sm transition-all focus:ring-4 focus:ring-primary/20 focus:border-primary rounded-xl"
                  disabled={loading}
                />
              </div>
              <Button
                type="submit"
                size="lg"
                className="h-16 px-10 min-w-[160px] font-bold text-lg shadow-xl shadow-primary/20 transition-all hover:translate-y-[-2px] active:translate-y-[0px] bg-primary hover:bg-primary/90 text-white rounded-xl"
                disabled={loading || !url}
              >
                {loading ? (
                  <Loader2 className="w-6 h-6 animate-spin" />
                ) : (
                  <>
                    Shortly Now! <ArrowRight className="ml-2 w-5 h-5" />
                  </>
                )}
              </Button>
            </div>

            <div className="flex items-center justify-between px-2">
              <button
                type="button"
                onClick={() => setShowCustomCode(!showCustomCode)}
                className="flex items-center gap-3 group focus:outline-none"
              >
                <div className={`relative w-10 h-6 rounded-full transition-colors duration-300 ${showCustomCode ? 'bg-primary' : 'bg-input shadow-inner'
                }`}>
                  <div className={`absolute top-1 left-1 w-4 h-4 bg-white rounded-full shadow-sm transition-transform duration-300 ${showCustomCode ? 'translate-x-4' : 'translate-x-0'
                  }`} />
                </div>
                <span className={`text-base font-medium transition-colors ${showCustomCode ? 'text-primary' : 'text-muted-foreground group-hover:text-foreground'
                }`}>
                  단축 URL 직접 입력
                </span>
              </button>
            </div>

            <AnimatePresence>
              {showCustomCode && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  transition={{ duration: 0.3, ease: 'easeInOut' }}
                  className="relative w-full overflow-hidden"
                >
                  <div className="relative">
                    <div className="absolute left-4 top-1/2 -translate-y-1/2 text-muted-foreground font-medium select-none">
                      shortly.com/
                    </div>
                    <Input
                      type="text"
                      placeholder="custom"
                      value={customCode}
                      onChange={(e) => handleCustomCodeChange(e.target.value)}
                      className={`h-14 text-base pl-[110px] bg-background/50 shadow-sm transition-all focus:ring-2 focus:ring-primary/20 border-2 rounded-xl border-border/50 ${customCodeValidation === 'valid' ? 'border-green-500/50 focus:border-green-500' :
                        customCodeValidation === 'invalid' ? 'border-red-500/50 focus:border-red-500' :
                          'hover:border-primary/30'
                      }`}
                      disabled={loading}
                      maxLength={20}
                    />
                  </div>
                  {customCodeValidation !== 'empty' && (
                    <div className="absolute right-4 top-1/2 -translate-y-1/2">
                      {customCodeValidation === 'valid' ? (
                        <CheckCircle className="w-5 h-5 text-green-600" />
                      ) : (
                        <XCircle className="w-5 h-5 text-red-600" />
                      )}
                    </div>
                  )}
                  <p className={`text-xs mt-1 ml-1 transition-colors ${customCodeValidation === 'valid' ? 'text-green-600' :
                    customCodeValidation === 'invalid' ? 'text-red-600' :
                      'text-muted-foreground'
                  }`}>
                    {customCodeValidation === 'invalid'
                      ? '3-20자, 영문/숫자/하이픈/언더스코어만 허용'
                      : '3-20자, 영문/숫자/하이픈/언더스코어만 사용 가능'}
                  </p>
                </motion.div>
              )}
            </AnimatePresence>
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
            initial={{ opacity: 0, scale: 0.9, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            transition={{ type: "spring", stiffness: 200, damping: 20 }}
          >
            <Card className="p-8 shadow-2xl border-primary/10 bg-white/50 dark:bg-black/20 backdrop-blur-md rounded-2xl overflow-hidden relative">
              <div className="absolute top-0 left-0 w-full h-1 bg-primary"></div>

              <div className="space-y-6">
                <div className="flex items-center gap-3 text-base font-medium text-emerald-600 bg-emerald-50 dark:bg-emerald-950/30 px-4 py-2 rounded-full w-fit">
                  <div className="bg-emerald-500 rounded-full p-1">
                    <Check className="w-3 h-3 text-white" />
                  </div>
                  <span>단축 완료!</span>
                </div>

                <div className="flex items-center gap-4 bg-muted/50 p-2 pr-3 rounded-xl border border-border/50 group hover:border-primary/30 transition-colors">
                  <div className="bg-background px-4 py-3 rounded-lg border flex-1 text-primary font-semibold text-xl tracking-tight overflow-hidden text-ellipsis whitespace-nowrap">
                    {urlService.getRedirectUrl(result.shortCode)}
                  </div>

                  <Button
                    size="icon"
                    onClick={copyToClipboard}
                    title="Copy to clipboard"
                    className={`shrink-0 transition-all duration-300 ${copied ? 'bg-emerald-500 hover:bg-emerald-600' : 'bg-primary hover:bg-primary/90'} text-white w-12 h-12 rounded-lg shadow-md`}
                  >
                    {copied ? (
                      <Check className="h-6 w-6" />
                    ) : (
                      <Copy className="h-5 w-5" />
                    )}
                  </Button>
                </div>
              </div>
            </Card>
          </motion.div>
        )}
      </div>
    </>
  );
}
