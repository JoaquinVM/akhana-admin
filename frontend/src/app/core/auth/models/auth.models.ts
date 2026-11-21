export type Role = 'OPERATOR' | 'ADMINISTRATOR';

export interface LoginRequest {
  username: string;
  password: string;
}

// An administrator gets no session with the password alone: the second factor still to enter (USR-08). secret and
// otpauthUri come only while activating it, for the QR code
export interface TwoFactorChallenge {
  challengeId: string;
  setupRequired: boolean;
  secret?: string | null;
  otpauthUri?: string | null;
}

export interface LoginResponse {
  token: string | null;
  id: string;
  username: string;
  fullName?: string;
  role: Role;
  permissions?: string[];
  mustChangePassword?: boolean;
  message: string;
  twoFactor?: TwoFactorChallenge | null;
}

export interface TwoFactorLoginRequest {
  challengeId: string;
  code: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

// GET /api/auth/me: the user as the server sees it now (role and permissions may have changed since the login)
export interface UserProfile {
  id: string;
  username: string;
  fullName?: string | null;
  role: Role;
  permissions?: string[] | null;
  mustChangePassword?: boolean | null;
}

export interface UserSession {
  id: string;
  username: string;
  fullName?: string;
  role: Role;
  permissions?: string[];
  mustChangePassword?: boolean;
}
