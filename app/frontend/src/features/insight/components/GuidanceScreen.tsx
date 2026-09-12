import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import {
  askForGuidance,
  fetchAiState,
  fetchAdoption,
  fetchDiscoveries,
  setAi,
  type Discovered,
  type Guidance,
} from '../api/nodePipelineApi';
import { adoptionShare } from '../adoptionShare';

const AI_KEY = ['node-pipeline', 'ai'];

/**
 * What the pipeline found, and what somebody joining this week would need to know to do it.
 *
 * The guidance is written by a language model. That is said on every card rather than once at the
 * top, because a reader who scrolls past a banner and then reads a paragraph has no way of telling
 * it from something the business wrote about itself.
 */
export function GuidanceScreen(): JSX.Element {
  const { t } = useTranslation();
  const client = useQueryClient();

  const found = useQuery({ queryKey: ['node-pipeline', 'discoveries'], queryFn: fetchDiscoveries });
  const ai = useQuery({ queryKey: AI_KEY, queryFn: fetchAiState });
  const adoption = useQuery({ queryKey: ['node-pipeline', 'adoption'], queryFn: fetchAdoption });

  const flip = useMutation({
    mutationFn: (on: boolean) => setAi(on),
    onSuccess: (state) => client.setQueryData(AI_KEY, state),
  });

  const everything = found.data ?? [];
  const templates = everything.filter((one) => one.kind === 'TEMPLATE');
  const processes = everything.filter((one) => one.kind === 'PROCESS');

  return (
    <div className="fo-page">
      <section className="fo-pb-panel">
        <div className="fo-pb-between">
          <div className="fo-pb-stack fo-pb-stack--tight">
            <span className="fo-pb-eyebrow">{t('guidance.eyebrow')}</span>
            <h2 className="fo-pb-title">{t('guidance.title')}</h2>
          </div>

          <div className="fo-guidance-switch">
            <Handbook items={everything} canAsk={ai.data?.on === true && ai.data.available} />

            <AiToggle
              available={ai.data?.available ?? false}
              on={ai.data?.on ?? false}
              model={ai.data?.modelId ?? null}
              busy={flip.isPending}
              onFlip={(on) => flip.mutate(on)}
            />
          </div>
        </div>

        <p className="fo-pb-note">{t('guidance.note')}</p>

        {adoption.data === undefined || adoption.data.marks === 0 ? null : (
          <p className="fo-pb-note">
            {t('guidance.adoption', {
              share: adoptionShare(adoption.data.naming, adoption.data.marks),
              naming: adoption.data.naming,
              marks: adoption.data.marks,
            })}
          </p>
        )}
      </section>

      {found.isPending ? <p className="fo-pb-note">{t('guidance.loading')}</p> : null}

      {!found.isPending && everything.length === 0 ? (
        <section className="fo-pb-panel">
          <p className="fo-pb-note">{t('guidance.nothingFound')}</p>
        </section>
      ) : null}

      <Group
        heading={t('guidance.templates', { count: templates.length })}
        eyebrow={t('guidance.templatesEyebrow')}
        items={templates}
        canAsk={ai.data?.on === true && ai.data.available}
      />

      <Group
        heading={t('guidance.processes', { count: processes.length })}
        eyebrow={t('guidance.processesEyebrow')}
        items={processes}
        canAsk={ai.data?.on === true && ai.data.available}
      />
    </div>
  );
}

/**
 * Fills in the guidance for everything found, then hands the whole lot to the browser's own print
 * dialogue, where "Save as PDF" produces the document.
 *
 * No PDF library. A browser already renders this page and already writes PDFs; adding a second
 * renderer server-side would mean the handbook and the screen could disagree about what the
 * pipeline found, which is the one thing a handover document must not do.
 *
 * The guidance is fetched one at a time on purpose. The model answers in tens of seconds and the
 * call budget is finite, so a dozen parallel requests would exhaust it and produce a handbook with
 * holes in it that nothing on the page would explain.
 */
