// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { TaskTemplate, TemplateStatus } from '../api/taskTemplateApi';
import { TemplateCard } from './TemplateCard';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: () => undefined },
}));

afterEach(cleanup);

function templateThatIs(status: TemplateStatus): TaskTemplate {
  return {
    id: 't-1',
    title: 'Monthly performance report',
    description: null,
    type: null,
    priority: 'NORMAL',
    estimatedHours: null,
    checklist: [],
    status,
    authorId: 'u-1',
    rejectionReason: null,
    convertedToProcessTemplateId: null,
    processShapeHints: [],
    metadata: {
      responsibleRole: null,
      triggerNote: null,
      requiredInput: null,
      expectedOutput: null,
      outputKind: null,
      completionCriteria: null,
      nextAsk: null,
      missing: [],
    },
    timesUsed: 3,
    createdAt: '2026-09-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
    discoveredByPipeline: false,
  };
}

function draw(status: TemplateStatus): void {
  render(
    <MemoryRouter>
      <TemplateCard
        template={templateThatIs(status)}
        along={0}
        busiest={10}
        mayRetire={false}
        onUse={() => undefined}
        onCopy={() => undefined}
        onRetire={() => undefined}
        onEdit={() => undefined}
        busy={false}
      />
    </MemoryRouter>,
  );
}

describe('TemplateCard, refusing a draft', () => {
  it('says why a draft cannot be used, in text somebody can actually read', () => {
    draw('DRAFT');

    // The regression this guards: the sentence lived in a `title` attribute on a disabled
    // button, so it was reachable by hovering with a mouse and by nothing else.
    expect(screen.getByText('tasklib.card.notApproved')).toBeTruthy();
  });

  it('ties the sentence to the button it refuses, so it is announced with it', () => {
    draw('DRAFT');

    const button = screen.getByRole('button', { name: 'tasklib.card.use' });
    const describedBy = button.getAttribute('aria-describedby');

    expect(describedBy).toBeTruthy();
    expect(document.getElementById(describedBy as string)?.textContent).toBe(
      'tasklib.card.notApproved',
    );
    expect(button.getAttribute('title')).toBeNull();
  });

  it('says nothing of the sort about an approved template, and lets it be used', () => {
    draw('APPROVED');

    expect(screen.queryByText('tasklib.card.notApproved')).toBeNull();

    const button = screen.getByRole('button', { name: 'tasklib.card.use' });
    expect(button.hasAttribute('disabled')).toBe(false);
    expect(button.getAttribute('aria-describedby')).toBeNull();
  });
});
