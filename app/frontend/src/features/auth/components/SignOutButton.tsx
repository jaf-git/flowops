import type { CSSProperties, JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { useLogout } from '../hooks/useAuth';

interface SignOutButtonProps {
  style?: CSSProperties;
}

export function SignOutButton({ style }: SignOutButtonProps): JSX.Element {
  const { t } = useTranslation();
  const logout = useLogout();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', ...style }}>
      <Button
        variant="secondary"
        onClick={() => {
          logout.mutate();
        }}
        loading={logout.isPending}
        loadingLabel={t('auth.logout.submitting')}
        style={{ alignSelf: 'flex-start' }}
      >
        {t('auth.logout.submit')}
      </Button>

      {logout.isError && <Banner tone="alert">{t('auth.logout.failed')}</Banner>}
    </div>
  );
}
