import { useQuery } from '@tanstack/react-query';
import { type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { useWorkVocabulary } from '../../../shared/model/workVocabulary';

interface TaskTemplatePickerProps {
  value: string;
  onChange: (taskTemplateId: string) => void;

  invalid?: boolean;
  id?: string;
}

export function TaskTemplatePicker({
  value,
  onChange,
  invalid,
  id,
}: TaskTemplatePickerProps): JSX.Element {
  const { t } = useTranslation();

  const vocabulary = useWorkVocabulary();
  const library = useQuery({
    queryKey: ['task-templates', 'approved-for-steps'],
    queryFn: () => vocabulary.listApproved(),
    staleTime: 30_000,
  });

  const templates = library.data ?? [];

  return (
    <select
      id={id}
      value={value}
      aria-invalid={invalid === true ? true : undefined}
      onChange={(event) => onChange(event.target.value)}
    >
      <option value="">{t('process.step.pickWork')}</option>
      {templates.map((template) => (
        <option key={template.id} value={template.id}>
          {template.title}
        </option>
      ))}
    </select>
  );
}
