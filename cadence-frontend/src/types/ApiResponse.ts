export interface ApiResponseDTO<T> {
  success: boolean;
  data: T;
  message: string;
}

export interface PaginatedResponseDTO<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
