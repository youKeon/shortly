// API Response types
export interface ShortenUrlRequest {
  originalUrl: string;
  customCode?: string;
}

export interface ShortenUrlResponse {
  shortCode: string;
  originalUrl: string;
  createdAt?: string; // Optional: 백엔드에서 제공하지 않음
}

export interface ClickStats {
  shortCode: string;
  totalClicks: number;
  clicksLast24Hours: number;
  clicksLast7Days: number;
}

export interface Click {
  clickedAt: string;
}

export interface ClickDetails {
  shortCode: string;
  totalCount: number;
  clicks: Click[];
}

export interface ErrorResponse {
  code: string;
  message: string;
  timestamp: string;
}
