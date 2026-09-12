import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useId, useMemo, useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { apiRequest } from '../../../shared/api/client';
import {
  askTheModelAboutNearMiss,
  createActivity,
  fetchActivities,
  type Activity,
} from '../api/bracketApi';
import type { EarlierWorkHere } from '../api/workApi';
import { useWorkVocabulary } from '../hooks/useWorkVocabulary';

export interface WorkPerson {
  id: string;
  displayName: string;
}

export interface MarkAddress {
  jobId: string | undefined;

  performerId: string | null;

  workType: string | null;

  activityId: string | null;
}

interface Familiarity {
  workType: string;
  neverUsedBefore: boolean;
  closestExisting: string[];
}

interface MarkWorkPanelProps {
  readonly conversationId: string;
  readonly address: MarkAddress;

  readonly derivedWorkType: string;
  readonly onChange: (next: MarkAddress) => void;

  readonly people: readonly WorkPerson[];

  readonly everybody: readonly WorkPerson[];
  readonly onBringIn: (personId: string) => Promise<void>;

  readonly clientName?: string | null;

  readonly earlierWorkHere?: EarlierWorkHere | null;
}

type Scope = 'RECENT' | 'DEPARTMENT' | 'CLIENT' | 'WORKSPACE';

interface Suggestion {
  activity: Activity;
  scope: Scope;
  rank: number;
  daysAgo: number | null;
  department: string | null;
  client: string | null;
}

const SOMEBODY_ELSE = '__ELSEWHERE__';

const ANOTHER_WORD = '__NEW_WORD__';

const SUGGESTIONS_SHOWN = 6;

const STILL_RECENT_AFTER_DAYS = 7;

const A_DAY = 86_400_000;

const CLOSE_ENOUGH_TO_OFFER = 0.68;

const CLOSE_ENOUGH_TO_ASK = 0.72;

const SHORTEST_WORTH_MATCHING = 3;

const SETTLED_AFTER_MS = 600;

const MOST_THE_MODEL_IS_ASKED_ABOUT = 25;

const SCOPE_ORDER: Scope[] = ['RECENT', 'DEPARTMENT', 'CLIENT', 'WORKSPACE'];

const activityKeys = { catalogue: ['discovery', 'activities'] as const };

function fold(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim().replace(/\s+/g, ' ');
}

