import { useState, type JSX, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { ConversationInspector } from './ConversationInspector';
import type { HandoverCandidate } from './HandOverControl';
import { AnalyticsTab } from './AnalyticsTab';
import { ShelfTab } from './ShelfTab';
import { WaitingTab } from './WaitingTab';
import { WorkTab } from './WorkTab';
import { useConversationWaits, useConversationWork } from '../hooks/useConversationWork';
import { ReadingMessageProvider } from './ReadingMessageProvider';

interface ConversationWorkPanelProps {
  readonly conversationId: string;

  readonly thread: ReactNode;
  readonly onOpenMessage?: (messageId: string) => void;

  readonly colleagues?: readonly HandoverCandidate[];

  readonly viewerId?: string;

  readonly nameOfConversation?: (conversationId: string) => string | undefined;

  readonly onOpenConversation?: (conversationId: string) => void;

  readonly onOpenGraph?: (jobId: string) => void;
}

type Tab = 'thread' | 'work' | 'waiting' | 'shelf' | 'analytics';

export function ConversationWorkPanel({
  conversationId,
  thread,
  onOpenMessage,
  colleagues,
  viewerId,
  nameOfConversation,
  onOpenConversation,
  onOpenGraph,
}: ConversationWorkPanelProps): JSX.Element {
  const { t } = useTranslation();
  const [tab, setTab] = useState<Tab>('thread');

  const work = useConversationWork(conversationId);
  const waits = useConversationWaits(conversationId);

  const liveWork = (work.data ?? []).filter((bracket) => bracket.live).length;
  const openWaits = (waits.data ?? []).length;

  const tabButton = (id: Tab, label: string, count?: number): JSX.Element => (
    <button
      type="button"
      role="tab"
      aria-selected={tab === id}
      className="fo-thread-tab"
      onClick={() => {
        setTab(id);
      }}
    >
      {label}

      {count === undefined || count === 0 ? null : (
        <span className="fo-thread-tab-count">{count}</span>
      )}
    </button>
  );

  return (
    <ReadingMessageProvider>
      <div className="fo-thread-panel">
        <header className="fo-thread-header">
          <nav className="fo-thread-tabs" role="tablist" aria-label={t('discovery.work.tabs')}>
            {tabButton('thread', t('discovery.work.tab.thread'))}
            {tabButton('work', t('discovery.work.tab.work'), liveWork)}
            {tabButton('waiting', t('discovery.work.tab.waiting'), openWaits)}

            {tabButton('shelf', t('discovery.work.tab.shelf'))}

            {tabButton('analytics', t('discovery.work.tab.analytics'))}
          </nav>
        </header>

        <div className="fo-thread-split">
          <div className="fo-thread-body" role="tabpanel">
            {tab === 'thread' ? thread : null}
            {tab === 'work' ? (
              <WorkTab
                conversationId={conversationId}
                viewerId={viewerId}

                onOpenMessage={
                  onOpenMessage === undefined
                    ? undefined
                    : (messageId) => {
                        setTab('thread');
                        onOpenMessage(messageId);
                      }
                }
                colleagues={colleagues}
              />
            ) : null}
            {tab === 'waiting' ? <WaitingTab conversationId={conversationId} /> : null}
            {tab === 'shelf' ? <ShelfTab conversationId={conversationId} /> : null}
            {tab === 'analytics' ? <AnalyticsTab conversationId={conversationId} /> : null}
          </div>

          <ConversationInspector
            conversationId={conversationId}
            viewerId={viewerId}
            nameOfConversation={nameOfConversation}
            onOpenConversation={onOpenConversation}
            onOpenGraph={onOpenGraph}
          />
        </div>
      </div>
    </ReadingMessageProvider>
  );
}
