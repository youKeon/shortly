import { useState } from 'react';
import { ShortenerForm } from './components/url-shortener/ShortenerForm';
import { LinkList, LinkData } from './components/url-shortener/LinkList';
import { LinkStats } from './components/url-shortener/LinkStats';
import { Toaster } from 'sonner';
import { Link2, ChartBar } from 'lucide-react';

// Mock data generator
const generateId = () => Math.random().toString(36).substring(2, 8);
const generateHistory = () => {
  const history = [];
  const today = new Date();
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    history.push({
      date: d.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' }),
      clicks: Math.floor(Math.random() * 50) + 10,
    });
  }
  return history;
};

export default function App() {
  const [links, setLinks] = useState<LinkData[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedLink, setSelectedLink] = useState<LinkData | null>(null);
  const [isStatsOpen, setIsStatsOpen] = useState(false);

  const handleShorten = async (originalUrl: string) => {
    setLoading(true);
    // Simulate network request
    await new Promise((resolve) => setTimeout(resolve, 800));

    const newLink: LinkData = {
      id: generateId(),
      originalUrl,
      shortUrl: `https://sho.rt/${generateId()}`,
      clicks: Math.floor(Math.random() * 100), // Start with some random clicks for demo
      createdAt: new Date().toISOString(),
      history: generateHistory(),
    };

    setLinks((prev) => [newLink, ...prev]);
    setLoading(false);
  };

  const handleViewStats = (link: LinkData) => {
    setSelectedLink(link);
    setIsStatsOpen(true);
  };

  return (
    <div className="min-h-screen bg-background font-sans text-foreground selection:bg-primary/10">
      <Toaster position="top-center" />
      
      {/* Header */}
      <header className="border-b bg-background/50 backdrop-blur-sm sticky top-0 z-50">
        <div className="container mx-auto px-4 h-16 flex items-center justify-between">
          <div className="flex items-center gap-2 font-bold text-xl tracking-tight">
            <Link2 className="w-6 h-6" />
            <span>Shortly</span>
          </div>
          <nav className="flex items-center gap-4">
            <button 
              className="p-2 rounded-full hover:bg-muted/50 transition-colors text-muted-foreground hover:text-foreground"
              title="통계 대시보드"
            >
              <ChartBar className="w-5 h-5" />
            </button>
            <a href="#" className="text-sm font-medium text-muted-foreground hover:text-foreground transition-colors">
              GitHub
            </a>
          </nav>
        </div>
      </header>

      <main className="container mx-auto px-4 py-16 flex flex-col items-center">
        <div className="text-center max-w-2xl mb-12 space-y-4">
          <h1 className="text-4xl sm:text-5xl font-extrabold tracking-tight lg:text-6xl mb-6">
            Make your links <br />
            <span className="text-primary/80">shorter & smarter.</span>
          </h1>
        </div>

        <ShortenerForm onShorten={handleShorten} isLoading={loading} />

        <LinkList links={links} />

        {selectedLink && (
          <LinkStats 
            isOpen={isStatsOpen}
            onClose={() => setIsStatsOpen(false)}
            shortUrl={selectedLink.shortUrl}
            originalUrl={selectedLink.originalUrl}
            totalClicks={selectedLink.clicks}
            history={selectedLink.history}
          />
        )}
      </main>

      <footer className="py-8 text-center text-sm text-muted-foreground">
        <p>&copy; 2024 Shortly. All rights reserved.</p>
      </footer>
    </div>
  );
}
