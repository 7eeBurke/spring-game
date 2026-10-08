import { ApiError } from '../api/client';

/** Player-facing wording for API failures: plain language, no codes or internals. */
export function describeError(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Something went wrong. Please try again.';
  switch (error.kind) {
    case 'network':
      return 'Could not reach the game. Check your connection and try again.';
    case 'timeout':
      return 'The game is taking a long time to answer. Your request may still be in progress.';
    case 'http':
      break;
  }
  switch (error.code) {
    case 'UNAUTHORIZED':
      return error.message.toLowerCase().includes('invite')
        ? 'That invite code was not accepted.'
        : 'This device can no longer open that tale.';
    case 'RUN_NOT_FOUND':
      return 'This device can no longer open that tale.';
    case 'RATE_LIMITED':
      return 'Too many attempts just now. Wait a little and try again.';
    case 'RUN_INITIALIZING':
      return 'This tale is still being created.';
    case 'SERVICE_UNAVAILABLE':
    case 'INTERNAL_ERROR':
      return 'The game is having trouble right now. Try again shortly.';
    default:
      return error.message;
  }
}
