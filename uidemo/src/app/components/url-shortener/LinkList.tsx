import { Button } from '../ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../ui/table';
import { Copy, ChartBar, ExternalLink } from 'lucide-react';
import { toast } from 'sonner';
import { motion, AnimatePresence } from 'motion/react';

export interface LinkData {
  id: string;
  originalUrl: string;
  shortUrl: string;
  clicks: number;
  createdAt: string;
  history: { date: string; clicks: number }[];
}

export function LinkList({ links }: { links: LinkData[] }) {
  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    toast.success('URL이 클립보드에 복사되었습니다.');
  };

  if (links.length === 0) {
    return null;
  }

  return (
    <div className="w-full max-w-4xl mx-auto mt-12">
      <h2 className="text-xl font-semibold mb-4 px-1">최근 단축 URL</h2>
      <div className="rounded-xl border bg-card shadow-sm overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[300px]">Original URL</TableHead>
              <TableHead>Short URL</TableHead>
              <TableHead className="text-right w-[100px]"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <AnimatePresence>
              {links.map((link) => (
                <motion.tr
                  key={link.id}
                  layout
                  initial={{ opacity: 0, x: -20 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: 20 }}
                  className="group hover:bg-muted/50 transition-colors"
                >
                  <TableCell className="font-medium">
                    <div className="truncate max-w-[250px] text-muted-foreground" title={link.originalUrl}>
                      {link.originalUrl}
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-primary font-medium">{link.shortUrl}</span>
                    </div>
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => copyToClipboard(link.shortUrl)}
                      title="주소 복사"
                    >
                      <Copy className="h-4 w-4" />
                    </Button>
                  </TableCell>
                </motion.tr>
              ))}
            </AnimatePresence>
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
