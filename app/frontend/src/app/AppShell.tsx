import { useMemo, type JSX, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';

import { destinationOf, reachable, type Destination } from './navigation';
import type { Section } from './sections';

import { NotificationBell, type NotificationSubject } from '../features/notification';
import { DEFAULT_LOCALE } from '../i18n';
import { PageEntrance } from '../shared/motion/PageEntrance';
import { NoticeCentre } from '../shared/notice/NoticeCentre';
import { NoticeProvider } from '../shared/notice/NoticeProvider';
import { ControlBar } from '../shared/ui/ControlBar';
import { DropdownChip } from '../shared/ui/DropdownChip';
import { Rail, type RailItem } from '../shared/ui/Rail';

interface AppShellProps {
  section: Section;
  onSection: (section: Section) => void;

  permissions: readonly string[];

  personName: string;

  controlBarChips?: ReactNode;

  /** Absent when the caller may not start a process, in which case no button is offered. */
  onNewProcess?: (() => void) | undefined;

  onOpenTask?: (taskId: string) => void;
  children: ReactNode;
}

export function AppShell({
  section,
  onSection,
  permissions,
  personName,
  controlBarChips,
  onNewProcess,
  onOpenTask,
  children,
}: AppShellProps): JSX.Element {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const destinations = useMemo(() => reachable(permissions), [permissions]);
  const here = destinationOf(section);
  const destination = destinations.find((candidate) => candidate.id === here);

  const toRailItem = (candidate: Destination): RailItem => ({
    id: candidate.id,
    glyph: candidate.glyph,
    name: t(`shell.nav.${candidate.label}`),
  });

  function openDestination(id: string): void {
    const target = destinations.find((candidate) => candidate.id === id);
    const first = target?.views[0];

    if (first !== undefined) {
      onSection(first.section);
    }
  }

  function openNotificationSubject(subject: NotificationSubject): void {
    if (subject.kind === 'RUN') {
      const locale = pathname.split('/')[1] || DEFAULT_LOCALE;
      navigate(`/${locale}/canvas/process/${subject.id}`);
      return;
    }

    if (subject.kind === 'TASK' && onOpenTask !== undefined) {
      onOpenTask(subject.id);
      return;
    }

    if (subject.kind === 'BRACKET' || subject.kind === 'JOB') {
      onSection('discovery');
      return;
    }

    onSection(
      subject.kind === 'STEP' ? 'processes' : subject.kind === 'WORKSPACE' ? 'settings' : 'tasks',
    );
  }

  const viewChip =
    destination === undefined || destination.views.length < 2 ? null : (
      <DropdownChip
        name={t('shell.view.name')}
        value={section}
        options={destination.views.map((view) => ({
          id: view.section,
          label: t(`shell.view.${view.label}`),
        }))}
        onChange={(next) => {
          onSection(next as Section);
        }}
      />
    );

  return (
    <NoticeProvider>
      <div className="fo-shell">
        <Rail
          label={t('shell.navigation')}
          active={here}
          onSelect={openDestination}
          items={destinations.filter((one) => one.place === 'primary').map(toRailItem)}
          footer={destinations.filter((one) => one.place === 'footer').map(toRailItem)}
          brand={
            <span className="fo-rail-brandmark" aria-label={t('app.name')} title={t('app.name')}>
              <svg width="18" height="18" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <rect
                  x="2"
                  y="2"
                  width="5"
                  height="5"
                  rx="1.5"
                  fill="currentColor"
                  opacity="0.95"
                />
                <rect
                  x="9"
                  y="2"
                  width="5"
                  height="5"
                  rx="1.5"
                  fill="currentColor"
                  opacity="0.45"
                />
                <rect
                  x="2"
                  y="9"
                  width="5"
                  height="5"
                  rx="1.5"
                  fill="currentColor"
                  opacity="0.45"
                />
                <rect
                  x="9"
                  y="9"
                  width="5"
                  height="5"
                  rx="1.5"
                  fill="currentColor"
                  opacity="0.95"
                />
              </svg>
            </span>
          }
        />

        <div className="fo-main">
          <ControlBar
            title={t(`shell.nav.${destination?.label ?? 'today'}`)}
            chips={
              viewChip === null && controlBarChips === undefined ? undefined : (
                <>
                  {viewChip}
                  {controlBarChips}
                </>
              )
            }
            status={
              <span className="fo-stream" title={personName}>
                <span aria-hidden="true" className="fo-stream-dot" />
                {t('shell.updatedJustNow')}
              </span>
            }
            action={
              onNewProcess === undefined ? undefined : (
                <button
                  type="button"
                  className="ui-button ui-button-primary"
                  onClick={onNewProcess}
                >
                  {t('shell.newProcess')}
                </button>
              )
            }
            notifications={<NotificationBell onOpenSubject={openNotificationSubject} />}
          />

          <PageEntrance className="fo-surface" routeKey={here ?? 'today'}>
            {children}
          </PageEntrance>
        </div>

        <NoticeCentre label={t('shell.notices')} dismissLabel={t('shell.dismissNotice')} />
      </div>
    </NoticeProvider>
  );
}
