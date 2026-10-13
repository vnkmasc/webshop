import { useQuery } from '@tanstack/react-query';
import { shopApi } from '@/api/shop';

/** Storefront flags from the backend (refreshed every minute). */
export function useSiteConfig() {
  return useQuery({
    queryKey: ['site-config'],
    queryFn: shopApi.siteConfig,
    staleTime: 60_000,
    refetchInterval: 60_000,
    retry: false,
  }).data ?? { frontendError: false };
}
