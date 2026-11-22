import { Role } from '../../auth/models/auth.models';

export interface User {
  id: string;
  fullName: string;
  username: string;
  role: Role;
  active: boolean;
  permissions: string[];
  createdAt: string;
  createdBy: string;
  updatedAt: string | null;
  updatedBy: string | null;
  deletedAt: string | null;
  deletedBy: string | null;
  // Second factor of administrators (USR-08): only whether it is active, never its key
  twoFactorEnabled: boolean;
  // 098: the second factor waits for the activation code handed by an administrator
  activationPending: boolean;
  // 098: the one-time activation code, only in the answer that generated it
  activationCode?: string | null;
}

export interface CreateUserRequest {
  fullName: string;
  username: string;
  password: string;
  role: Role;
}

export interface UpdateUserRequest {
  fullName: string;
  role: Role;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
