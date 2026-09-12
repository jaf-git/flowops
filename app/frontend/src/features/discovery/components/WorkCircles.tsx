import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import type { MessageMark } from '../model/bracket';
import type { MarkVerb, OpenWork } from '../api/workApi';
import { useConversationMarks } from '../hooks/useConversationWork';
import { useJobsForConversation } from '../hooks/useDiscovery';
import { useReadingMessage } from '../hooks/useReadingMessage';
import { useMarkWork, useTakeMarkBack, useVerbOffer, type TakeBack } from '../hooks/useWorkCircles';
import { UndoToast } from './UndoToast';
import { DeliverControl } from './DeliverControl';
import { MarkWorkPanel, type MarkAddress, type WorkPerson } from './MarkWorkPanel';
import { OpenEngagementForm } from './OpenEngagementForm';

export type { WorkPerson } from './MarkWorkPanel';

const NEW_ENGAGEMENT = '__NEW__';

interface WorkCirclesProps {
  readonly conversationId: string;
  readonly messageId: string;

  readonly authorId?: string;

  readonly viewerId: string;
  readonly permissions: readonly string[];

  readonly people: readonly WorkPerson[];

  readonly everybody: readonly WorkPerson[];
  readonly onBringIn: (personId: string) => Promise<void>;
}

