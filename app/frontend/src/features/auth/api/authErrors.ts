import { ApiError } from '../../../shared/api/client';
import { passwordRuleMessage } from '../../../shared/api/passwordRuleMessage';

type Translate = (key: string) => string;

export function passcodeRequestMessage(failure: Error | null, t: Translate): string {
  if (!(failure instanceof ApiError)) {
    return t('auth.error.unexpected');
  }

  if (failure.code === 'SIGNUP_CLOSED') {
    return t('auth.error.signupClosed');
  }
  if (failure.status === 429) {
    return t('auth.error.rateLimited');
  }
  if (failure.code === 'EMAIL_INVALID' || failure.status === 400) {
    return t('auth.error.emailInvalid');
  }
  return t('auth.error.unexpected');
}

export { passwordRuleMessage };

export function signupMessage(failure: Error | null, t: Translate): string {
  if (!(failure instanceof ApiError)) {
    return t('auth.error.unexpected');
  }
  if (failure.code === 'SIGNUP_CLOSED') {
    return t('auth.error.signupClosed');
  }
  if (failure.status === 423) {
    return t('auth.error.attemptLocked');
  }
  if (failure.status === 401) {
    return t('auth.error.passcodeRejected');
  }
  return t('auth.error.unexpected');
}

export function loginMessage(failure: Error | null, t: Translate): string {
  if (failure instanceof ApiError && failure.status === 429) {
    return t('auth.error.rateLimited');
  }
  return t('auth.error.loginRefused');
}

export function reauthenticationMessage(failure: Error | null, t: Translate): string {
  if (!(failure instanceof ApiError)) {
    return t('auth.error.unexpected');
  }
  if (failure.status === 429) {
    return t('auth.error.rateLimited');
  }
  if (failure.status === 401) {
    return t('auth.error.reauthenticationRefused');
  }
  return t('auth.error.unexpected');
}

export function changePasswordMessage(failure: Error | null, t: Translate): string {
  if (!(failure instanceof ApiError)) {
    return t('auth.error.unexpected');
  }
  if (failure.status === 403) {
    return t('auth.error.reauthenticationExpired');
  }
  if (failure.code === 'PASSWORD_POLICY_VIOLATION') {
    return passwordRuleMessage(failure, t) ?? t('auth.error.passwordRule.UNKNOWN');
  }
  return t('auth.error.unexpected');
}

export function terminateSessionMessage(failure: Error | null, t: Translate): string {
  if (!(failure instanceof ApiError)) {
    return t('auth.error.unexpected');
  }
  if (failure.code === 'REAUTHENTICATION_REQUIRED') {
    return t('auth.error.reauthenticationExpired');
  }
  if (failure.status === 403) {
    return t('auth.error.permissionDenied');
  }
  return t('auth.error.unexpected');
}

export function sessionLookupMessage(failure: Error | null, t: Translate): string {
  if (!(failure instanceof ApiError)) {
    return t('auth.error.unexpected');
  }
  if (failure.status === 400) {
    return t('auth.error.userIdInvalid');
  }
  if (failure.status === 403) {
    return t('auth.error.permissionDenied');
  }
  return t('auth.error.unexpected');
}
