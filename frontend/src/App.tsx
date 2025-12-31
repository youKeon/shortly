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
    <header className="border-b bg-background/50 backdrop-blur-sm sticky top-0 z-50">
      <div className="container mx-auto px-4 h-20 flex items-center justify-between">
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
      <div className={`${isDark ? 'dark' : ''} min-h-screen bg-background font-sans text-foreground selection:bg-primary/10`}>
        <Navigation isDark={isDark} onToggleTheme={toggleTheme} />

        {/* Background Logo Watermark */}
        <div className="fixed inset-0 z-0 flex items-center justify-center overflow-hidden pointer-events-none">
          <div className="relative w-[1000px] h-[1000px] opacity-[0.08] blur-3xl animate-pulse" style={{ animationDuration: '8s' }}>
            <img
              src="/logo.png"
              alt=""
              className="w-full h-full object-contain"
            />
          </div>
        </div>

        <main className="relative z-10 container mx-auto px-4 py-16">
          <div className="text-center max-w-2xl mx-auto mb-12 space-y-4">
            <h1 className="text-4xl sm:text-5xl font-extrabold tracking-tight lg:text-6xl mb-6">
              Make your links <br />
              <span className="text-primary">shorter & smarter.</span>
            </h1>
          </div>

          <Routes>
            <Route path="/" element={<UrlShortener />} />
          </Routes>
        </main>

        <footer className="py-8 text-center text-sm text-muted-foreground border-t mt-16">
          <p className="flex items-center justify-center gap-2">
            <span>&copy; 20255</span>
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
