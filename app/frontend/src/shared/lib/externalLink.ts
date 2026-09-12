const RENDERABLE_SCHEMES = ['http:', 'https:'];

export function renderableLink(link: string | null | undefined): string | undefined {
  if (link === null || link === undefined || link.trim() === '') {
    return undefined;
  }

  try {
    const parsed = new URL(link.trim());
    return RENDERABLE_SCHEMES.includes(parsed.protocol) ? link.trim() : undefined;
  } catch {
    return undefined;
  }
}
