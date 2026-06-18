declare namespace API {
  type InitialState = {
    fetchUserInfo?: () => Promise<CurrentUser | undefined>;
    currentUser?: CurrentUser;
  };

  type CurrentUser = {
    id: number;
    username: string;
    nickname?: string;
    email?: string;
    roles: string[];
    permissions: string[];
    menus: MenuNode[];
  };

  type MenuNode = {
    id: number;
    name: string;
    path?: string;
    icon?: string;
    component?: string;
    children?: MenuNode[];
  };

  type ApiResponse<T> = {
    code: number;
    message: string;
    data: T;
  };

  type PageResult<T> = {
    list: T[];
    total: number;
    page: number;
    pageSize: number;
  };
}

export {};
