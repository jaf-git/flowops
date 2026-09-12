import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { addClient, fetchClients, type Client } from '../api/bracketApi';
import { useOpenJob } from '../hooks/useDiscovery';

interface OpenEngagementFormProps {
  readonly messageId: string;

  readonly onOpened: (jobId: string) => void;
  readonly onCancel: () => void;
}

const CREATE_ONE = '__NEW__';

export function OpenEngagementForm({
  messageId,
  onOpened,
  onCancel,
}: OpenEngagementFormProps): JSX.Element {
  const { t } = useTranslation();
  const cache = useQueryClient();
  const nameFieldId = useId();
  const projectFieldId = useId();
  const clientFieldId = useId();
  const newClientFieldId = useId();

  const [name, setName] = useState('');
  const [projectLabel, setProjectLabel] = useState('');
  const [clientId, setClientId] = useState('');
  const [newClient, setNewClient] = useState('');
  const [nameMissing, setNameMissing] = useState(false);

  const [oneIsAlreadyOpen, setOneIsAlreadyOpen] = useState<string | null>(null);

  const clients = useQuery({ queryKey: ['discovery', 'clients'], queryFn: fetchClients });
  const opening = useOpenJob();

  const create = useMutation({
    mutationFn: (clientName: string) => addClient(clientName),
    onSuccess: (created: Client) => {
      void cache.invalidateQueries({ queryKey: ['discovery', 'clients'] });
      setClientId(created.id);
      setNewClient('');
    },
  });

  const naming = clientId === CREATE_ONE;

  function open(anyway = false): void {
    const trimmed = name.trim();

    if (trimmed === '') {
      setNameMissing(true);
      return;
    }

    opening.mutate(
      {
        evenThoughOneIsOpen: anyway ? true : undefined,
        messageId,
        name: trimmed,
        projectLabel: projectLabel.trim() === '' ? undefined : projectLabel.trim(),

        counterpartyId: clientId === '' || naming ? undefined : clientId,
      },
      {
        onSuccess: (opened) => {
          setOneIsAlreadyOpen(null);
          onOpened(opened.jobId);
        },
        onError: (refused) => {
          const failure = refused as { code?: string; message?: string };
          setOneIsAlreadyOpen(
            failure.code === 'AN_ENGAGEMENT_FOR_THIS_CLIENT_IS_ALREADY_OPEN'
              ? (failure.message ?? t('discovery.strip.oneIsAlreadyOpen'))
              : null,
          );
        },
      },
    );
  }

  return (
    <div className="fo-mark-picker">
      <p className="fo-mark-help">{t('discovery.strip.opensTheJob')}</p>

      <Field
        id={nameFieldId}
        label={t('discovery.strip.jobName')}
        error={nameMissing ? t('discovery.strip.jobNameRequired') : undefined}
        required
      >
        <Input
          id={nameFieldId}
          value={name}
          invalid={nameMissing}
          describedBy={nameMissing ? 'error' : undefined}
          placeholder={t('discovery.strip.jobNamePlaceholder')}
          onChange={(event) => {
            setName(event.target.value);
            setNameMissing(false);
          }}
        />
      </Field>

      <Field id={projectFieldId} label={t('discovery.strip.projectLabel')}>
        <Input
          id={projectFieldId}
          value={projectLabel}
          placeholder={t('discovery.strip.projectLabelPlaceholder')}
          onChange={(event) => {
            setProjectLabel(event.target.value);
          }}
        />
      </Field>

      <label className="fo-mark-field" htmlFor={clientFieldId}>
        {t('discovery.circles.whichClient')}
        <select
          id={clientFieldId}
          className="ui-control"
          value={clientId}
          onChange={(event) => {
            setClientId(event.target.value);
          }}
        >
          <option value="">{t('discovery.circles.clientNotSet')}</option>
          {(clients.data ?? []).map((client) => (
            <option key={client.id} value={client.id}>
              {client.name}
            </option>
          ))}
          <option value={CREATE_ONE}>{t('discovery.circles.clientCreate')}</option>
        </select>
      </label>

      {naming ? (
        <>
          <Field id={newClientFieldId} label={t('discovery.circles.clientName')}>
            <Input
              id={newClientFieldId}
              value={newClient}
              placeholder={t('discovery.circles.clientNamePlaceholder')}
              onChange={(event) => {
                setNewClient(event.target.value);
              }}
            />
          </Field>
          <button
            type="button"
            className="ui-button ui-button-quiet"
            disabled={newClient.trim() === '' || create.isPending}
            onClick={() => {
              create.mutate(newClient.trim());
            }}
          >
            {t('discovery.circles.clientAdd')}
          </button>
        </>
      ) : null}

      <div className="fo-mark-familiar">
        <div className="fo-mark-nearest">
          <button
            type="button"
            className="fo-mark-do"
            disabled={opening.isPending}
            onClick={() => {
              open();
            }}
          >
            {opening.isPending ? t('discovery.circle.opening') : t('discovery.strip.open')}
          </button>
          <button type="button" className="ui-button ui-button-quiet" onClick={onCancel}>
            {t('discovery.strip.close')}
          </button>
        </div>
        {oneIsAlreadyOpen !== null ? (
          <div className="fo-mark-anyway" role="alert">
            <p className="fo-mark-failed">{oneIsAlreadyOpen}</p>
            <button
              type="button"
              className="ui-button ui-button-quiet"
              disabled={opening.isPending}
              onClick={() => {
                open(true);
              }}
            >
              {t('discovery.strip.openAnyway')}
            </button>
          </div>
        ) : opening.isError ? (
          <p className="fo-mark-failed">{t('discovery.circles.openFailed')}</p>
        ) : null}
      </div>
    </div>
  );
}
