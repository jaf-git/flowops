import type { JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Field } from '../../../shared/ui/Field';
import { Input } from '../../../shared/ui/Input';
import { Select } from '../../../shared/ui/Select';
import type { MetadataField, OutputKind } from '../api/taskTemplateApi';

const ROLES = ['OWNER', 'MANAGER', 'EMPLOYEE'] as const;

const OUTPUT_KINDS: readonly OutputKind[] = [
  'TEXT',
  'DESIGN',
  'REPORT',
  'SCHEDULE',
  'DECISION',
  'PHYSICAL',
  'NONE',
];

interface MetadataAskProps {
  field: MetadataField;
  value: string;
  onChange: (value: string) => void;
}

export function MetadataAsk({ field, value, onChange }: MetadataAskProps): JSX.Element {
  const { t } = useTranslation();
  const id = `metadata-${field.toLowerCase()}`;

  return (
    <Field id={id} label={t(`tasklib.metadata.ask.${field}`)} hint={t('tasklib.metadata.optional')}>
      {field === 'RESPONSIBLE_ROLE' ? (
        <Select
          id={id}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          options={[
            { value: '', label: t('tasklib.metadata.skip') },
            ...ROLES.map((role) => ({ value: role, label: t(`tasklib.metadata.role.${role}`) })),
          ]}
        />
      ) : field === 'OUTPUT_KIND' ? (
        <Select
          id={id}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          options={[
            { value: '', label: t('tasklib.metadata.skip') },
            ...OUTPUT_KINDS.map((kind) => ({
              value: kind,
              label: t(`tasklib.metadata.outputKind.${kind}`),
            })),
          ]}
        />
      ) : (
        <Input
          id={id}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          maxLength={2000}
          placeholder={t(`tasklib.metadata.placeholder.${field}`)}
        />
      )}
    </Field>
  );
}
