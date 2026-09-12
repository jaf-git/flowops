import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ForgotPasswordForm } from './ForgotPasswordForm';
import { LoginForm } from './LoginForm';
import { SignupForm } from './SignupForm';

type Screen = 'login' | 'signup' | 'forgot';

export function AuthGate(): JSX.Element {
  const { t } = useTranslation();
  const [screen, setScreen] = useState<Screen>('login');

  return (
    <section style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
        <h1
          style={{
            margin: 0,
            fontSize: 'var(--text-xl)',
            fontWeight: 700,
            letterSpacing: '-0.01em',
          }}
        >
          {screen === 'forgot'
            ? t('auth.forgotPassword.heading')
            : screen === 'login'
              ? t('auth.login.heading')
              : t('auth.signup.heading')}
        </h1>
      </div>

      {screen === 'forgot' ? (
        <ForgotPasswordForm onGiveUp={() => setScreen('login')} />
      ) : screen === 'login' ? (
        <LoginForm />
      ) : (
        <SignupForm />
      )}

      {screen === 'login' && (
        <button
          type="button"
          className="ui-button ui-button-quiet"
          onClick={() => setScreen('forgot')}

          style={{ alignSelf: 'flex-start' }}
        >
          {t('auth.forgotPassword.link')}
        </button>
      )}

      {screen !== 'forgot' && (
        <button
          type="button"
          className="ui-button ui-button-quiet"
          onClick={() => setScreen(screen === 'login' ? 'signup' : 'login')}

          style={{ alignSelf: 'flex-start' }}
        >
          {screen === 'login' ? t('auth.login.switchToSignup') : t('auth.signup.switchToLogin')}
        </button>
      )}
    </section>
  );
}
