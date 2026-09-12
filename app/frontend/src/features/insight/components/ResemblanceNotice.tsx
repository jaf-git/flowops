import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { useAnnounce } from '../../../shared/notice/useNotices';
import { Button } from '../../../shared/ui/Button';
import { resembling } from '../api/resemblanceApi';

const NOTICE_MS = 30_000;

interface ResemblanceNoticeProps {
  said?: string;

  onPreview: (templateId: string) => void;
}

export function ResemblanceNotice({ said, onPreview }: ResemblanceNoticeProps): JSX.Element | null {
  const { t } = useTranslation();
  const announce = useAnnounce();
  const announced = useRef<string | undefined>(undefined);

  const answer = useQuery({
    queryKey: ['nodepipeline', 'resemblance', said],
    queryFn: () => resembling(said as string),
    enabled: said !== undefined && said.trim() !== '',

    retry: false,

    staleTime: 5 * 60 * 1000,
  });

  const match = answer.data?.match;

  useEffect(() => {
    if (said === undefined || match === undefined || match === null || announced.current === said) {
      return;
    }

    announced.current = said;

    announce({
      tone: 'offer',
      message: t('chat.resemblance.looksLike', { title: match.title }),
      dwellMs: NOTICE_MS,
      action: (
        <Button variant="secondary" onClick={() => onPreview(match.templateId)}>
          {t('chat.resemblance.preview')}
        </Button>
      ),
    });
  }, [said, match, announce, onPreview, t]);

  return null;
}
