import type { JSX, ReactNode } from 'react';

import { Icon, type IconName } from './Icon';

type Tone = 'info' | 'done' | 'waiting' | 'alert';

interface BannerProps {
  tone?: Tone;
  children: ReactNode;
}

const ICON: Record<Tone, IconName> = {
  info: 'list',
  done: 'check',
  waiting: 'clock',
  alert: 'alert',
};

export function Banner({ tone = 'info', children }: BannerProps): JSX.Element {
  return (
    <div
      className={`ui-banner ui-banner-${tone}`}

      role={tone === 'alert' ? 'alert' : 'status'}
    >
      <span aria-hidden="true" className="ui-banner-glyph">
        <Icon name={ICON[tone]} size={18} />
      </span>
      <span className="ui-banner-message">{children}</span>
    </div>
  );
}
