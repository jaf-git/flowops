import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { fetchInvitation, type InvitationPreview } from '../api/invitationApi';

export function useInvitation(token: string): UseQueryResult<InvitationPreview, Error> {
  const { i18n } = useTranslation();

  return useQuery({
    queryKey: ['workspace', 'invitation', token, i18n.language],
    queryFn: () => fetchInvitation(token),
    enabled: token !== '',
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });
}
