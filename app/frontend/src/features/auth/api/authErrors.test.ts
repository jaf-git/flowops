import { describe, expect, it } from 'vitest';

import en from '../../../i18n/locales/en/common.json';
import { ApiError } from '../../../shared/api/client';
import {
  changePasswordMessage,
  loginMessage,
  passcodeRequestMessage,
  passwordRuleMessage,
  reauthenticationMessage,
  sessionLookupMessage,
  signupMessage,
  terminateSessionMessage,
} from './authErrors';

const key = (value: string): string => value;

describe('the password policy failure', () => {
  it('names the rule the server said was unmet', () => {
    const failure = new ApiError(400, {
      code: 'PASSWORD_POLICY_VIOLATION',
      details: [{ field: 'password', rule: 'MINIMUM_LENGTH' }],
    });

    expect(passwordRuleMessage(failure, key)).toBe('auth.error.passwordRule.MINIMUM_LENGTH');
  });

  it('carries whichever rule was broken rather than a fixed one', () => {
    const failure = new ApiError(400, {
      code: 'PASSWORD_POLICY_VIOLATION',
      details: [{ field: 'password', rule: 'NOT_EMAIL_LOCAL_PART' }],
    });

    expect(passwordRuleMessage(failure, key)).toBe('auth.error.passwordRule.NOT_EMAIL_LOCAL_PART');
  });

  it('falls back to a named unknown rule rather than showing nothing', () => {
    const failure = new ApiError(400, { code: 'PASSWORD_POLICY_VIOLATION', details: [] });

    expect(passwordRuleMessage(failure, key)).toBe('auth.error.passwordRule.UNKNOWN');
  });

  it('claims no rule for a failure that is not a policy violation', () => {
    const failure = new ApiError(401, { code: 'PASSCODE_REJECTED' });

    expect(passwordRuleMessage(failure, key)).toBeUndefined();
    expect(signupMessage(failure, key)).toBe('auth.error.passcodeRejected');
  });
});

describe('the passcode request failure', () => {
  it('names a malformed address only when the server said the address was malformed', () => {
    const malformed = new ApiError(400, { code: 'EMAIL_INVALID' });

    expect(passcodeRequestMessage(malformed, key)).toBe('auth.error.emailInvalid');
  });

  it('does not blame the address for a failure that has nothing to do with it', () => {
    expect(passcodeRequestMessage(new Error('network down'), key)).toBe('auth.error.unexpected');
    expect(passcodeRequestMessage(new ApiError(500, { code: 'INTERNAL' }), key)).toBe(
      'auth.error.unexpected',
    );
  });

  it('reports being rate-limited as itself', () => {
    expect(passcodeRequestMessage(new ApiError(429, { code: 'RATE_LIMITED' }), key)).toBe(
      'auth.error.rateLimited',
    );
  });
});

describe('the login failure', () => {
  it('is the same message whether the address is unknown or the password is wrong', () => {
    const unknownAddress = new ApiError(401, {
      code: 'AUTHENTICATION_REFUSED',
      message: 'The email address or password is incorrect.',
    });
    const wrongPassword = new ApiError(401, {
      code: 'AUTHENTICATION_REFUSED',
      message: 'The email address or password is incorrect.',
    });

    expect(loginMessage(unknownAddress, key)).toBe(loginMessage(wrongPassword, key));
    expect(loginMessage(unknownAddress, key)).toBe('auth.error.loginRefused');
  });

  it('does not become a different message for a deactivated account or an unexpected failure', () => {
    expect(loginMessage(new ApiError(403, { code: 'ACCOUNT_DEACTIVATED' }), key)).toBe(
      'auth.error.loginRefused',
    );
    expect(loginMessage(new Error('network down'), key)).toBe('auth.error.loginRefused');
  });

  it('tells apart only being rate-limited, which the server states openly', () => {
    expect(loginMessage(new ApiError(429, { code: 'RATE_LIMITED' }), key)).toBe(
      'auth.error.rateLimited',
    );
  });
});

describe('the re-authentication challenge', () => {
  it('says the password was wrong, because there is no identity left to protect', () => {
    const failure = new ApiError(401, { code: 'AUTHENTICATION_REFUSED', details: [] });

    expect(reauthenticationMessage(failure, key)).toBe('auth.error.reauthenticationRefused');
  });

  it('tells being rate-limited apart from a wrong password', () => {
    const failure = new ApiError(429, { code: 'RATE_LIMITED', details: [] });

    expect(reauthenticationMessage(failure, key)).toBe('auth.error.rateLimited');
  });

  it('admits it does not know rather than blaming the password', () => {
    expect(reauthenticationMessage(new Error('the network went away'), key)).toBe(
      'auth.error.unexpected',
    );
  });
});

