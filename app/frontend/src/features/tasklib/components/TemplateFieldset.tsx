import type { JSX, KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { Field } from '../../../shared/ui/Field';
import { IconButton } from '../../../shared/ui/IconButton';
import { Input } from '../../../shared/ui/Input';
import { Textarea } from '../../../shared/ui/Textarea';
import type { TemplatePriority } from '../api/taskTemplateApi';
import type { TemplateDraftController } from '../hooks/useTemplateDraft';

const PRIORITIES: readonly TemplatePriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];

interface TemplateFieldsetProps {
  draft: TemplateDraftController;

  knownTypes: readonly string[];

  idPrefix: string;

  showGroupHeadings?: boolean;
}

export function TemplateFieldset({
  draft,
  knownTypes,
  idPrefix,
  showGroupHeadings = false,
}: TemplateFieldsetProps): JSX.Element {
  const { t } = useTranslation();
  const { values, set } = draft;

  const onChecklistKey = (event: KeyboardEvent<HTMLInputElement>): void => {
    if (event.key === 'Enter') {
      event.preventDefault();
      draft.addChecklistItem();
    }
  };

  return (
    <>
      {showGroupHeadings && <p className="fo-form-group">{t('tasklib.form.essentials')}</p>}

      <Field id={`${idPrefix}-title`} label={t('tasklib.form.title')}>
        <Input
          id={`${idPrefix}-title`}
          value={values.title}
          onChange={(event) => set('title', event.target.value)}
          placeholder={t('tasklib.form.titlePlaceholder')}
        />
      </Field>

      <Field
        id={`${idPrefix}-type`}
        label={t('tasklib.form.type')}
        hint={t('tasklib.form.typeHint')}
      >
        <Input
          id={`${idPrefix}-type`}
          list={`${idPrefix}-types`}
          value={values.type}
          onChange={(event) => set('type', event.target.value)}
          placeholder={t('tasklib.form.typePlaceholder')}
        />
        <datalist id={`${idPrefix}-types`}>
          {knownTypes.map((known) => (
            <option key={known} value={known} />
          ))}
        </datalist>
      </Field>

      <fieldset className="fo-priority-set">
        <legend className="fo-visually-hidden">{t('tasklib.form.priority')}</legend>
        {PRIORITIES.map((option) => (
          <button
            key={option}
            type="button"
            className="fo-priority-chip"
            data-priority={option}
            aria-pressed={values.priority === option}
            onClick={() => set('priority', option)}
          >
            {t(`task.priority.${option}`)}
          </button>
        ))}
      </fieldset>

      {showGroupHeadings && <p className="fo-form-group">{t('tasklib.form.advanced')}</p>}

      <Field id={`${idPrefix}-description`} label={t('tasklib.form.description')}>
        <Textarea
          id={`${idPrefix}-description`}
          rows={2}
          value={values.description}
          onChange={(event) => set('description', event.target.value)}
        />
      </Field>

      <Field id={`${idPrefix}-estimate`} label={t('tasklib.form.estimate')}>
        <Input
          id={`${idPrefix}-estimate`}
          type="number"
          min="0"
          step="0.25"
          value={values.estimate}
          onChange={(event) => set('estimate', event.target.value)}
        />
      </Field>

      <Field
        id={`${idPrefix}-checklist`}
        label={t('tasklib.form.checklist')}
        hint={t('tasklib.form.checklistHint')}
      >
        {values.checklist.length > 0 && (
          <ul className="fo-checklist-draft">
            {values.checklist.map((item, index) => (
              <li key={`${item}-${index}`}>
                <span>{item}</span>
                <IconButton
                  icon="close"
                  label={t('tasklib.form.removeItem', { item })}
                  onClick={() => draft.removeChecklistItem(index)}
                />
              </li>
            ))}
          </ul>
        )}
        <Input
          id={`${idPrefix}-checklist`}
          value={values.pendingChecklistItem}
          onChange={(event) => set('pendingChecklistItem', event.target.value)}
          onKeyDown={onChecklistKey}
          placeholder={t('tasklib.form.checklistPlaceholder')}
        />
      </Field>
    </>
  );
}
