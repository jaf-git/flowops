import { describe, expect, it } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import { ApiError } from '../../../shared/api/client';
import { INVITE_REFUSAL_CODES, inviteFailureMessage } from './inviteErrors';

const t = (key: string): string => key;

describe('the invitation refusals', () => {
  it('gives every refusal a message of its own', () => {
    const messages = INVITE_REFUSAL_CODES.map((code) =>
      inviteFailureMessage(new ApiError(409, { code }), t),
    );

    expect(new Set(messages).size).toBe(INVITE_REFUSAL_CODES.length);
  });

  it.each(INVITE_REFUSAL_CODES)('has copy for %s', (code) => {
    expect((en.workspace.invite.error as Record<string, string>)[code]).toBeTruthy();
  });

  it('does not offer reactivation, which is not built', () => {
    const message = (en.workspace.invite.error as Record<string, string>).DEACTIVATED_MEMBER ?? '';

    expect(message).not.toBe('');
    expect(message.toLowerCase()).not.toContain('reactivat');
  });

  it('falls back to the unexpected message rather than to the nearest one', () => {
    expect(inviteFailureMessage(new ApiError(500, { code: 'SOMETHING_NEW' }), t)).toBe(
      'workspace.invite.error.unexpected',
    );
    expect(inviteFailureMessage(new Error('the network went away'), t)).toBe(
      'workspace.invite.error.unexpected',
    );
  });

  it('reads the limit from the status when no code names it', () => {
    expect(inviteFailureMessage(new ApiError(429, {}), t)).toBe(
      'workspace.invite.error.RATE_LIMIT',
    );
  });
});
