import { useState, type FormEvent, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '../../../shared/ui/Button';
import { Chip } from '../../../shared/ui/Chip';
import { Input } from '../../../shared/ui/Input';
import {
  useCreateTaskCategory,
  useDeleteTaskCategory,
  useTaskCategories,
} from '../hooks/useTaskCategories';

interface TaskCategoryBarProps {
  permissions: readonly string[];
}

export function TaskCategoryBar({ permissions }: TaskCategoryBarProps): JSX.Element | null {
  const { t } = useTranslation();
  const groupings = useTaskCategories();
  const name = useCreateTaskCategory();
  const remove = useDeleteTaskCategory();
  const [typed, setTyped] = useState('');

  const [confirming, setConfirming] = useState<string | undefined>(undefined);

  const mayShape = permissions.includes('WORKSPACE_CONFIGURE');
  const categories = groupings.data?.categories ?? [];

  if (categories.length === 0 && !mayShape) {
    return null;
  }

  const submit = (event: FormEvent): void => {
    event.preventDefault();
    const wanted = typed.trim();
    if (wanted === '') {
      return;
    }
    name.mutate(wanted, { onSuccess: () => setTyped('') });
  };

  return (
    <section
      aria-label={t('task.category.region')}
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        gap: 'var(--gap-3)',
      }}
    >
      <span style={{ color: 'var(--ink-500)', fontSize: 'var(--text-body)' }}>
        {t('task.category.label')}
      </span>

      {categories.length === 0 ? (
        <span style={{ color: 'var(--ink-500)', fontSize: 'var(--text-body)' }}>
          {t('task.category.none')}
        </span>
      ) : (
        categories.map((category) => (
          <span key={category.id} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <Chip tone="neutral">{`${category.name} · ${category.taskCount}`}</Chip>
            {mayShape &&
              (confirming === category.id ? (
                <Button
                  variant="destructive"
                  onClick={() => {
                    remove.mutate(category.id);
                    setConfirming(undefined);
                  }}
                  disabled={remove.isPending}
                >
                  {t('task.category.confirmRemove', { count: category.taskCount })}
                </Button>
              ) : (
                <Button variant="quiet" onClick={() => setConfirming(category.id)}>
                  {t('task.category.remove', { name: category.name })}
                </Button>
              ))}
          </span>
        ))
      )}

      {mayShape && (
        <form onSubmit={submit} style={{ display: 'flex', gap: 'var(--gap-2)', minWidth: '16rem' }}>
          <Input
            id="task-category-name"
            aria-label={t('task.category.newLabel')}
            value={typed}
            maxLength={80}
            placeholder={t('task.category.placeholder')}
            onChange={(event) => setTyped(event.target.value)}
          />
          <Button type="submit" variant="quiet" disabled={name.isPending || typed.trim() === ''}>
            {t('task.category.add')}
          </Button>
        </form>
      )}

      {name.isError && (
        <span role="alert" style={{ color: 'var(--on-critical)', fontSize: 'var(--text-body)' }}>
          {t('task.category.taken')}
        </span>
      )}
    </section>
  );
}
