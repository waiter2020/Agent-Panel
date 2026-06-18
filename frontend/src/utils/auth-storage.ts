const REMEMBER_KEY = 'rememberMe';
const USERNAME_KEY = 'savedUsername';
const PASSWORD_KEY = 'savedPassword';

function encode(value: string): string {
  try {
    return btoa(encodeURIComponent(value));
  } catch {
    return value;
  }
}

function decode(value: string): string {
  try {
    return decodeURIComponent(atob(value));
  } catch {
    return value;
  }
}

export type SavedCredentials = {
  rememberMe: boolean;
  username: string;
  password: string;
};

export function getSavedCredentials(): SavedCredentials | null {
  const rememberMe = localStorage.getItem(REMEMBER_KEY) === 'true';
  if (!rememberMe) {
    return null;
  }
  const username = localStorage.getItem(USERNAME_KEY);
  const encodedPassword = localStorage.getItem(PASSWORD_KEY);
  if (!username || !encodedPassword) {
    return null;
  }
  return {
    rememberMe: true,
    username,
    password: decode(encodedPassword),
  };
}

export function saveCredentials(username: string, password: string): void {
  localStorage.setItem(REMEMBER_KEY, 'true');
  localStorage.setItem(USERNAME_KEY, username);
  localStorage.setItem(PASSWORD_KEY, encode(password));
}

export function clearSavedCredentials(): void {
  localStorage.removeItem(REMEMBER_KEY);
  localStorage.removeItem(USERNAME_KEY);
  localStorage.removeItem(PASSWORD_KEY);
}