function keyOf(text: string): string {
  return fold(text)
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function editDistance(one: string, other: string): number {
  let previous = Array.from({ length: other.length + 1 }, (_empty, at) => at);

  for (let row = 1; row <= one.length; row += 1) {
    const current = [row];

    for (let column = 1; column <= other.length; column += 1) {
      const swap = one[row - 1] === other[column - 1] ? 0 : 1;

      current[column] = Math.min(
        (previous[column] ?? 0) + 1,
        (current[column - 1] ?? 0) + 1,
        (previous[column - 1] ?? 0) + swap,
      );
    }

    previous = current;
  }

  return previous[other.length] ?? 0;
}

function similarity(one: string, other: string): number {
  const longest = Math.max(one.length, other.length);

  return longest === 0 ? 1 : 1 - editDistance(one, other) / longest;
}

function daysSince(lastUsedAt: string | null, now: number): number | null {
  if (lastUsedAt === null) {
    return null;
  }

  const at = Date.parse(lastUsedAt);

  return Number.isNaN(at) ? null : Math.max(0, Math.floor((now - at) / A_DAY));
}

function rankOf(name: string, typed: string): number | null {
  if (typed === '') {
    return 3;
  }

  if (name.startsWith(typed)) {
    return 0;
  }

  if (name.includes(typed)) {
    return 1;
  }

  if (typed.length < SHORTEST_WORTH_MATCHING) {
    return null;
  }

  const allowedSlips = Math.max(1, Math.floor(typed.length / 4));

  if (name.split(' ').some((word) => editDistance(word, typed) <= allowedSlips)) {
    return 2;
  }

  return similarity(name, typed) >= CLOSE_ENOUGH_TO_OFFER ? 2 : null;
}

export function MarkWorkPanel({
  conversationId,
  address,
  derivedWorkType,
  onChange,
  people,
  everybody,
  onBringIn,
  clientName = null,
  earlierWorkHere = null,
}: MarkWorkPanelProps): JSX.Element {
  const { t } = useTranslation();
  const cache = useQueryClient();
  const kindFieldId = useId();
  const activityFieldId = useId();

  const [reachingWider, setReachingWider] = useState(false);
  const [bringingIn, setBringingIn] = useState<string | null>(null);

  const [typing, setTyping] = useState(false);

  const [activityText, setActivityText] = useState('');
  const [notThisOne, setNotThisOne] = useState<string | null>(null);

  const familiar = useWorkVocabulary(conversationId);
  const inTheRoom = new Set(people.map((person) => person.id));
  const outside = everybody.filter((person) => !inTheRoom.has(person.id));

  const corrected = address.workType;

  const familiarity = useQuery({
    queryKey: ['discovery', 'work-type', corrected ?? ''],
    enabled: corrected !== null,
    queryFn: () =>
      apiRequest<Familiarity>(
        `/discovery/graph/work-type?name=${encodeURIComponent(corrected as string)}`,
      ),
  });

  const activities = useQuery({
    queryKey: activityKeys.catalogue,
    queryFn: fetchActivities,
    staleTime: Number.POSITIVE_INFINITY,
  });

  const naming = useMutation({
    mutationFn: (name: string) => createActivity(name),
    onSuccess: (named: Activity) => {
      void cache.invalidateQueries({ queryKey: activityKeys.catalogue });
      setActivityText(named.name);
      onChange({ ...address, activityId: named.id });
    },
  });

  const inTheBox = corrected ?? derivedWorkType;

  const choices = [derivedWorkType, ...familiar, ...(corrected === null ? [] : [corrected])]
    .map((one) => one.trim())
    .filter((one) => one !== '')
    .filter((one, at, all) => all.indexOf(one) === at);

  const catalogue = activities.data;
  const readAt = activities.dataUpdatedAt;
  const typedKey = keyOf(activityText);

  const suggestions = useMemo(() => {
    const now = readAt;
    const typed = fold(activityText);
    const mine = keyOf(inTheBox);
    const here = clientName === null ? null : keyOf(clientName);

    return (catalogue ?? [])
      .map((activity): Suggestion | null => {
        const rank = rankOf(fold(activity.name), typed);

        if (rank === null) {
          return null;
        }

        const daysAgo = daysSince(activity.lastUsedAt, now);
        const department = activity.departments.find((one) => keyOf(one) === mine) ?? null;
        const client =
          here === null
            ? null
            : (activity.counterparties.find((one) => keyOf(one) === here) ?? null);

        const scope: Scope =
          daysAgo !== null && daysAgo <= STILL_RECENT_AFTER_DAYS
            ? 'RECENT'
            : department !== null
              ? 'DEPARTMENT'
              : client !== null
                ? 'CLIENT'
                : 'WORKSPACE';

        return { activity, scope, rank, daysAgo, department, client };
      })
      .filter((one): one is Suggestion => one !== null)
      .sort((one, other) => {
        const byScope = SCOPE_ORDER.indexOf(one.scope) - SCOPE_ORDER.indexOf(other.scope);

        if (byScope !== 0) {
          return byScope;
        }

        if (one.rank !== other.rank) {
          return one.rank - other.rank;
        }

        return other.activity.timesUsed === one.activity.timesUsed
          ? one.activity.name.localeCompare(other.activity.name)
          : other.activity.timesUsed - one.activity.timesUsed;
      })
      .slice(0, SUGGESTIONS_SHOWN);
  }, [catalogue, readAt, activityText, inTheBox, clientName]);

  const chosen = (catalogue ?? []).find((activity) => activity.id === address.activityId) ?? null;

  const alreadyNamed =
    typedKey !== '' && (catalogue ?? []).some((activity) => keyOf(activity.name) === typedKey);

  const nearMiss = useMemo(() => {
    if (chosen !== null || typedKey === '' || alreadyNamed) {
      return null;
    }

    const typed = fold(activityText);

    if (typed.length < SHORTEST_WORTH_MATCHING) {
      return null;
    }

    let best: Activity | null = null;
    let bestScore = CLOSE_ENOUGH_TO_ASK;

    for (const activity of catalogue ?? []) {
      const name = fold(activity.name);

      if (name.startsWith(typed)) {
        continue;
      }

      const score = similarity(name, typed);

      if (score >= bestScore) {
        best = activity;
        bestScore = score;
      }
    }

    return best === null || best.id === notThisOne ? null : best;
  }, [catalogue, activityText, typedKey, alreadyNamed, chosen, notThisOne]);

  const [settled, setSettled] = useState('');

  useEffect(() => {
    const waiting = setTimeout(() => {
      setSettled(activityText.trim());
    }, SETTLED_AFTER_MS);

    return () => {
      clearTimeout(waiting);
    };
  }, [activityText]);

  const worthAskingTheModel =
    chosen === null &&
    nearMiss === null &&
    !alreadyNamed &&
    suggestions.length === 0 &&
    (catalogue ?? []).length > 0 &&
    settled.length >= SHORTEST_WORTH_MATCHING &&
    keyOf(settled) === typedKey;

  const judged = useQuery({
    queryKey: ['discovery', 'activity-near-miss', typedKey],
    enabled: worthAskingTheModel,
    retry: false,
    staleTime: Number.POSITIVE_INFINITY,
    queryFn: () =>
      askTheModelAboutNearMiss(
        settled,
        (catalogue ?? []).slice(0, MOST_THE_MODEL_IS_ASKED_ABOUT).map((one) => one.name),
      ),
  });

  const modelSaid = useMemo(() => {
    const said = judged.data?.same;

    if (said === undefined || !worthAskingTheModel) {
      return null;
    }

    const match = (catalogue ?? []).find((one) => fold(one.name) === fold(said)) ?? null;

    return match === null || match.id === notThisOne ? null : match;
  }, [judged.data, catalogue, worthAskingTheModel, notThisOne]);

  const asking = chosen !== null ? null : (nearMiss ?? modelSaid);

  const askedByTheModel = nearMiss === null && modelSaid !== null;

  function describe(one: Suggestion): string {
    if (one.scope === 'RECENT') {
      const days = one.daysAgo ?? 0;

      if (days === 0) {
        return t('discovery.activity.today');
      }

      return days === 1
        ? t('discovery.activity.yesterday')
        : t('discovery.activity.daysAgo', { count: days });
    }

    if (one.scope === 'DEPARTMENT' && one.department !== null) {
      return t('discovery.activity.byDepartment', {
        department: one.department,
        count: one.activity.timesUsed,
      });
    }

    if (one.scope === 'CLIENT' && one.client !== null) {
      return t('discovery.activity.onClientWork', {
        client: one.client,
        count: one.activity.timesUsed,
      });
    }

    return one.activity.timesUsed === 0
      ? t('discovery.activity.neverUsed')
      : t('discovery.activity.acrossWorkspace', { count: one.activity.timesUsed });
  }

  function setWorkType(raw: string): void {
    const cleaned = raw.trim().toUpperCase();
    onChange({ ...address, workType: cleaned === '' ? null : cleaned });
  }

  function chooseKind(one: string): void {
    setTyping(false);
    onChange({ ...address, workType: one === derivedWorkType.trim() ? null : one });
  }

  function chooseActivity(activity: Activity): void {
    setActivityText(activity.name);
    setNotThisOne(null);
    onChange({ ...address, activityId: activity.id });
  }

  function typeActivity(raw: string): void {
    setActivityText(raw);
    setNotThisOne(null);

    if (address.activityId !== null) {
      onChange({ ...address, activityId: null });
    }
  }

  async function bringIn(personId: string): Promise<void> {
    setBringingIn(personId);

    try {
      await onBringIn(personId);
      onChange({ ...address, performerId: personId });
      setReachingWider(false);
    } finally {
      setBringingIn(null);
    }
  }

  return (
    <div className="fo-mark-picker">
      <label className="fo-mark-line">
        <span className="fo-mark-part">{t('discovery.mark.forWhom')}</span>
        <select
          className="fo-mark-select"
          value={address.performerId ?? ''}
          onChange={(event) => {
            const picked = event.target.value;

            if (picked === SOMEBODY_ELSE) {
              setReachingWider(true);
              return;
            }

            setReachingWider(false);
            onChange({ ...address, performerId: picked === '' ? null : picked });
          }}
        >
          <option value="">{t('discovery.mark.unclaimed')}</option>
          {people.map((person) => (
            <option key={person.id} value={person.id}>
              {person.displayName}
            </option>
          ))}

          {outside.length === 0 ? null : (
            <option value={SOMEBODY_ELSE}>{t('discovery.circles.someoneElse')}</option>
          )}
        </select>
      </label>

      {reachingWider ? (
        <label className="fo-mark-line">
          <span className="fo-mark-part">{t('discovery.circles.whoElse')}</span>
          <select
            className="fo-mark-select"
            value=""
            disabled={bringingIn !== null}
            onChange={(event) => {
              if (event.target.value !== '') {
                void bringIn(event.target.value);
              }
            }}
          >
            <option value="">{t('discovery.circles.someoneElseHelp')}</option>
            {outside.map((person) => (
              <option key={person.id} value={person.id}>
                {person.displayName}
              </option>
            ))}
          </select>
        </label>
      ) : null}

      <label className="fo-mark-line">
        <span className="fo-mark-part">{t('discovery.mark.whatKind')}</span>
        <select
          className="fo-mark-select"
          value={typing ? ANOTHER_WORD : inTheBox}
          onChange={(event) => {
            if (event.target.value === ANOTHER_WORD) {
              setTyping(true);
              return;
            }

            chooseKind(event.target.value);
          }}
        >
          {choices.map((one) => (
            <option key={one} value={one}>
              {one}
            </option>
          ))}
          <option value={ANOTHER_WORD}>{t('discovery.mark.kindOther')}</option>
        </select>
      </label>

      {typing ? (
        <input
          id={kindFieldId}
          className="fo-mark-typed"
          type="text"
          value={corrected ?? ''}
          aria-label={t('discovery.mark.kindOwnWord')}
          placeholder={t('discovery.mark.kindOwnWord')}
          onChange={(event) => {
            setWorkType(event.target.value);
          }}
        />
      ) : null}

      {corrected !== null && familiarity.data?.neverUsedBefore === true ? (
        <label className="fo-mark-line">
          <span className="fo-mark-part">{t('discovery.mark.kindNearest')}</span>
          <select
            className="fo-mark-select"
            value=""
            onChange={(event) => {
              if (event.target.value !== '') {
                setTyping(false);
                chooseKind(event.target.value);
              }
            }}
          >
            <option value="">
              {t('discovery.mark.kindNew', { name: familiarity.data.workType })}
            </option>
            {familiarity.data.closestExisting.map((near) => (
              <option key={near} value={near}>
                {near}
              </option>
            ))}
          </select>
        </label>
      ) : null}

      <div className="fo-mark-activity">
        {chosen !== null || activityText !== '' || earlierWorkHere === null ? null : (
          <div className="fo-mark-activity-ambiguous" role="status">
            <p className="fo-mark-activity-ambiguous-case">
              {earlierWorkHere.people.length === 1
                ? t('discovery.activity.ambiguityWho', {
                    who: earlierWorkHere.people[0],
                    kind: earlierWorkHere.workType,
                  })
                : t('discovery.activity.ambiguityHere', { kind: earlierWorkHere.workType })}
            </p>

            <p className="fo-mark-help">{t('discovery.activity.ambiguityAsk')}</p>

            {earlierWorkHere.activities.length === 0 ? null : (
              <p className="fo-mark-help">
                {t('discovery.activity.ambiguityAlready', {
                  names: earlierWorkHere.activities.join(', '),
                })}
              </p>
            )}
          </div>
        )}

        <label className="fo-mark-line" htmlFor={activityFieldId}>
          <span className="fo-mark-part">{t('discovery.activity.label')}</span>
          <input
            id={activityFieldId}
            className="fo-mark-typed"
            type="text"
            value={activityText}
            autoComplete="off"
            placeholder={t('discovery.activity.placeholder')}
            onChange={(event) => {
              typeActivity(event.target.value);
            }}
          />
        </label>

        <p className="fo-mark-help">{t('discovery.activity.help')}</p>

        {chosen === null ? null : (
          <div className="fo-mark-activity-chosen">
            <span className="fo-mark-activity-name">{chosen.name}</span>
            <button
              type="button"
              className="ui-button ui-button-quiet"
              onClick={() => {
                typeActivity('');
              }}
            >
              {t('discovery.activity.clear')}
            </button>
          </div>
        )}

        {asking === null ? null : (
          <div className="fo-mark-activity-near" role="status">
            <p className="fo-mark-help">
              {asking.timesUsed === 0
                ? t('discovery.activity.nearMissNew', { name: asking.name })
                : t('discovery.activity.nearMiss', {
                    name: asking.name,
                    count: asking.timesUsed,
                  })}
            </p>

            {askedByTheModel ? (
              <p className="fo-mark-help">{t('discovery.activity.nearMissByModel')}</p>
            ) : null}

            {asking.departments[0] === undefined ? null : (
              <p className="fo-mark-help">
                {t('discovery.activity.nearMissMostly', { department: asking.departments[0] })}
              </p>
            )}

            <div className="fo-mark-activity-acts">
              <button
                type="button"
                className="ui-button ui-button-quiet"
                onClick={() => {
                  chooseActivity(asking);
                }}
              >
                {t('discovery.activity.nearMissUse')}
              </button>
              <button
                type="button"
                className="ui-button ui-button-quiet"
                onClick={() => {
                  setNotThisOne(asking.id);
                }}
              >
                {t('discovery.activity.nearMissDifferent')}
              </button>
            </div>
          </div>
        )}

        {chosen !== null || suggestions.length === 0 ? null : (
          <div
            className="fo-mark-activity-list"
            role="group"
            aria-label={t('discovery.activity.suggestions')}
          >
            {suggestions.map((one) => (
              <button
                key={one.activity.id}
                type="button"
                className="fo-mark-activity-option"
                onClick={() => {
                  chooseActivity(one.activity);
                }}
              >
                <span className="fo-mark-activity-name">{one.activity.name}</span>
                <span className="fo-mark-activity-scope">{describe(one)}</span>
                {one.activity.tooGenericToBeOneThing ? (
                  <span className="fo-mark-activity-scope">
                    {t('discovery.activity.tooGeneric')}
                  </span>
                ) : null}
              </button>
            ))}
          </div>
        )}

        {chosen !== null || typedKey === '' || alreadyNamed ? null : (
          <div className="fo-mark-activity-acts">
            <button
              type="button"
              className="ui-button ui-button-quiet"
              disabled={naming.isPending}
              onClick={() => {
                naming.mutate(activityText.trim());
              }}
            >
              {naming.isPending
                ? t('discovery.activity.naming')
                : t('discovery.activity.name', { name: activityText.trim() })}
            </button>
          </div>
        )}

        {naming.isError ? (
          <p className="fo-mark-failed">{t('discovery.activity.nameFailed')}</p>
        ) : null}
      </div>
    </div>
  );
}
