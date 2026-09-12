import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import './index.css';
import './shell.css';

import { Providers } from './app/providers';
import { AppRoutes } from './app/routes';

const container = document.getElementById('root');

if (!container) {
  throw new Error('Root container is missing from index.html');
}

createRoot(container).render(
  <StrictMode>
    <Providers>
      <AppRoutes />
    </Providers>
  </StrictMode>,
);
