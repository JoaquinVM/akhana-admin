import type { PermissionKey } from '../navigation.config';

export interface NavItem {
  label: string;
  route: string;
  icon?: string;
  badge?: string;
  caption?: string;
  permissionKey?: PermissionKey;
}

export interface NavGroup {
  id: string;
  label: string;
  icon?: string;
  children: NavItem[];
}
