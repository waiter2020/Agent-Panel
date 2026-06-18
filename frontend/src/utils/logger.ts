const isDev = process.env.NODE_ENV === 'development';
const prefix = '[AgentPanel]';

export const logger = {
  debug: (...args: unknown[]) => {
    if (isDev) {
      console.debug(prefix, ...args);
    }
  },
  warn: (...args: unknown[]) => {
    console.warn(prefix, ...args);
  },
  error: (...args: unknown[]) => {
    console.error(prefix, ...args);
  },
};
