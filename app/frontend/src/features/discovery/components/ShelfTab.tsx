import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { apiRequest } from '../../../shared/api/client';

interface ShelfItem {
  id: string;
  kind: 'TEXT' | 'LINK' | 'MESSAGE_REF';
  value: string;

  label: string | null;
  placedBy: string;
  placedByName: string | null;
  placedAt: string;
}

interface ShelfTabProps {
  readonly conversationId: string;
}

const shelfKey = (conversationId: string) => ['discovery', 'shelf', conversationId] as const;

export function ShelfTab({ conversationId }: ShelfTabProps): JSX.Element {
  const { t } = useTranslation();
  const cache = useQueryClient();

  const [value, setValue] = useState('');
  const [label, setLabel] = useState('');

  const shelf = useQuery({
    queryKey: shelfKey(conversationId),
    queryFn: () =>
      apiRequest<ShelfItem[]>(
        `/discovery/brackets/conversation/${encodeURIComponent(conversationId)}/shelf`,
      ),
  });

  const place = useMutation({
    mutationFn: () =>
      apiRequest<void>(
        `/discovery/brackets/conversation/${encodeURIComponent(conversationId)}/shelf`,
        {
          method: 'POST',

          body: JSON.stringify({
            kind: /^https?:\/\//i.test(value.trim()) ? 'LINK' : 'TEXT',
            value: value.trim(),
            label: label.trim() === '' ? null : label.trim(),
          }),
        },
      ),
    onSuccess: () => {
      setValue('');
      setLabel('');
      void cache.invalidateQueries({ queryKey: shelfKey(conversationId) });
    },
  });

  const remove = useMutation({
    mutationFn: (itemId: string) =>
      apiRequest<void>(`/discovery/brackets/shelf/${encodeURIComponent(itemId)}/remove`, {
        method: 'POST',
      }),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: shelfKey(conversationId) });
    },
  });

  const items = shelf.data ?? [];

  return (
    <div className="fo-shelf">
      <form
        className="fo-shelf-add"
        onSubmit={(event) => {
          event.preventDefault();
          if (value.trim() !== '') {
            place.mutate();
          }
        }}
      >
        <input
          className="ui-control"
          value={value}
          placeholder={t('discovery.shelf.placeholder')}
          aria-label={t('discovery.shelf.valueLabel')}
          onChange={(event) => {
            setValue(event.target.value);
          }}
        />

        <input
          className="ui-control fo-shelf-label"
          value={label}
          placeholder={t('discovery.shelf.labelPlaceholder')}
          aria-label={t('discovery.shelf.labelLabel')}
          onChange={(event) => {
            setLabel(event.target.value);
          }}
        />

        <button
          type="submit"
          className="ui-button ui-button-primary"
          disabled={value.trim() === '' || place.isPending}
        >
          {t('discovery.shelf.keep')}
        </button>
      </form>

      {shelf.isPending ? (
        <p className="fo-work-quiet">{t('discovery.shelf.loading')}</p>
      ) : items.length === 0 ? (
        <div className="fo-work-empty">
          <p>{t('discovery.shelf.empty.what')}</p>
          <p className="fo-work-quiet">{t('discovery.shelf.empty.how')}</p>
        </div>
      ) : (
        <ul className="fo-work-list">
          {items.map((item) => (
            <li key={item.id} className="fo-work-row">
              <span className="fo-work-address">{item.kind}</span>

              {item.kind === 'LINK' ? (
                <a
                  className="fo-shelf-value"
                  href={item.value}
                  target="_blank"
                  rel="noreferrer noopener"
                >
                  {item.label ?? item.value}
                </a>
              ) : (
                <span className="fo-shelf-value">{item.label ?? item.value}</span>
              )}

              <span className="fo-wait-on">{item.placedByName ?? ''}</span>

              <button
                type="button"
                className="fo-work-open"
                aria-label={t('discovery.shelf.remove')}
                title={t('discovery.shelf.remove')}
                onClick={() => {
                  remove.mutate(item.id);
                }}
              >
                <span aria-hidden="true">±</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
