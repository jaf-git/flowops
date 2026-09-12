import { ApiError } from '../../../shared/api/client';

type Translate = (key: string, options?: Record<string, unknown>) => string;

export { passwordRuleMessage as invitedPasswordRuleMessage } from '../../../shared/api/passwordRuleMessage';

export function joinRefusalMessage(failure: Error | null, t: Translate): string | undefined {
  if (!(failure instanceof ApiError)) {
    return failure === null ? undefined : t('workspace.invitation.join.error.UNKNOWN');
  }
  switch (failure.code) {
    case 'PASSWORD_POLICY_VIOLATION':
      return undefined;
    case 'DISPLAY_NAME_REQUIRED':
      return t('workspace.invitation.join.error.DISPLAY_NAME_REQUIRED');
    case 'ALREADY_MEMBER':
      return t('workspace.invitation.join.error.ALREADY_MEMBER');
    case 'CONSENT_REQUIRED':
      return t('workspace.invitation.join.error.CONSENT_REQUIRED');
    case 'CONSENT_VERSION_STALE':
      return t('workspace.invitation.join.error.CONSENT_VERSION_STALE');
    case 'INVITATION_NOT_USABLE':
      return t('workspace.invitation.join.error.INVITATION_NOT_USABLE');
    default:
      return t('workspace.invitation.join.error.UNKNOWN');
  }
}
