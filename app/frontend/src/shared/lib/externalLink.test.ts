import { describe, expect, it } from 'vitest';

import { renderableLink } from './externalLink';

describe('renderableLink', () => {
  it('renders an ordinary https address', () => {
    expect(renderableLink('https://drive.atelier.ro/q3-supplier-review')).toBe(
      'https://drive.atelier.ro/q3-supplier-review',
    );
  });

  it('renders plain http too, because an internal tool is often not on https', () => {
    expect(renderableLink('http://intranet.atelier.ro/rapoarte/q3')).toBe(
      'http://intranet.atelier.ro/rapoarte/q3',
    );
  });

  it('refuses a javascript: URL, whatever case it is written in', () => {
    expect(renderableLink('javascript:alert(document.cookie)')).toBeUndefined();
    expect(renderableLink('JavaScript:alert(1)')).toBeUndefined();
    expect(renderableLink('  javascript:alert(1)  ')).toBeUndefined();
  });

  it('refuses a data: URL, which can carry a whole document', () => {
    expect(renderableLink('data:text/html,<script>alert(1)</script>')).toBeUndefined();
  });

  it('refuses vbscript: and file:, which are not ours to open either', () => {
    expect(renderableLink('vbscript:msgbox(1)')).toBeUndefined();
    expect(renderableLink('file:///C:/Users/maria/Desktop/raport.xlsx')).toBeUndefined();
  });

  it('treats something that is not a URL at all as text rather than as a link', () => {
    expect(renderableLink('shelf 3, folder B')).toBeUndefined();
  });

  it('treats absent, empty and whitespace alike', () => {
    expect(renderableLink(null)).toBeUndefined();
    expect(renderableLink(undefined)).toBeUndefined();
    expect(renderableLink('')).toBeUndefined();
    expect(renderableLink('   ')).toBeUndefined();
  });

  it('trims what it returns, so the href never carries stray whitespace', () => {
    expect(renderableLink('  https://atelier.ro/raport  ')).toBe('https://atelier.ro/raport');
  });
});
