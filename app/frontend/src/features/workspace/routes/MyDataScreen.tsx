import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { Button } from '../../../shared/ui/Button';
import { Card } from '../../../shared/ui/Card';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { useAnnounce } from '../../../shared/notice/useNotices';
import { RelativeTime } from '../../../shared/ui/RelativeTime';
import { Spinner } from '../../../shared/ui/Spinner';
import type { OwnData } from '../api/workspaceApi';
import { useEditOwnProfile, useExportOwnData, useOwnData } from '../hooks/useOwnData';

function handOverTheFile(file: OwnData): void {
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(file, null, 2)], { type: 'application/json' }),
  );
  const link = document.createElement('a');
  link.href = url;
  link.download = 'my-data.json';
  document.body.appendChild(link);
  link.click();
  link.remove();

  URL.revokeObjectURL(url);
}

const CARD_TITLE = {
  margin: 0,
  fontSize: 'var(--text-base)',
  fontWeight: 600,
  color: 'var(--ink)',
} as const;

const BODY_NOTE = {
  margin: 0,
  fontSize: 'var(--text-md)',
  color: 'var(--muted)',
  lineHeight: 1.6,
} as const;

const FIELD_VALUE = {
  margin: 0,
  marginBlockStart: 'var(--space-1)',
  fontSize: 'var(--text-base)',
  color: 'var(--ink)',
} as const;

const PLAIN_LIST = {
  margin: 0,
  padding: 0,
  listStyle: 'none',
  display: 'grid',
  gap: 'var(--space-2)',
  fontSize: 'var(--text-md)',
  color: 'var(--slate)',
} as const;

