/** 32 random bytes as unpadded base64url: exactly the 43-character run token the server expects. */
export function generateRunToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return toBase64Url(bytes);
}

const TOKEN_FORMAT = /^[A-Za-z0-9_-]{43}$/;
const UUID_FORMAT = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isRunToken(value: string): boolean {
  return TOKEN_FORMAT.test(value);
}

export function isUuid(value: string): boolean {
  return UUID_FORMAT.test(value);
}

export function newUuid(): string {
  return crypto.randomUUID();
}

export function toBase64Url(bytes: Uint8Array): string {
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
