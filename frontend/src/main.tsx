import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@app';
import { attemptShortCodeRedirect } from '@app/redirect';
import { Toaster } from 'sonner';
import '@app/styles/global.css';

const rootElement = document.getElementById('root');

if (!rootElement) {
  throw new Error('Root element #root not found');
}

if (!attemptShortCodeRedirect()) {
  createRoot(rootElement).render(
    <StrictMode>
      <>
        <Toaster richColors position="top-center" closeButton expand />
        <App />
      </>
    </StrictMode>
  );
}
