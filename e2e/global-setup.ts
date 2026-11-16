import { BACKEND } from './support/api';

// reuseExistingServer reuses whatever listens on 8080; fail fast if it is not the e2e profile.
export default async function globalSetup(): Promise<void> {
  const res = await fetch(`${BACKEND}/test-support/reset`, { method: 'POST' }).catch(() => null);
  if (res?.status !== 204) {
    throw new Error(
      `Backend on ${BACKEND} is not running profile e2e (POST /test-support/reset -> ${res?.status ?? 'unreachable'}). ` +
        'Stop the process on port 8080 and run the suite again.',
    );
  }
}
