import { apiClient } from './client';
import type { ShortenUrlRequest, ShortenUrlResponse, ClickStats, ClickDetails } from '../types';

export const urlService = {
  // URL 단축
  shortenUrl: async (request: ShortenUrlRequest): Promise<ShortenUrlResponse> => {
    const response = await apiClient.post<ShortenUrlResponse>('/api/v1/urls/shorten', request);
    return response.data;
  },

  // 통계 조회
  getStats: async (shortCode: string): Promise<ClickStats> => {
    const response = await apiClient.get<ClickStats>(`/api/v1/analytics/${shortCode}/stats`);
    return response.data;
  },

  // 클릭 상세 조회
  getClickDetails: async (shortCode: string): Promise<ClickDetails> => {
    const response = await apiClient.get<ClickDetails>(`/api/v1/analytics/${shortCode}/clicks`);
    return response.data;
  },

  // 리다이렉트 URL 생성 (표시용)
  getRedirectUrl: (shortCode: string): string => {
    // VITE_APP_DOMAIN 환경 변수 사용 (설정된 도메인으로 표시)
    const baseUrl = import.meta.env.VITE_APP_DOMAIN || window.location.origin;
    return `${baseUrl}/r/${shortCode}`;
  },
};
