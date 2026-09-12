const URL_PATTERN = /https?:\/\/[^\s<>"']+/gi;

const TRAILING = /[.,;:!?)\]}'"]+$/;

export function urlsIn(text: string | null | undefined): readonly string[] {
  if (text === null || text === undefined || text === '') {
    return [];
  }

  const found = [...text.matchAll(URL_PATTERN)].map((match) => match[0].replace(TRAILING, ''));

  return [...new Set(found)].filter((url) => url.length > 'https://'.length);
}

export function likelyOutput(text: string | null | undefined): string | null {
  return urlsIn(text)[0] ?? null;
}
