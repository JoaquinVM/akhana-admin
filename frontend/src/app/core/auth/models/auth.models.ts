export type Role = 'ADMIN' | 'SELLER';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  id: string;
  username: string;
  fullName?: string;
  role: Role;
  permissions?: string[];
  message: string;
}

export interface UserSession {
  id: string;
  username: string;
  fullName?: string;
  role: Role;
  permissions?: string[];
}
