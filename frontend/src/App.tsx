import { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';
import { UrlShortener } from './features/url-shortener/UrlShortener';
import { Github, Moon, Sun } from 'lucide-react';

interface NavigationProps {
  isDark: boolean;
  onToggleTheme: () => void;
}

function Navigation({ isDark, onToggleTheme }: NavigationProps) {
  return (
    <header className="fixed top-0 left-0 right-0 z-50 border-b border-black/10 dark:border-white/10 bg-transparent">
      <div className="container mx-auto px-4 h-24 flex items-center justify-between">
        <Link to="/" className="group hover:opacity-90 transition-opacity">
          <h1 className="text-5xl text-primary transition-transform group-hover:scale-105" style={{ fontFamily: 'Pacifico, cursive' }}>
            Shortly
          </h1>
        </Link>
        <nav className="flex items-center gap-6">
          <button
            onClick={onToggleTheme}
            className="text-sm font-medium transition-colors hover:text-foreground/80 text-foreground/60 flex items-center gap-1.5"
            aria-label="Toggle theme"
          >
            {isDark ? <Sun className="w-5 h-5" /> : <Moon className="w-5 h-5" />}
          </button>
          <a
            href="https://github.com/youKeon/shortly"
            target="_blank"
            rel="noopener noreferrer"
            className="text-sm font-medium transition-colors hover:text-foreground/80 text-foreground/60 flex items-center gap-1.5"
          >
            <Github className="w-5 h-5" />
          </a>
        </nav>
      </div>
    </header>
  );
}

function App() {
  const [isDark, setIsDark] = useState<boolean>(() => {
    const saved = localStorage.getItem('theme');
    return saved ? saved === 'dark' : true;
  });

  useEffect(() => {
    localStorage.setItem('theme', isDark ? 'dark' : 'light');
  }, [isDark]);

  const toggleTheme = () => {
    setIsDark(prev => !prev);
  };

  return (
    <Router>
      <div className={`${isDark ? 'dark' : ''} min-h-screen bg-background font-sans text-foreground selection:bg-primary/20 overflow-x-hidden`}>
        <Navigation isDark={isDark} onToggleTheme={toggleTheme} />

        {/* Background Logo Watermark */}
        {/* Dynamic Animated Background */}
        <div className="fixed inset-0 z-0 pointer-events-none overflow-hidden">
          <div className="absolute top-0 left-1/4 w-96 h-96 bg-primary/20 rounded-full mix-blend-multiply filter blur-3xl opacity-30 animate-blob"></div>
          <div className="absolute top-0 right-1/4 w-96 h-96 bg-secondary/30 rounded-full mix-blend-multiply filter blur-3xl opacity-30 animate-blob animation-delay-2000"></div>
          <div className="absolute -bottom-32 left-1/3 w-96 h-96 bg-purple-500/20 rounded-full mix-blend-multiply filter blur-3xl opacity-30 animate-blob animation-delay-4000"></div>
        </div>

        <main className="relative z-10 container mx-auto px-4 pt-32 pb-16">
          <div className="text-center max-w-2xl mx-auto mb-12 space-y-4">
            <h1 className="text-5xl sm:text-6xl font-extrabold tracking-tight lg:text-7xl mb-6 text-foreground animate-in fade-in slide-in-from-bottom-4 duration-1000">
              Make your links <br />
              <span className="text-primary">shorter & smarter.</span>
            </h1>
          </div>

          <Routes>
            <Route path="/" element={<UrlShortener />} />
          </Routes>
        </main>

        <footer className="py-8 text-center text-sm text-muted-foreground border-t border-border/40 mt-24 relative z-10 glass-footer">
          <p className="flex items-center justify-center gap-2">
            <span>&copy; 2025</span>
            <span className="text-primary text-lg" style={{ fontFamily: 'Pacifico, cursive' }}>
              Shortly
            </span>
          </p>
        </footer>
      </div>
    </Router>
  );
}

export default App;
