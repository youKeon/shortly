import { useState } from 'react';
import { Link2, Activity } from 'lucide-react';
import { App as LinksApp } from './App';
import { LoadTestDashboard } from '@features/dashboard';

type Page = 'links' | 'dashboard';

export function AppRouter() {
  const [currentPage, setCurrentPage] = useState<Page>(() => {
    const path = window.location.hash.slice(1);
    return path === '/dashboard' ? 'dashboard' : 'links';
  });

  const navigate = (page: Page) => {
    setCurrentPage(page);
    window.location.hash = page === 'dashboard' ? '/dashboard' : '/';
  };

  if (currentPage === 'dashboard') {
    return <LoadTestDashboard onNavigateToLinks={() => navigate('links')} />;
  }

  return (
    <>
      <nav className="app-nav">
        <button 
          className="app-nav__link app-nav__link--active"
          onClick={() => navigate('links')}
        >
          <Link2 size={20} />
          <span>Links</span>
        </button>
        <button 
          className="app-nav__link"
          onClick={() => navigate('dashboard')}
        >
          <Activity size={20} />
          <span>Load Tests</span>
        </button>
      </nav>
      <LinksApp />
    </>
  );
}

