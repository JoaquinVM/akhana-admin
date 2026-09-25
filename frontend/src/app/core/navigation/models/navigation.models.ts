export interface NavItem {
  label: string;
  route: string;
  icon?: string;
  badge?: string;
  caption?: string;
}

export interface NavGroup {
  id: string;
  label: string;
  icon?: string;
  children: NavItem[];
}
