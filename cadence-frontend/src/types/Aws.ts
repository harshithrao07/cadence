export enum HTTPMethod {
  GET = "GET",
  POST = "POST",
  PUT = "PUT",
  DELETE = "DELETE",
  HEAD = "HEAD",
  PATCH = "PATCH",
}

export interface MetadataDTO {
  category: string;
  subCategory: string;
  primaryKey: string;
  httpMethod: HTTPMethod;
}

export interface FileUploadResult {
  tableName: string;
  columnName: string;
  primaryKey: string;
  url: string;
}
