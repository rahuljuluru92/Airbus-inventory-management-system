export interface ErrorResponse {
  status: number;
  message: string;
  timestamp: string;
  details?: string[];
}