export function MyDataScreen(): JSX.Element {
  const { t } = useTranslation();
  const own = useOwnData();
  const exportCopy = useExportOwnData();
  const editProfile = useEditOwnProfile();
  const [name, setName] = useState<string | undefined>(undefined);
  const announce = useAnnounce();

  if (own.isPending) {
    return (
      <Card>
        <Spinner label={t('workspace.myData.loading')} />
      </Card>
    );
  }

  if (own.isError || own.data === undefined) {
    return (
      <Card>
        <EmptyState
          icon="alert"
          heading={t('workspace.myData.error.heading')}
          body={t('workspace.myData.error.body')}
        />
      </Card>
    );
  }

  const data = own.data;

  const typed = name ?? data.account.displayName ?? '';
  const copiesLeft = Math.max(0, data.exports.limit - data.exports.produced);

  const exportRefusal =
    exportCopy.error instanceof ApiError && exportCopy.error.code === 'EXPORT_LIMIT'
      ? t('workspace.myData.exportLimit')
      : exportCopy.error != null
        ? t('workspace.myData.exportFailed')
        : undefined;

  const nameRefusal =
    editProfile.error instanceof ApiError && editProfile.error.status === 400
      ? t('workspace.myProfile.nameRequired')
      : editProfile.error != null
        ? t('workspace.myProfile.failed')
        : undefined;

  function onSaveName(): void {
    editProfile.mutate(typed.trim(), {
      onSuccess: (result) => {
        announce({
          tone: 'done',
          message: result.changed
            ? t('workspace.myProfile.saved')
            : t('workspace.myProfile.alreadyYourName'),
        });
        setName(undefined);
      },
    });
  }

  return (
    <div style={{ display: 'grid', gap: 'var(--space-5)', maxWidth: '760px' }}>
      <h2
        style={{
          margin: 0,
          fontSize: 'var(--text-lg)',
          fontWeight: 600,
          letterSpacing: '-0.01em',
        }}
      >
        {t('workspace.myData.title')}
      </h2>

      {data.producedForSomebodyElse ? (
        <Banner tone="info">{t('workspace.myData.producedForSomebodyElse')}</Banner>
      ) : null}

      <Card>
        <h3 style={CARD_TITLE}>{t('workspace.myProfile.title')}</h3>
        {nameRefusal === undefined ? null : <Banner tone="alert">{nameRefusal}</Banner>}
        <Field id="my-display-name" label={t('workspace.myProfile.nameLabel')}>
          <Input
            id="my-display-name"
            value={typed}
            onChange={(event) => setName(event.target.value)}
          />
        </Field>

        <p style={BODY_NOTE}>
          {t('workspace.myProfile.addressIsFixed', { address: data.account.emailAddress })}
        </p>

        <div>
          <Button
            type="button"
            disabled={typed.trim() === ''}
            loading={editProfile.isPending}
            loadingLabel={t('workspace.myProfile.saving')}
            onClick={onSaveName}
          >
            {t('workspace.myProfile.save')}
          </Button>
        </div>
      </Card>

      <Card>
        <h3 style={CARD_TITLE}>{t('workspace.myData.account')}</h3>

        <dl style={{ margin: 0, display: 'grid', gap: 'var(--space-3)' }}>
          <div>
            <dt className="fo-eyebrow">{t('workspace.myData.role')}</dt>
            <dd style={FIELD_VALUE}>{t(`workspace.role.${data.account.role}`)}</dd>
          </div>
          <div>
            <dt className="fo-eyebrow">{t('workspace.myData.manager')}</dt>
            <dd style={FIELD_VALUE}>
              {data.membership.managerName ?? t('workspace.myData.noManager')}
            </dd>
          </div>
        </dl>
      </Card>

      <Card>
        <h3 style={CARD_TITLE}>{t('workspace.myData.reportingLine')}</h3>
        {data.reportingLineHistory.length === 0 ? (
          <p style={BODY_NOTE}>{t('workspace.myData.noReportingLine')}</p>
        ) : (
          <ul style={PLAIN_LIST}>
            {data.reportingLineHistory.map((period) => (
              <li key={`${period.from}-${period.managerName ?? 'gone'}`}>
                {t('workspace.myData.reportedTo', {
                  manager: period.managerName ?? t('workspace.myData.formerMember'),
                })}{' '}
                <RelativeTime value={period.from} />
                {period.until === null ? ` — ${t('workspace.myData.stillNow')}` : null}
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card>
        <h3 style={CARD_TITLE}>{t('workspace.myData.consent')}</h3>
        {data.consent === null ? (
          <p style={BODY_NOTE}>{t('workspace.myData.noConsent')}</p>
        ) : (
          <p style={BODY_NOTE}>
            {t('workspace.myData.agreedTo', { version: data.consent.version })}{' '}
            <RelativeTime value={data.consent.agreedAt} />
          </p>
        )}
      </Card>

      <Card>
        <h3 style={CARD_TITLE}>{t('workspace.myData.sessions')}</h3>
        {data.account.sessions.length === 0 ? (
          <p style={BODY_NOTE}>{t('workspace.myData.noSessions')}</p>
        ) : (
          <ul style={PLAIN_LIST}>
            {data.account.sessions.map((session) => (
              <li key={session.reference}>
                {session.deviceSummary ?? t('workspace.myData.unknownDevice')} —{' '}
                {session.coarseLocation ?? t('workspace.myData.unknownPlace')}
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card>
        <h3 style={CARD_TITLE}>{t('workspace.myData.authored')}</h3>

        <p style={BODY_NOTE}>
          {t('workspace.myData.authoredCounts', {
            tasks: data.authored.tasks,
            comments: data.authored.comments,
          })}
        </p>
      </Card>

      <Card>
        <h3 style={CARD_TITLE}>{t('workspace.myData.export')}</h3>
        {exportRefusal === undefined ? null : <Banner tone="alert">{exportRefusal}</Banner>}
        <p style={BODY_NOTE}>{t('workspace.myData.copiesLeft', { count: copiesLeft })}</p>
        <div>
          <Button
            type="button"
            disabled={copiesLeft === 0}
            loading={exportCopy.isPending}
            loadingLabel={t('workspace.myData.exporting')}
            onClick={() => exportCopy.mutate(undefined, { onSuccess: handOverTheFile })}
          >
            {t('workspace.myData.takeACopy')}
          </Button>
        </div>
      </Card>
    </div>
  );
}