export function WorkCircles({
  conversationId,
  messageId,
  authorId,
  viewerId,
  permissions,
  people,
  everybody,
  onBringIn,
}: WorkCirclesProps): JSX.Element | null {
  const { t } = useTranslation();

  const reading = useReadingMessage();

  const counterpart = people.filter((person) => person.id !== viewerId);

  const authorInRoom = people.some((person) => person.id === authorId);
  const defaultPerformer = authorInRoom
    ? (authorId ?? viewerId)
    : counterpart.length === 1
      ? (counterpart[0]?.id ?? viewerId)
      : viewerId;

  const [address, setAddress] = useState<MarkAddress>({
    jobId: undefined,
    performerId: defaultPerformer,
    workType: null,
    activityId: null,
  });

  const [takeBack, setTakeBack] = useState<TakeBack | null>(null);

  const [undone, setUndone] = useState<'DONE' | 'REFUSED' | null>(null);

  const [open, setOpen] = useState(false);

  const [stage, setStage] = useState<'choose' | 'adding'>('choose');
  const [opening, setOpening] = useState(false);
  const [joining, setJoining] = useState(false);

  const subject = `${conversationId} ${messageId} ${defaultPerformer}`;
  const [shownFor, setShownFor] = useState(subject);

  if (shownFor !== subject) {
    setShownFor(subject);
    setAddress({
      jobId: undefined,
      performerId: defaultPerformer,
      workType: null,
      activityId: null,
    });
    setOpening(false);
    setJoining(false);
    setStage('choose');
  }

  const jobs = useJobsForConversation(conversationId);
  const offered = jobs.data ?? [];

  const job = address.jobId ?? (offered.length === 1 ? offered[0]?.jobId : undefined);

  const marks = useConversationMarks(conversationId);
  const already = (marks.data ?? []).filter((mark) => mark.messageId === messageId);

  const mine = already.filter((mark) => mark.performerId === viewerId);

  const offer = useVerbOffer(job, conversationId, address.performerId, address.workType);
  const mark = useMarkWork(conversationId);
  const undo = useTakeMarkBack(conversationId);

  if (!permissions.includes('WORK_NODE_MARK')) {
    return null;
  }

  function press(verb: MarkVerb, work?: OpenWork): void {
    if (job === undefined) {
      return;
    }

    mark.mutate(
      {
        messageId,
        jobId: job,
        performerId: address.performerId,

        ...(address.workType === null ? {} : { workType: address.workType }),
        ...(address.activityId === null ? {} : { activityId: address.activityId }),
        verb,
        ...(work === undefined ? {} : { joining: work.bracketId }),
      },
      {
        onSuccess: (placed) => {
          setJoining(false);

          setTakeBack({ nodeId: placed.nodeId, jobId: job });

          reading?.read(messageId);
        },
      },
    );
  }

  function twoCircles(dimmed: boolean): JSX.Element {
    return (
      <>
        <button
          type="button"
          className="fo-mark-circle"
          aria-label={t('discovery.circles.openLabel')}
          title={t('discovery.circles.openLabel')}
          aria-expanded={open}
          data-dimmed={dimmed}
          onClick={() => {
            setOpen((showing) => !showing);
          }}
        >
          <span aria-hidden="true" />
        </button>

        {already.length === 0 ? null : (
          <DeliverControl conversationId={conversationId} messageId={messageId} />
        )}
      </>
    );
  }

  function inTheFlow(situation: string, body: JSX.Element): JSX.Element {
    return (
      <div className="fo-message-flow">
        <div className="fo-message-flow__head">
          <span className="fo-message-flow__dot" aria-hidden="true" />
          <span className="fo-message-flow__case">{situation}</span>

          <button
            type="button"
            className="fo-message-flow__close"
            aria-label={t('discovery.strip.close')}
            onClick={() => {
              setOpen(false);
              setStage('choose');
            }}
          >
            <span aria-hidden="true">✕</span>
          </button>
        </div>
        {body}
      </div>
    );
  }

  const panel =
    job === undefined ? null : (
      <MarkWorkPanel
        conversationId={conversationId}
        address={{ ...address, jobId: job }}
        derivedWorkType={offer.data?.workType ?? ''}
        onChange={setAddress}
        people={people}
        everybody={everybody}
        onBringIn={onBringIn}
        clientName={offer.data?.clientName ?? null}
        earlierWorkHere={offer.data?.earlierWorkHere ?? null}
      />
    );

  const undoToast =
    undone !== null ? (
      <UndoToast
        state={undone}
        onDismiss={() => {
          setUndone(null);
        }}
      />
    ) : takeBack === null ? null : (
      <UndoToast
        state="COUNTING"
        onUndo={() => {
          const taken = takeBack;
          setTakeBack(null);
          undo.mutate(taken, {
            onSuccess: () => {
              setUndone('DONE');

              reading?.read(null);
            },
            onError: () => {
              setUndone('REFUSED');
            },
          });
        }}

        onCommit={() => {
          setTakeBack(null);
        }}
      />
    );

  const afterMark = <>{undoToast}</>;

  const newEngagement = (tone: 'primary' | 'secondary') => (
    <>
      {tone === 'primary' ? (
        <button
          type="button"
          className="fo-mark-do"
          aria-expanded={opening}
          onClick={() => {
            setOpening((showing) => !showing);
          }}
        >
          {t('discovery.strip.newJob')}
        </button>
      ) : null}

      {opening ? (
        <OpenEngagementForm
          messageId={messageId}
          onOpened={(openedJobId) => {
            setAddress((current) => ({ ...current, jobId: openedJobId }));
            setOpening(false);

            setStage('adding');
          }}
          onCancel={() => {
            setOpening(false);
          }}
        />
      ) : null}
    </>
  );

  function tagsFor(mark: MessageMark): JSX.Element {
    return (
      <>
        <span className="fo-mark-state" data-state={mark.state} data-close={mark.closeKind ?? ''}>
          {mark.boundary ? t('discovery.mark.opensJob', { where: mark.jobName }) : mark.state}
        </span>
        {mark.boundary ? null : (
          <>
            {mark.client === null ? null : <span className="fo-mark-tag">{mark.client}</span>}
            {mark.project === null ? null : <span className="fo-mark-tag">{mark.project}</span>}
            <span className="fo-mark-tag" data-kind="type">
              {mark.workType}
            </span>
            {mark.performerName === null ? null : (
              <span className="fo-mark-tag">{mark.performerName}</span>
            )}
          </>
        )}
        {already.length > 1 ? (
          <span className="fo-mark-count">
            {t('discovery.mark.alsoOthers', { count: already.length - 1 })}
          </span>
        ) : null}
      </>
    );
  }

  function markedRow(mark: MessageMark): JSX.Element {
    const body = tagsFor(mark);

    return reading === null ? (
      <p className="fo-mark-strip" data-marked="true">
        {body}
      </p>
    ) : (
      <button
        type="button"
        className="fo-mark-strip fo-mark-inspect"
        data-marked="true"
        aria-pressed={reading.messageId === messageId}
        aria-label={t('discovery.mark.inspect')}

        onClick={() => {
          reading.read(reading.messageId === messageId ? null : messageId);
        }}
      >
        {body}
      </button>
    );
  }

  const first = mine[0] ?? already.find((mark) => mark.boundary);

  if (first !== undefined) {
    return (
      <div className="fo-mark-strip">
        {afterMark}
        {twoCircles(true)}

        {markedRow(first)}

        {!open
          ? null
          : inTheFlow(
              first.boundary
                ? t('discovery.circles.caseBoundary')
                : t('discovery.circles.caseRecorded'),
              <p className="fo-mark-help">
                {first.boundary
                  ? t('discovery.circles.boundaryTakesNoWork')
                  : t('discovery.circles.alreadyYours')}
              </p>,
            )}
      </div>
    );
  }

  if (offered.length === 0) {
    return (
      <div className="fo-mark-strip">
        {afterMark}
        {twoCircles(false)}

        {!open
          ? null
          : inTheFlow(t('discovery.circles.caseNoEngagement'), newEngagement('primary'))}
      </div>
    );
  }

  if (job === undefined) {
    return (
      <div className="fo-mark-strip">
        {afterMark}
        {twoCircles(false)}

        {!open
          ? null
          : inTheFlow(
              t('discovery.mark.whichJob'),
              <>
                <div className="fo-mark-picker">
                  <label className="fo-mark-field">
                    <select
                      className="ui-control"

                      aria-label={t('discovery.mark.whichJob')}
                      value=""
                      onChange={(event) => {
                        if (event.target.value === NEW_ENGAGEMENT) {
                          setOpening(true);
                          return;
                        }
                        setAddress((current) => ({ ...current, jobId: event.target.value }));
                      }}
                    >
                      <option value="">{t('discovery.strip.chooseJob')}</option>
                      {offered.map((one) => (
                        <option key={one.jobId} value={one.jobId}>
                          {one.name}
                        </option>
                      ))}

                      <option value={NEW_ENGAGEMENT}>{t('discovery.strip.newJobOption')}</option>
                    </select>
                  </label>
                </div>

                {newEngagement('secondary')}
              </>,
            )}
      </div>
    );
  }

  if (offer.isPending || offer.isError || offer.data === undefined) {
    return (
      <div className="fo-mark-strip">
        {afterMark}
        {twoCircles(false)}
        {!open
          ? null
          : inTheFlow(
              t('discovery.circles.caseWhereItLands'),
              <p className="fo-mark-help">
                {offer.isError ? t('discovery.circles.readFailed') : t('discovery.circles.reading')}
              </p>,
            )}
      </div>
    );
  }

  const destination = offer.data;
  const chosen = people.find((person) => person.id === address.performerId);
  const others = destination.othersHere;

  const startsIt =
    address.performerId === viewerId
      ? t('discovery.circles.startOwn')
      : chosen === undefined
        ? t('discovery.circles.start')
        : t('discovery.circles.startFor', { who: chosen.displayName });

  return (
    <div className="fo-mark-strip">
      {afterMark}

      {twoCircles(false)}

      {already.length === 0 || already[0] === undefined ? null : markedRow(already[0])}

      {!open
        ? null
        : stage === 'choose'
          ? inTheFlow(
              t('discovery.circles.caseWhereItLands'),
              <>
                {offered.map((one) => (
                  <button
                    key={one.jobId}
                    type="button"
                    className="fo-mark-do"
                    onClick={() => {
                      setAddress((current) => ({ ...current, jobId: one.jobId }));
                      setStage('adding');
                    }}
                  >
                    {t('discovery.circles.addToEngagement', { name: one.name })}
                  </button>
                ))}

                {newEngagement('primary')}
              </>,
            )
          : inTheFlow(
              t('discovery.circles.caseWhereItLands'),
              <>
                {destination.verbs.includes('ADD') ? (
                  <button
                    type="button"
                    className="fo-mark-do"
                    disabled={mark.isPending}
                    onClick={() => {
                      press('ADD');
                    }}
                  >
                    {mark.isPending
                      ? t('discovery.mark.marking')
                      : t('discovery.circles.addTo', { where: destination.describe })}
                  </button>
                ) : null}

                {destination.verbs.includes('CREATE') ? (
                  <button
                    type="button"
                    className="fo-mark-do"
                    disabled={mark.isPending}
                    onClick={() => {
                      press('CREATE');
                    }}
                  >
                    {mark.isPending ? t('discovery.mark.marking') : startsIt}
                  </button>
                ) : null}

                {destination.verbs.includes('JOIN') && others.length === 1 ? (
                  <button
                    type="button"
                    className="fo-mark-join"
                    disabled={mark.isPending}
                    onClick={() => {
                      press('JOIN', others[0]);
                    }}
                  >
                    {t('discovery.circles.joinOne', {
                      who: others[0]?.performerName ?? t('discovery.mark.unclaimed'),
                      where: others[0]?.destination ?? '',
                    })}
                  </button>
                ) : null}

                {destination.verbs.includes('JOIN') && others.length > 1 ? (
                  <button
                    type="button"
                    className="fo-mark-who"
                    aria-expanded={joining}
                    onClick={() => {
                      setJoining((open) => !open);
                    }}
                  >
                    {t('discovery.circles.joinSomeone')}
                  </button>
                ) : null}

                {joining ? (
                  <div className="fo-mark-picker">
                    <p className="fo-mark-help">{t('discovery.circles.whoseWork')}</p>

                    <div className="fo-mark-familiar">
                      {others.map((work) => (
                        <button
                          key={work.bracketId}
                          type="button"
                          className="fo-mark-join"
                          disabled={mark.isPending}
                          onClick={() => {
                            press('JOIN', work);
                          }}
                        >
                          {t('discovery.circles.joinOne', {
                            who: work.performerName ?? t('discovery.mark.unclaimed'),
                            where: work.destination,
                          })}
                        </button>
                      ))}
                    </div>
                  </div>
                ) : null}

                {panel}

                {mark.isError ? (
                  <span className="fo-mark-failed">{t('discovery.mark.failed')}</span>
                ) : null}
              </>,
            )}
    </div>
  );
}