function Handbook({
  items,
  canAsk,
}: {
  items: readonly Discovered[];
  canAsk: boolean;
}): JSX.Element {
  const { t } = useTranslation();
  const [progress, setProgress] = useState<{ done: number; total: number } | undefined>(undefined);

  async function prepare(): Promise<void> {
    setProgress({ done: 0, total: items.length });
    const written: Guidance[] = [];

    for (const [at, one] of items.entries()) {
      try {
        written.push(await askForGuidance(one.id));
      } catch {
        // A model that could not answer for one entry must not lose the other eleven.
      }
      setProgress({ done: at + 1, total: items.length });
    }

    setProgress(undefined);
    printHandbook(items, written, t);
  }

  if (!canAsk || items.length === 0) {
    return <></>;
  }

  return (
    <button
      type="button"
      className="ui-button ui-button-quiet"
      disabled={progress !== undefined}
      onClick={() => void prepare()}
    >
      {progress === undefined
        ? t('guidance.handbook')
        : t('guidance.handbookProgress', { done: progress.done, total: progress.total })}
    </button>
  );
}

/**
 * Writes the handbook into a window of its own and prints it.
 *
 * A separate document rather than print styles over the application: the page carries a rail, a
 * control bar and a notice desk, and every one of them is furniture a reader of a handover note
 * does not want and a print stylesheet would have to hide one at a time, forever, as the shell
 * grows.
 */
function printHandbook(
  items: readonly Discovered[],
  written: readonly Guidance[],
  t: (key: string, options?: Record<string, unknown>) => string,
): void {
  const guidanceFor = new Map(written.map((one) => [one.id, one]));
  const paper = window.open('', '_blank', 'width=900,height=1000');

  if (paper === null) {
    return;
  }

  const escape = (value: string): string =>
    value.replace(/[&<>]/g, (c) => (c === '&' ? '&amp;' : c === '<' ? '&lt;' : '&gt;'));

  const entries = items
    .map((one) => {
      const guidance = guidanceFor.get(one.id);
      const facts = [one.status, one.workType, one.responsibleRole].filter(Boolean).join(' · ');

      const steps =
        one.steps.length === 0
          ? ''
          : `<h3>${escape(t('guidance.printSteps'))}</h3><ol>${one.steps
              .map((step) => `<li>${escape(step)}</li>`)
              .join('')}</ol>`;

      const note =
        guidance === undefined
          ? `<p class="missing">${escape(t('guidance.printMissing'))}</p>`
          : `<h3>${escape(t('guidance.printGuidance'))}</h3>${guidance.text
              .split(/\n{2,}/)
              .map((p) => `<p>${escape(p.trim())}</p>`)
              .join('')}<p class="by">${escape(
              t('guidance.writtenBy', { model: guidance.modelId ?? 'a language model' }),
            )}</p>`;

      return `<article>
          <span class="facts">${escape(facts)}</span>
          <h2>${escape(one.title)}</h2>
          ${one.description === null ? '' : `<p class="observed">${escape(one.description)}</p>`}
          ${steps}
          ${note}
        </article>`;
    })
    .join('');

  paper.document.write(`<!doctype html><html><head><meta charset="utf-8">
    <title>${escape(t('guidance.printTitle'))}</title>
    <style>
      @page { margin: 18mm; }
      body { font: 11pt/1.55 Georgia, 'Times New Roman', serif; color: #111; margin: 0; }
      header { border-bottom: 2px solid #111; padding-bottom: 8mm; margin-bottom: 8mm; }
      h1 { font-size: 20pt; margin: 0 0 2mm; }
      .lede { margin: 0; color: #444; font-size: 10pt; max-width: 60em; }
      article { page-break-inside: avoid; break-inside: avoid; margin-bottom: 10mm;
                padding-bottom: 6mm; border-bottom: 1px solid #ddd; }
      h2 { font-size: 14pt; margin: 0 0 1mm; }
      h3 { font-size: 10pt; text-transform: uppercase; letter-spacing: .06em;
           color: #444; margin: 5mm 0 2mm; }
      .facts { font-size: 8.5pt; text-transform: uppercase; letter-spacing: .08em; color: #666; }
      .observed { color: #333; margin: 2mm 0 0; }
      ol { margin: 0; padding-left: 6mm; }
      li { margin-bottom: 1mm; }
      p { margin: 0 0 3mm; max-width: 62em; }
      .by { font-size: 8.5pt; color: #666; font-style: italic; }
      .missing { font-size: 9pt; color: #666; font-style: italic; }
    </style></head><body>
    <header>
      <h1>${escape(t('guidance.printTitle'))}</h1>
      <p class="lede">${escape(t('guidance.printLede'))}</p>
    </header>
    ${entries}
  </body></html>`);

  paper.document.close();
  paper.focus();
  paper.print();
}