describe('the password change failure', () => {
  it('tells an expired window apart from a rejected password', () => {
    const failure = new ApiError(403, { code: 'REAUTHENTICATION_REQUIRED', details: [] });

    expect(changePasswordMessage(failure, key)).toBe('auth.error.reauthenticationExpired');
  });

  it('names the unmet rule so the person can fix it', () => {
    const failure = new ApiError(400, {
      code: 'PASSWORD_POLICY_VIOLATION',
      details: [{ field: 'password', rule: 'NOT_CURRENT_PASSWORD' }],
    });

    expect(changePasswordMessage(failure, key)).toBe(
      'auth.error.passwordRule.NOT_CURRENT_PASSWORD',
    );
  });

  it('falls back to a named unknown rule when the server names none', () => {
    const failure = new ApiError(400, { code: 'PASSWORD_POLICY_VIOLATION', details: [] });

    expect(changePasswordMessage(failure, key)).toBe('auth.error.passwordRule.UNKNOWN');
  });

  it('admits it does not know for anything else', () => {
    expect(changePasswordMessage(new Error('the network went away'), key)).toBe(
      'auth.error.unexpected',
    );
  });
});

describe('every message this module can emit', () => {
  it('exists in both languages', () => {
    const emitted = everyKeyTheErrorModuleCanEmit();

    expect(emitted.length).toBeGreaterThanOrEqual(23);
    expect(
      new Set(emitted).size,
      'the same key twice covers one emitter, not two',
    ).toBeGreaterThanOrEqual(13);

    for (const emittedKey of emitted) {
      expect(keysOf(en), `missing from the catalogue: ${emittedKey}`).toContain(emittedKey);
    }
  });
});

type Translations = Record<string, unknown>;

function keysOf(translations: Translations, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([entry, value]) => {
    const path = prefix === '' ? entry : `${prefix}.${entry}`;
    return typeof value === 'object' && value !== null
      ? keysOf(value as Translations, path)
      : [path];
  });
}

function everyKeyTheErrorModuleCanEmit(): string[] {
  const rules = [
    'MINIMUM_LENGTH',
    'MAXIMUM_LENGTH',
    'NOT_EMAIL_LOCAL_PART',
    'NOT_CURRENT_PASSWORD',
  ];
  const policyFailure = (rule: string): ApiError =>
    new ApiError(400, {
      code: 'PASSWORD_POLICY_VIOLATION',
      details: [{ field: 'password', rule }],
    });

  const noRuleNamed = new ApiError(400, { code: 'PASSWORD_POLICY_VIOLATION', details: [] });

  return [
    ...rules.map((rule) => passwordRuleMessage(policyFailure(rule), key) as string),
    passwordRuleMessage(noRuleNamed, key) as string,
    passcodeRequestMessage(new ApiError(429, { code: 'RATE_LIMITED' }), key),
    passcodeRequestMessage(new ApiError(400, { code: 'EMAIL_INVALID' }), key),
    signupMessage(new ApiError(423, { code: 'SIGNUP_ATTEMPT_LOCKED' }), key),
    signupMessage(new ApiError(401, { code: 'PASSCODE_REJECTED' }), key),
    signupMessage(new Error('network down'), key),
    loginMessage(new ApiError(429, { code: 'RATE_LIMITED' }), key),
    loginMessage(new ApiError(401, { code: 'AUTHENTICATION_REFUSED' }), key),
    reauthenticationMessage(new ApiError(401, { code: 'AUTHENTICATION_REFUSED' }), key),
    reauthenticationMessage(new ApiError(429, { code: 'RATE_LIMITED' }), key),
    reauthenticationMessage(new Error('network down'), key),
    changePasswordMessage(new ApiError(403, { code: 'REAUTHENTICATION_REQUIRED' }), key),
    changePasswordMessage(noRuleNamed, key),
    changePasswordMessage(new Error('network down'), key),

    terminateSessionMessage(new ApiError(403, { code: 'REAUTHENTICATION_REQUIRED' }), key),
    terminateSessionMessage(new ApiError(403, { code: 'PERMISSION_DENIED' }), key),
    terminateSessionMessage(new Error('network down'), key),
    sessionLookupMessage(new ApiError(400, { code: 'REQUEST_INVALID' }), key),
    sessionLookupMessage(new ApiError(403, { code: 'PERMISSION_DENIED' }), key),
    sessionLookupMessage(new Error('network down'), key),
  ];
}
