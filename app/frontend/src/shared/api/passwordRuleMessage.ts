import { ApiError } from './client';

type Translate = (key: string) => string;

export function passwordRuleMessage(failure: Error | null, t: Translate): string | undefined {
  if (!(failure instanceof ApiError) || failure.code !== 'PASSWORD_POLICY_VIOLATION') {
    return undefined;
  }
  const rule = failure.details.find((violation) => violation.field === 'password')?.rule;
  return rule === undefined
    ? t('auth.error.passwordRule.UNKNOWN')
    : t(`auth.error.passwordRule.${rule}`);
}
