import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '../ui/dialog';
import { Card, CardContent, CardHeader, CardTitle } from '../ui/card';
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';
import { MousePointerClick, Calendar, BarChart as ChartIcon } from 'lucide-react';
import { Button } from '../ui/button';
import { Separator } from '../ui/separator';

interface StatData {
  date: string;
  clicks: number;
}

interface LinkStatsProps {
  isOpen: boolean;
  onClose: () => void;
  shortUrl: string;
  originalUrl: string;
  totalClicks: number;
  history: StatData[];
}

export function LinkStats({ isOpen, onClose, shortUrl, originalUrl, totalClicks, history }: LinkStatsProps) {
  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-[600px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-xl">
            <ChartIcon className="w-5 h-5" /> 통계 대시보드
          </DialogTitle>
          <DialogDescription>
            단축 URL에 대한 상세 통계입니다.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6 py-4">
          <div className="grid grid-cols-2 gap-4">
             <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    총 클릭 수
                  </CardTitle>
                  <MousePointerClick className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{totalClicks}</div>
                </CardContent>
             </Card>
             <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    생성일
                  </CardTitle>
                  <Calendar className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">Today</div>
                </CardContent>
             </Card>
          </div>

          <div className="space-y-2">
             <h4 className="text-sm font-medium">클릭 추이 (최근 7일)</h4>
             <div className="h-[200px] w-full border rounded-lg p-4 bg-card/50">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={history}>
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
          </div>

          <div className="space-y-1">
             <h4 className="text-sm font-medium mb-2">링크 정보</h4>
             <div className="text-sm border p-3 rounded-md bg-muted/30">
               <div className="flex justify-between py-1">
                  <span className="text-muted-foreground">Short:</span>
                  <span className="font-mono">{shortUrl}</span>
               </div>
               <Separator className="my-2" />
               <div className="flex flex-col gap-1 py-1">
                  <span className="text-muted-foreground">Original:</span>
                  <span className="text-xs text-muted-foreground break-all">{originalUrl}</span>
               </div>
             </div>
          </div>
        </div>

        <div className="flex justify-end">
          <Button onClick={onClose}>닫기</Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
