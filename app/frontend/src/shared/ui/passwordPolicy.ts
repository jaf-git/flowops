export const PASSWORD_RULES = ['MINIMUM_LENGTH', 'MAXIMUM_LENGTH'] as const;

export type PasswordRuleName = (typeof PASSWORD_RULES)[number];

const MINIMUM_LENGTH = 12;
const MAXIMUM_LENGTH = 128;

export function passwordRulesMet(password: string): Record<PasswordRuleName, boolean> {
  if (password === '') {
    return { MINIMUM_LENGTH: false, MAXIMUM_LENGTH: false };
  }
  return {
    MINIMUM_LENGTH: password.length >= MINIMUM_LENGTH,
    MAXIMUM_LENGTH: password.length <= MAXIMUM_LENGTH,
  };
}
