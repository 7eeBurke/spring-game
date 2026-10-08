import '@testing-library/jest-dom/vitest';
import { webcrypto } from 'node:crypto';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import { resetFetcher } from '../api/client';

// jsdom's crypto lacks SubtleCrypto; use Node's Web Crypto implementation, as browsers provide.
if (!globalThis.crypto?.subtle || typeof globalThis.crypto.randomUUID !== 'function') {
  Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true });
}

afterEach(() => {
  cleanup();
  localStorage.clear();
  resetFetcher();
});
