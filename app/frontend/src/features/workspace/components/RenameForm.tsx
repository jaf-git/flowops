import { useState, type FormEvent, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { Input } from '../../../shared/ui/Input';

interface RenameFormProps {
  id: string;

  label: string;
  current: string;
  pending: boolean;
  onRename: (name: string) => void;
}

export function RenameForm({
  id,
  label,
  current,
  pending,
  onRename,
}: RenameFormProps): JSX.Element {
  const { t } = useTranslation();
  const [draft, setDraft] = useState(current);

  const [shownFor, setShownFor] = useState(current);
  if (shownFor !== current) {
    setShownFor(current);
    setDraft(current);
  }

  const trimmed = draft.trim();
  const changed = trimmed !== '' && trimmed !== current;

  return (
    <form
      className="fo-org-rename"
      onSubmit={(event: FormEvent) => {
        event.preventDefault();
        if (changed) {
          onRename(trimmed);
        }
      }}
    >
      <label className="fo-org-rename-label" htmlFor={id}>
        {label}
      </label>
      <Input
        id={id}
        value={draft}
        onChange={(event) => {
          setDraft(event.target.value);
        }}
      />
      <Button variant="quiet" type="submit" disabled={!changed} loading={pending}>
        {t('organisation.save')}
      </Button>
    </form>
  );
}
