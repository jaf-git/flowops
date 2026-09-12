import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError, apiRequest } from './client';

function respondWith(status: number, body: string, contentType = 'application/json'): void {
  vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=a-token' });
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(
      new Response(body === '' ? null : body, {
        status,
        headers: body === '' ? {} : { 'Content-Type': contentType },
      }),
    ),
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('a successful response with no body', () => {
  it('is not an error when the passcode request answers 202', async () => {
    respondWith(202, '');

    await expect(apiRequest('/auth/signup/passcode', { method: 'POST' })).resolves.toBeUndefined();
  });

  it('is not an error when logout answers 204', async () => {
    respondWith(204, '');

    await expect(apiRequest('/auth/logout', { method: 'POST' })).resolves.toBeUndefined();
  });
});

describe('a successful response with a body', () => {
  it('is parsed on 200', async () => {
    respondWith(200, '{"email":"founder@flowops.test"}');

    await expect(apiRequest('/auth/session')).resolves.toEqual({ email: 'founder@flowops.test' });
  });

  it('is parsed on 201', async () => {
    respondWith(201, '{"userId":"a-uuid"}');

    await expect(apiRequest('/auth/signup', { method: 'POST' })).resolves.toEqual({
      userId: 'a-uuid',
    });
  });
});

describe('a failure', () => {
  it('carries the envelope the server sent', async () => {
    respondWith(400, '{"code":"EMAIL_INVALID","message":"That is not a valid email address."}');

    await expect(apiRequest('/auth/signup/passcode', { method: 'POST' })).rejects.toSatisfy(
      (failure: unknown) =>
        failure instanceof ApiError && failure.code === 'EMAIL_INVALID' && failure.status === 400,
    );
  });

  it('is still an ApiError when the response carries no envelope at all', async () => {
    respondWith(401, '');

    await expect(apiRequest('/auth/session')).rejects.toSatisfy(
      (failure: unknown) =>
        failure instanceof ApiError && failure.status === 401 && failure.code === 'UNKNOWN',
    );
  });
});
