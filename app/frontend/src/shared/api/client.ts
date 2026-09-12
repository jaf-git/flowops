import { i18next } from '../../i18n';

const API_BASE_URL = '/api';
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

export interface FieldViolation {
  field: string;
  rule: string;
}

export interface ErrorEnvelope {
  code: string;
  message: string;
  details: FieldViolation[];
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: FieldViolation[];

  constructor(status: number, envelope: Partial<ErrorEnvelope>) {
    super(envelope.message ?? 'The request failed.');
    this.name = 'ApiError';
    this.status = status;
    this.code = envelope.code ?? 'UNKNOWN';
    this.details = envelope.details ?? [];
  }
}

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((entry) => entry.startsWith(`${name}=`))
    ?.split('=')[1];
}

export async function apiRequest<TResponse>(
  path: string,
  init: RequestInit = {},
): Promise<TResponse> {
  const csrfToken = readCookie(CSRF_COOKIE);

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'Accept-Language': i18next.language,
      ...(csrfToken === undefined ? {} : { [CSRF_HEADER]: decodeURIComponent(csrfToken) }),
      ...init.headers,
    },
  });

  if (!response.ok) {
    throw new ApiError(response.status, await readEnvelope(response));
  }

  const body = await response.text();
  return (body === '' ? undefined : JSON.parse(body)) as TResponse;
}

export async function apiDownload(path: string, init: RequestInit = {}): Promise<Blob> {
  const csrfToken = readCookie(CSRF_COOKIE);

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'Accept-Language': i18next.language,
      ...(csrfToken === undefined ? {} : { [CSRF_HEADER]: decodeURIComponent(csrfToken) }),
      ...init.headers,
    },
  });

  if (!response.ok) {
    throw new ApiError(response.status, await readEnvelope(response));
  }

  return response.blob();
}

async function readEnvelope(response: Response): Promise<Partial<ErrorEnvelope>> {
  try {
    return (await response.json()) as Partial<ErrorEnvelope>;
  } catch {
    return {};
  }
}
