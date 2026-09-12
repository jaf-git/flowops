import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { PASSWORD_RULES, passwordRulesMet } from './passwordPolicy';

interface PasswordRulesProps {
  password: string;
  id: string;
}

export function PasswordRules({ password, id }: PasswordRulesProps): JSX.Element {
  const { t } = useTranslation();
  const met = passwordRulesMet(password);

  return (
    <ul
      id={id}

      aria-live="polite"
      className="ui-password-rules"
    >
      {PASSWORD_RULES.map((rule) => (
        <li key={rule} data-met={met[rule] ? 'yes' : 'no'} className="ui-password-rule">
          <span aria-hidden="true" className="ui-password-rule-glyph">
            {met[rule] ? '✓' : '◇'}
          </span>
          <span>
            {t(`auth.error.passwordRule.${rule}`)}

            <span className="sr-only">
              {met[rule] ? ` — ${t('ui.passwordRules.met')}` : ` — ${t('ui.passwordRules.notMet')}`}
            </span>
          </span>
        </li>
      ))}
    </ul>
  );
}
