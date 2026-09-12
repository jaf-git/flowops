import { ApiError } from '../../../shared/api/client';

type Translate = (key: string) => string;

const KNOWN_CODES = [
  'ALREADY_MEMBER',
  'DEACTIVATED_MEMBER',
  'DUPLICATE_INVITATION',
  'DECLINE_WINDOW',
  'MANAGER_INACTIVE',
  'SELF_INVITATION',
  'RATE_LIMIT',
  'ROLE_CEILING',

  'MANAGER_NOT_ELIGIBLE',
] as const;

type KnownCode = (typeof KNOWN_CODES)[number];

function isKnown(code: string): code is KnownCode {
  return (KNOWN_CODES as readonly string[]).includes(code);
}

export function inviteFailureMessage(failure: Error | null, t: Translate): string {
  if (!(failure instanceof ApiError)) {
    return t('workspace.invite.error.unexpected');
  }
  if (isKnown(failure.code)) {
    return t(`workspace.invite.error.${failure.code}`);
  }
  if (failure.status === 429) {
    return t('workspace.invite.error.RATE_LIMIT');
  }
  if (failure.status === 400 || failure.status === 422) {
    return t('workspace.invite.error.malformed');
  }
  if (failure.status === 403) {
    return t('workspace.invite.error.notPermitted');
  }
  return t('workspace.invite.error.unexpected');
}

export const INVITE_REFUSAL_CODES: readonly string[] = KNOWN_CODES;
