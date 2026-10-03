import Cookies from 'js-cookie';

const ACCESS_TOKEN_KEY = 'invoiceiq_access_token';
const REFRESH_TOKEN_KEY = 'invoiceiq_refresh_token';

export const getAccessToken = () => Cookies.get(ACCESS_TOKEN_KEY);
export const getRefreshToken = () => Cookies.get(REFRESH_TOKEN_KEY);

/**
 * Security Limitation: 
 * Tokens are stored using js-cookie and are accessible to client-side JavaScript.
 * They are NOT HttpOnly. This architecture requires JS to attach the Bearer token.
 * To mitigate risks, ensure XSS protections are strictly enforced elsewhere.
 */
export const setTokens = (accessToken: string, refreshToken: string) => {
  const isProd = process.env.NODE_ENV === 'production';
  Cookies.set(ACCESS_TOKEN_KEY, accessToken, { secure: isProd, sameSite: 'strict' });
  Cookies.set(REFRESH_TOKEN_KEY, refreshToken, { secure: isProd, sameSite: 'strict' });
};

export const clearTokens = () => {
  Cookies.remove(ACCESS_TOKEN_KEY);
  Cookies.remove(REFRESH_TOKEN_KEY);
};

export const isAuthenticated = () => !!getAccessToken();