function AiToggle({
  available,
  on,
  model,
  busy,
  onFlip,
}: {
  available: boolean;
  on: boolean;
  model: string | null;
  busy: boolean;
  onFlip: (on: boolean) => void;
}): JSX.Element {
  const { t } = useTranslation();

  if (!available) {
    return <span className="fo-pb-note">{t('guidance.noModel')}</span>;
  }

  return (
    <div className="fo-guidance-switch">
      <span className="fo-pb-note">{model}</span>
      <button
        type="button"
        className={on ? 'ui-button ui-button-primary' : 'ui-button ui-button-quiet'}
        aria-pressed={on}
        disabled={busy}
        onClick={() => onFlip(!on)}
      >
        {on ? t('guidance.aiOn') : t('guidance.aiOff')}
      </button>
    </div>
  );
}

function Group({
  heading,
  eyebrow,
  items,
  canAsk,
}: {
  heading: string;
  eyebrow: string;
  items: readonly Discovered[];
  canAsk: boolean;
}): JSX.Element | null {
  if (items.length === 0) {
    return null;
  }

  return (
    <section className="fo-pb-panel">
      <div className="fo-pb-stack fo-pb-stack--tight">
        <span className="fo-pb-eyebrow">{eyebrow}</span>
        <h3 className="fo-pb-title">{heading}</h3>
      </div>

      {items.map((one) => (
        <Card key={one.id} found={one} canAsk={canAsk} />
      ))}
    </section>
  );
}

function Card({ found, canAsk }: { found: Discovered; canAsk: boolean }): JSX.Element {
  const { t } = useTranslation();
  const [guidance, setGuidance] = useState<Guidance | undefined>(undefined);

  const ask = useMutation({
    mutationFn: () => askForGuidance(found.id),
    onSuccess: setGuidance,
  });

  return (
    <article className="fo-guidance-card">
      <div className="fo-pb-stack fo-pb-stack--tight">
        <span className="fo-pb-eyebrow">
          {found.status}
          {found.workType === null ? '' : ` · ${found.workType}`}
          {found.responsibleRole === null ? '' : ` · ${found.responsibleRole}`}
        </span>
        <h4 className="fo-guidance-name">{found.title}</h4>
      </div>

      {found.description === null ? null : <p className="fo-pb-note">{found.description}</p>}

      {found.steps.length > 0 && (
        <ol className="fo-guidance-steps">
          {found.steps.map((step, at) => (
            <li key={`${found.id}-${String(at)}`}>{step}</li>
          ))}
        </ol>
      )}

      {guidance === undefined ? (
        <button
          type="button"
          className="ui-button ui-button-quiet"
          disabled={!canAsk || ask.isPending}
          onClick={() => ask.mutate()}
        >
          {ask.isPending ? t('guidance.writing') : t('guidance.explain')}
        </button>
      ) : (
        <div className="fo-guidance-note">
          <span className="fo-pb-eyebrow">
            {t('guidance.writtenBy', { model: guidance.modelId ?? 'a language model' })}
          </span>
          {guidance.text.split(/\n{2,}/).map((paragraph, at) => (
            <p key={`${found.id}-p-${String(at)}`}>{paragraph.trim()}</p>
          ))}
        </div>
      )}

      {ask.isError ? (
        <p className="fo-pb-note" role="alert">
          {t('guidance.failed')}
        </p>
      ) : null}

      {!canAsk && guidance === undefined ? (
        <p className="fo-pb-note">{t('guidance.turnItOn')}</p>
      ) : null}
    </article>
  );
}
