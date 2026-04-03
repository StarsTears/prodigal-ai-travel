declare module '*.css';

declare namespace NodeJS {
  interface ProcessEnv {
    readonly UMI_APP_API_BASE?: string;
    readonly UMI_APP_USER_ID?: string;
  }
}
