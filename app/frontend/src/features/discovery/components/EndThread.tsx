import { useState, type JSX } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '../../../shared/api/client';
import { Banner } from '../../../shared/ui/Banner';
import { useEndThread } from '../hooks/useDiscovery';
import { Refusal } from './Refusal';

interface EndThreadProps {
  trackId: string;
}

export function EndThread({ trackId }: EndThreadProps): JSX.Element {
  const { t } = useTranslation();
  const end = useEndThread();

  const [ended, setEnded] = useState(false);
  const [refusal, setRefusal] = useState<string | undefined>(undefined);

  function endIt(): void {
    setRefusal(undefined);
    end.mutate(trackId, {
      onSuccess: () => setEnded(true),
      onError: (failed) => setRefusal(failed instanceof ApiError ? failed.code : 'UNKNOWN'),
    });
  }

  return (
    <div className="fo-disc-questions">
      {ended ? (
        <Banner tone="done">{t('discovery.canvas.ended')}</Banner>
      ) : (
        <div className="fo-disc-answer-row">
          <button type="button" className="fo-disc-answer" onClick={endIt}>
            {t('discovery.canvas.endThread')}
          </button>
        </div>
      )}

      <Refusal code={refusal} />
    </div>
  );
}
