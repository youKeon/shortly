import { useState } from 'react';
import { motion } from 'framer-motion';
import { Search, MousePointerClick, Calendar, BarChart as ChartIcon, ExternalLink } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';
import { urlService } from '@/api/urlService';
import type { ClickStats, ClickDetails } from '@/types';
import { toast, Toaster } from 'sonner';

export function Analytics() {
  const [shortCode, setShortCode] = useState('');
  const [stats, setStats] = useState<ClickStats | null>(null);
  const [clickDetails, setClickDetails] = useState<ClickDetails | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!shortCode.trim()) {
      setError('Short Code를 입력해주세요.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const [statsData, detailsData] = await Promise.all([
        urlService.getStats(shortCode),
        urlService.getClickDetails(shortCode),
      ]);

      setStats(statsData);
      setClickDetails(detailsData);
      toast.success('통계를 성공적으로 불러왔습니다!');
    } catch (err: any) {
      console.error('통계 조회 실패:', err);
      const errorMessage = err.response?.data?.message || '통계 조회에 실패했습니다.';
      setError(errorMessage);
      toast.error(errorMessage);
      setStats(null);
      setClickDetails(null);
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setShortCode('');
    setStats(null);
    setClickDetails(null);
    setError('');
  };

  // 차트 데이터 생성 (최근 7일)
  const getChartData = () => {
    if (!clickDetails) return [];

    const last7Days: { date: string; clicks: number }[] = [];
    for (let i = 6; i >= 0; i--) {
      const date = new Date();
      date.setDate(date.getDate() - i);
      last7Days.push({
        date: date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' }),
        clicks: 0
      });
    }

    // 클릭 데이터를 날짜별로 집계
    clickDetails.clicks.forEach(click => {
      const clickDate = new Date(click.clickedAt);
      const dateStr = clickDate.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' });
      const dayData = last7Days.find(d => d.date === dateStr);
      if (dayData) {
        dayData.clicks++;
      }
    });

    return last7Days;
  };

  return (
    <div className="w-full max-w-4xl mx-auto">
      <Toaster position="top-center" />

      <motion.form
        onSubmit={handleSearch}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="mb-12"
      >
        <div className="flex flex-col sm:flex-row gap-4">
          <div className="relative flex-1">
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
              <Search className="w-5 h-5" />
            </div>
            <Input
              type="text"
              placeholder="Short Code 입력 (예: aB12cD)"
              value={shortCode}
              onChange={(e) => setShortCode(e.target.value)}
              className="pl-10 h-14 text-lg bg-background border-input font-mono shadow-sm"
              disabled={loading}
            />
          </div>
          <div className="flex gap-2">
            <Button
              type="submit"
              size="lg"
              className="h-14 px-8 font-semibold text-lg shadow-md"
              disabled={loading}
            >
              {loading ? '조회 중...' : '조회하기'}
            </Button>
            {(stats || error) && (
              <Button
                type="button"
                variant="outline"
                size="lg"
                onClick={handleReset}
                className="h-14 px-6"
                disabled={loading}
              >
                초기화
              </Button>
            )}
          </div>
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

      {stats && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="space-y-6"
        >
          {/* 통계 카드 */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  총 클릭 수
                </CardTitle>
                <MousePointerClick className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{stats.totalClicks.toLocaleString()}</div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  최근 24시간
                </CardTitle>
                <Calendar className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{stats.clicksLast24Hours.toLocaleString()}</div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  최근 7일
                </CardTitle>
                <ChartIcon className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{stats.clicksLast7Days.toLocaleString()}</div>
              </CardContent>
            </Card>
          </div>

          {/* 차트 */}
          <Card className="p-6">
            <h3 className="text-lg font-semibold mb-4">클릭 추이 (최근 7일)</h3>
            <div className="h-[250px] w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={getChartData()}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="hsl(var(--border))" />
                  <XAxis
                    dataKey="date"
                    fontSize={12}
                    tickLine={false}
                    axisLine={false}
                    stroke="hsl(var(--muted-foreground))"
                  />
                  <YAxis
                    fontSize={12}
                    tickLine={false}
                    axisLine={false}
                    stroke="hsl(var(--muted-foreground))"
                    allowDecimals={false}
                  />
                  <Tooltip
                    cursor={{ fill: 'hsl(var(--muted)/0.4)' }}
                    contentStyle={{
                      backgroundColor: 'hsl(var(--popover))',
                      borderColor: 'hsl(var(--border))',
                      borderRadius: '6px'
                    }}
                  />
                  <Bar dataKey="clicks" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </Card>

          {/* 링크 정보 */}
          <Card className="p-6">
            <h3 className="text-lg font-semibold mb-4">링크 정보</h3>
            <div className="space-y-4 text-sm">
              <div className="flex items-center justify-between py-2">
                <span className="text-muted-foreground">Short Code:</span>
                <code className="font-mono bg-muted/50 px-3 py-1 rounded">
                  {stats.shortCode}
                </code>
              </div>
              <Separator />
              <div className="flex items-center justify-between py-2">
                <span className="text-muted-foreground">단축 URL:</span>
                <a
                  href={urlService.getRedirectUrl(stats.shortCode)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center gap-1 text-primary hover:underline font-medium"
                >
                  {urlService.getRedirectUrl(stats.shortCode)}
                  <ExternalLink className="w-3 h-3" />
                </a>
              </div>
            </div>
          </Card>

          {/* 클릭 상세 기록 */}
          {clickDetails && clickDetails.clicks.length > 0 && (
            <Card className="p-6">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-semibold">클릭 상세 기록</h3>
                <span className="text-sm text-muted-foreground">
                  총 {clickDetails.totalCount.toLocaleString()}건
                </span>
              </div>
              <div className="space-y-2 max-h-[300px] overflow-y-auto">
                {clickDetails.clicks.map((click, index) => (
                  <div
                    key={index}
                    className="flex items-center justify-between py-2 px-3 rounded-md hover:bg-muted/50 transition-colors"
                  >
                    <span className="text-sm font-medium text-muted-foreground">
                      #{clickDetails.clicks.length - index}
                    </span>
                    <span className="text-sm font-mono text-foreground/80">
                      {new Date(click.clickedAt).toLocaleString('ko-KR', {
                        year: 'numeric',
                        month: '2-digit',
                        day: '2-digit',
                        hour: '2-digit',
                        minute: '2-digit',
                        second: '2-digit',
                      })}
                    </span>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </motion.div>
      )}
    </div>
  );
}
