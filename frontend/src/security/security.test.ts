import { describe, expect, it } from 'vitest';
import { decodeRecoveryCode, encodeRecoveryCode } from './recovery';
import { generateRunToken, isRunToken, isUuid, newUuid, toBase64Url } from './tokens';

describe('run tokens', () => {
  it('are 43 base64url characters from 32 random bytes', () => {
    const token = generateRunToken();
    expect(token).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(isRunToken(token)).toBe(true);
  });

  it('are different every time', () => {
    const tokens = new Set(Array.from({ length: 200 }, generateRunToken));
    expect(tokens.size).toBe(200);
  });

  it('encode bytes as unpadded base64url', () => {
    expect(toBase64Url(new Uint8Array([0xfb, 0xff, 0xbf]))).toBe('-_-_');
    expect(toBase64Url(new Uint8Array([1]))).toBe('AQ');
  });

  it('reject malformed values', () => {
    expect(isRunToken('short')).toBe(false);
    expect(isRunToken('a'.repeat(42) + '=')).toBe(false);
    expect(isUuid(newUuid())).toBe(true);
    expect(isUuid('not-a-uuid')).toBe(false);
  });
});

describe('recovery codes', () => {
  const runId = '3f2b8c1e-7d4a-4c6b-9e2f-1a2b3c4d5e6f';
  const token = generateRunToken();

  it('round-trip a run and its token', async () => {
    const code = await encodeRecoveryCode(runId, token);
    expect(code.startsWith('HC1.')).toBe(true);
    expect(await decodeRecoveryCode(code)).toEqual({ ok: true, run: { runId, token } });
  });

  it('tolerate whitespace and line breaks from copying', async () => {
    const code = await encodeRecoveryCode(runId, token);
    const wrapped = `  ${code.slice(0, 30)}\n${code.slice(30)}  `;
    expect(await decodeRecoveryCode(wrapped)).toEqual({ ok: true, run: { runId, token } });
  });

  it('detect a typo through the check digits', async () => {
    const code = await encodeRecoveryCode(runId, token);
    const swapped = token[0] === 'A' ? 'B' : 'A';
    const typo = code.replace(`.${token}.`, `.${swapped}${token.slice(1)}.`);
    const result = await decodeRecoveryCode(typo);
    expect(result.ok).toBe(false);
  });

  it('reject anything that is not a code', async () => {
    for (const input of ['', 'hello', `HC1.${runId}.short.abcd1234`, `XX1.${runId}.${token}.00000000`]) {
      expect((await decodeRecoveryCode(input)).ok).toBe(false);
    }
  });

  it('refuse to export an invalid run', async () => {
    await expect(encodeRecoveryCode('nope', token)).rejects.toThrow();
  });
});
