import { useMutation, useQuery } from '@tanstack/react-query';
import { useState, type FormEvent, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { StatTile } from '../../../shared/ui/StatTile';
import { fetchDigest, nameType, type DigestDecision } from '../api/digestApi';
import { Refusal } from '../components/Refusal';
import { discoveryKeys } from '../hooks/useDiscovery';

const AT_MOST = 3;

interface DigestScreenProps {
  permissions: readonly string[];
}

export function DigestScreen({ permissions }: DigestScreenProps): JSX.Element {
  const { t } = useTranslation();

  const digest = useQuery({
    queryKey: discoveryKeys.digest(),
    queryFn: fetchDigest,

    retry: false,
  });

  const week = digest.data;
  const decisions = (week?.decisions ?? []).slice(0, AT_MOST);
  const mayName = permissions.includes('DISCOVERY_TYPE_CURATE');

  return (
    <section className="fo-disc-digest">
      <header className="fo-disc-digest__head">
        <div className="fo-page-header-text">
          <h2 className="fo-page-title">{t('discovery.digest.title')}</h2>

          <p className="fo-page-subtitle">
            {t('discovery.digest.summary', {
              recognised: week?.proposals ?? 0,
              needing: decisions.length,
            })}
          </p>
        </div>
      </header>

      {digest.isError ? (
        <Refusal code={digest.error instanceof ApiError ? digest.error.code : 'UNKNOWN'} />
      ) : digest.isPending || week === undefined ? (
        <p style={{ color: 'var(--muted)' }}>{t('discovery.digest.loading')}</p>
      ) : (
        <>
          <div className="fo-disc-tiles">
            <StatTile
              label={t('discovery.digest.tile.tracksClosed')}
              value={String(week.closedThisWeek)}
            />
            <StatTile label={t('discovery.digest.tile.proposals')} value={String(week.proposals)} />
            <StatTile
              label={t('discovery.digest.tile.coverage')}
              value={t('discovery.digest.tile.coverageValue', { percent: week.coveragePercent })}
            />
          </div>

          <h3 className="fo-eyebrow">{t('discovery.digest.work')}</h3>

          {decisions.length === 0 ? (
            <EmptyState
              heading={t('discovery.digest.empty.heading')}
              body={t('discovery.digest.empty.body')}
            />
          ) : (
            decisions.map((decision) => (
              <Decision
                key={decision.typeId ?? decision.subject}
                decision={decision}
                mayName={mayName}
              />
            ))
          )}
        </>
      )}
    </section>
  );
}

function Decision({
  decision,
  mayName,
}: {
  decision: DigestDecision;
  mayName: boolean;
}): JSX.Element {
  return decision.kind === 'A_ROLE_STOPPED_CLICKING' ? (
    <ARoleWentQuiet decision={decision} />
  ) : (
    <AShapeWorthAName decision={decision} mayName={mayName} />
  );
}

function AShapeWorthAName({
  decision,
  mayName,
}: {
  decision: DigestDecision;
  mayName: boolean;
}): JSX.Element {
  const { t } = useTranslation();

  const [typed, setTyped] = useState('');
  const [named, setNamed] = useState<string | undefined>(undefined);
  const [refusal, setRefusal] = useState<string | undefined>(undefined);

  const confirm = useMutation({
    mutationFn: (chosen: string) => nameType(decision.typeId as string, chosen),
  });

  function submit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();

    const chosen = typed.trim();
    if (chosen === '') {
      return;
    }

    setRefusal(undefined);
    confirm.mutate(chosen, {
      onSuccess: () => setNamed(chosen),
      onError: (failed) => setRefusal(failed instanceof ApiError ? failed.code : 'UNKNOWN'),
    });
  }

  const label = (
    <div className="fo-disc-decision__label">
      {decision.subject}{' '}
      <span className="fo-disc-decision__count">
        {t('discovery.digest.repeated', { count: decision.occurrenceCount })}
      </span>
    </div>
  );

  if (named !== undefined) {
    return (
      <div className="fo-disc-decision">
        {label}
        <Banner tone="done">{t('discovery.digest.confirmed', { name: named })}</Banner>
      </div>
    );
  }

  if (!mayName) {
    return <div className="fo-disc-decision">{label}</div>;
  }

  return (
    <form className="fo-disc-decision" onSubmit={submit}>
      {label}

      <input
        className="ui-control"
        aria-label={t('discovery.type.name')}
        placeholder={t('discovery.type.namePlaceholder')}
        value={typed}
        onChange={(event) => setTyped(event.target.value)}

        style={{ maxWidth: '18rem' }}
      />

      <button
        type="submit"

        className="ui-button ui-button-primary"

        aria-label={t('discovery.digest.confirmLabel', { name: decision.subject })}
      >
        {t('discovery.digest.confirm')}
      </button>

      {refusal === 'TYPE_NAME_TAKEN' ? (
        <Banner tone="alert">{t('discovery.type.nameTaken')}</Banner>
      ) : (
        <Refusal code={refusal} />
      )}
    </form>
  );
}

function ARoleWentQuiet({ decision }: { decision: DigestDecision }): JSX.Element {
  const { t } = useTranslation();

  return (
    <div className="fo-disc-decision">
      <div className="fo-disc-decision__label">{decision.subject}</div>

      <span className="fo-disc-decision__count" title={t('discovery.digest.provisionalNote')}>
        {t('discovery.digest.provisional')}
      </span>
    </div>
  );
}
