export type Role = 'OPERATOR' | 'ADMINISTRATOR';

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
  mustChangePassword?: boolean;
  message: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface UserSession {
  id: string;
  username: string;
  fullName?: string;
  role: Role;
  permissions?: string[];
  mustChangePassword?: boolean;
}
