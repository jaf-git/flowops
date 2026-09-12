import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { fetchActivities, mergeActivity, retireActivity } from '../api/bracketApi';

const ACTIVITIES_KEY = ['discovery', 'activities'];

const ADOPTION_KEY = ['node-pipeline', 'adoption'];

/**
 * The activities this workspace names its work by, and the two things a person can do to the list.
 *
 * It sits under the discoveries because it is the input to them: a step is keyed on the activity
 * somebody named, so two names for one piece of work are two steps, and the shapes above are the
 * place that shows up. Merging is the remedy and it is deliberately here rather than in the mark
 * panel — somebody marking work is answering "what is this", not "what should our vocabulary be",
 * and a merge control in that flow is a destructive button beside a routine one.
 *
 * Neither action is ever taken by the software. A wrong merge makes two different activities one
 * and nothing afterwards can tell them apart again.
 */
export function ActivityVocabulary(): JSX.Element | null {
  const { t } = useTranslation();
  const client = useQueryClient();

  const [merging, setMerging] = useState<{ from: string; into: string } | null>(null);
  const [retiring, setRetiring] = useState<string | null>(null);

  const activities = useQuery({ queryKey: ACTIVITIES_KEY, queryFn: fetchActivities });

  const settle = {
    onSuccess: () => {
      setMerging(null);
      setRetiring(null);
      void client.invalidateQueries({ queryKey: ACTIVITIES_KEY });
      void client.invalidateQueries({ queryKey: ADOPTION_KEY });
    },
  };

  const merge = useMutation({
    mutationFn: (asked: { from: string; into: string }) => mergeActivity(asked.from, asked.into),
    ...settle,
  });

  const retire = useMutation({ mutationFn: (id: string) => retireActivity(id), ...settle });

  const named = activities.data ?? [];

  if (named.length === 0) {
    return null;
  }

  const busy = merge.isPending || retire.isPending;

  return (
    <section className="fo-pb-panel">
      <div className="fo-pb-stack fo-pb-stack--tight">
        <span className="fo-pb-eyebrow">{t('discovery.vocabulary.eyebrow')}</span>
        <h3 className="fo-pb-title">{t('discovery.vocabulary.count', { count: named.length })}</h3>
      </div>

      <p className="fo-pb-note">{t('discovery.vocabulary.note')}</p>

      <ul className="fo-vocabulary">
        {named.map((one) => {
          const chosen = merging?.from === one.id ? merging.into : '';
          const target = named.find((other) => other.id === chosen);

          return (
            <li key={one.id} className="fo-vocabulary-word">
              <div className="fo-pb-stack fo-pb-stack--tight">
                <span className="fo-vocabulary-name">{one.name}</span>
                <span className="fo-pb-eyebrow">
                  {t('discovery.vocabulary.used', { count: one.timesUsed })}
                  {one.departments.length === 0 ? '' : ` · ${one.departments.join(', ')}`}
                  {one.counterparties.length === 0 ? '' : ` · ${one.counterparties.join(', ')}`}
                  {one.tooGenericToBeOneThing ? ` · ${t('discovery.vocabulary.generic')}` : ''}
                </span>
              </div>

              <div className="fo-vocabulary-acts">
                <select
                  className="ui-control"
                  aria-label={t('discovery.vocabulary.mergeInto', { name: one.name })}
                  value={chosen}
                  disabled={busy}
                  onChange={(event) => {
                    setMerging(
                      event.target.value === '' ? null : { from: one.id, into: event.target.value },
                    );
                  }}
                >
                  <option value="">{t('discovery.vocabulary.mergePick')}</option>
                  {named
                    .filter((other) => other.id !== one.id)
                    .map((other) => (
                      <option key={other.id} value={other.id}>
                        {other.name}
                      </option>
                    ))}
                </select>

                {target === undefined ? null : (
                  <button
                    type="button"
                    className="ui-button ui-button-primary"
                    disabled={busy}
                    onClick={() => merge.mutate({ from: one.id, into: target.id })}
                  >
                    {t('discovery.vocabulary.merge', { name: target.name })}
                  </button>
                )}

                <button
                  type="button"
                  className="ui-button ui-button-quiet"
                  disabled={busy}
                  onClick={() => {
                    if (retiring === one.id) {
                      retire.mutate(one.id);
                      return;
                    }

                    setRetiring(one.id);
                  }}
                >
                  {retiring === one.id
                    ? t('discovery.vocabulary.retireConfirm')
                    : t('discovery.vocabulary.retire')}
                </button>
              </div>
            </li>
          );
        })}
      </ul>

      {merge.isError || retire.isError ? (
        <p className="fo-pb-note" role="alert">
          {t('discovery.vocabulary.failed')}
        </p>
      ) : null}
    </section>
  );
}
