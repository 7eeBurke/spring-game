import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '../styles/tokens.css';
import '../styles/base.css';
import { GAME_TITLE } from '../config';
import { PreviewApp } from './PreviewApp';

document.title = `Design preview — ${GAME_TITLE}`;

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <PreviewApp />
  </StrictMode>,
);
