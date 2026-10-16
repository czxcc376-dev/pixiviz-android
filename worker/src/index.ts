import {
  APP_OS,
  APP_OS_VERSION,
  APP_UA,
  APP_VERSION,
  ACCEPT_LANGUAGE,
  BROWSER_UA,
  PIXIV_APP_API,
  PIXIV_IMAGE_HOST,
  PIXIV_OAUTH_API,
  clientTime,
  pixivHeaders,
} from './headers';
import { TOKEN_PAGE } from './token-page';

export interface Env {
  TOKENS: KVNamespace;
  WORKER_ACCESS_KEY?: string;
  CLIENT_ID?: string;
  CLIENT_SECRET?: string;
  PIXIV_REFRESH_TOKEN?: string;
}

const ACCESS_TOKEN_KEY = 'pixiv:access_token';
const REFRESH_TOKEN_KEY = 'pixiv:refresh_token';

const cors = (): Record<string, string> => ({
  'access-control-allow-origin': '*',
  'access-control-allow-headers': 'content-type,x-app-key,authorization',
  'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS',
});

const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', ...cors() },
  });

const hasKey = (request: Request, env: Env): boolean => {
  const provided = request.headers.get('x-app-key');
  return Boolean(env.WORKER_ACCESS_KEY) && provided === env.WORKER_ACCESS_KEY;
};

async function readBody(request: Request): Promise<Record<string, string>> {
  const type = request.headers.get('content-type') ?? '';
  const raw = await request.text();
  if (!raw) return {};
  if (type.includes('application/json')) {
    try {
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      const out: Record<string, string> = {};
      for (const [k, v] of Object.entries(parsed)) out[k] = String(v);
      return out;
    } catch {
      return {};
    }
  }
  const out: Record<string, string> = {};
  for (const [k, v] of new URLSearchParams(raw)) out[k] = v;
  return out;
}

/** Exchange an OAuth code or refresh_token against pixiv. */
async function pixivToken(env: Env, form: Record<string, string>): Promise<Response> {
  if (!env.CLIENT_ID || !env.CLIENT_SECRET) {
    return json(
      { error: 'worker_misconfigured', message: 'CLIENT_ID / CLIENT_SECRET 未配置' },
      500,
    );
  }
  const body = new URLSearchParams({
    client_id: env.CLIENT_ID,
    client_secret: env.CLIENT_SECRET,
    device_token: crypto.randomUUID(),
    get_secure_url: 'true',
    include_policy: 'true',
    ...form,
  });
  return fetch(PIXIV_OAUTH_API + '/auth/token', {
    method: 'POST',
    headers: {
      ...pixivHeaders(),
      'content-type': 'application/x-www-form-urlencoded',
      'Accept-Language': ACCEPT_LANGUAGE,
    },
    body: body.toString(),
  });
}

interface CachedToken {
  token: string;
  expiresAt: number;
}

async function storeTokenFromResponse(env: Env, payload: unknown): Promise<CachedToken | null> {
  const root = payload as { response?: { access_token?: string; refresh_token?: string; expires_in?: number } };
  const response = root?.response;
  if (!response?.access_token) return null;
  const expiresIn = response.expires_in ?? 3600;
  const cached: CachedToken = { token: response.access_token, expiresAt: Date.now() + expiresIn * 1000 };
  await env.TOKENS.put(ACCESS_TOKEN_KEY, JSON.stringify(cached), {
    expirationTtl: Math.max(60, expiresIn - 300),
  });
  if (response.refresh_token) {
    await env.TOKENS.put(REFRESH_TOKEN_KEY, response.refresh_token);
  }
  return cached;
}

async function refreshAccessToken(env: Env): Promise<CachedToken | null> {
  const refreshToken = (await env.TOKENS.get(REFRESH_TOKEN_KEY)) ?? env.PIXIV_REFRESH_TOKEN ?? '';
  if (!refreshToken) return null;
  const upstream = await pixivToken(env, { grant_type: 'refresh_token', refresh_token: refreshToken });
  if (!upstream.ok) return null;
  return storeTokenFromResponse(env, await upstream.json());
}

async function currentAccessToken(env: Env): Promise<string | null> {
  const raw = await env.TOKENS.get(ACCESS_TOKEN_KEY);
  if (raw) {
    try {
      const cached = JSON.parse(raw) as CachedToken;
      if (cached.expiresAt - 60_000 > Date.now()) return cached.token;
    } catch {
      /* fall through to refresh */
    }
  }
  const refreshed = await refreshAccessToken(env);
  return refreshed?.token ?? null;
}

async function handleApi(request: Request, env: Env, pathWithQuery: string): Promise<Response> {
  const forwardedAuth = request.headers.get('authorization');
  let token = forwardedAuth?.startsWith('Bearer ') ? forwardedAuth.slice(7) : null;
  if (!token) token = await currentAccessToken(env);
  if (!token) {
    return json(
      {
        error: 'no_credential',
        message: '未配置 pixiv 凭据。请在 App 内登录，或为 Worker 设置 PIXIV_REFRESH_TOKEN。',
      },
      401,
    );
  }

  const send = async (bearer: string): Promise<Response> => {
    const headers = new Headers(pixivHeaders());
    headers.set('authorization', 'Bearer ' + bearer);
    headers.set('accept', 'application/json');
    const contentType = request.headers.get('content-type');
    if (contentType) headers.set('content-type', contentType);
    const init: RequestInit = { method: request.method, headers };
    if (request.method !== 'GET' && request.method !== 'HEAD') {
      init.body = await request.clone().text();
    }
    return fetch(PIXIV_APP_API + pathWithQuery, init);
  };

  let upstream = await send(token);
  if (upstream.status === 401 && !forwardedAuth) {
    const refreshed = await refreshAccessToken(env);
    if (refreshed?.token) upstream = await send(refreshed.token);
  }

  const headers = new Headers(cors());
  headers.set('content-type', upstream.headers.get('content-type') ?? 'application/json; charset=utf-8');
  return new Response(upstream.body, { status: upstream.status, headers });
}

async function handleImage(env: Env, rest: string): Promise<Response> {
  const upstream = await fetch(PIXIV_IMAGE_HOST + '/' + rest, {
    headers: { referer: 'https://www.pixiv.net/', 'user-agent': BROWSER_UA },
    cf: { cacheEverything: true, cacheTtl: 86400 },
  });
  const headers = new Headers(cors());
  for (const name of ['content-type', 'content-length', 'etag', 'last-modified']) {
    const value = upstream.headers.get(name);
    if (value) headers.set(name, value);
  }
  headers.set('cache-control', 'public, max-age=86400');
  return new Response(upstream.body, { status: upstream.status, headers });
}

async function handleHealth(env: Env): Promise<Response> {
  const raw = await env.TOKENS.get(ACCESS_TOKEN_KEY);
  let expiresAt: number | null = null;
  if (raw) {
    try {
      expiresAt = (JSON.parse(raw) as CachedToken).expiresAt;
    } catch {
      expiresAt = null;
    }
  }
  const serverToken = Boolean((await env.TOKENS.get(REFRESH_TOKEN_KEY)) ?? env.PIXIV_REFRESH_TOKEN);
  return json({
    ok: true,
    mode: serverToken ? 'server-token' : 'client-token',
    hasServerToken: serverToken,
    tokenExpiresAt: expiresAt,
    now: Date.now(),
  });
}

/**
 * Guest browsing through pixiv's public web endpoints. Verified without any cookie:
 * /ajax/illust/{id}, /ajax/illust/discovery, /ajax/search/artworks, /ranking.php?format=json.
 * R-18 modes answer 403 here, so adult content still requires a signed-in token.
 */
const WEB_ALLOWED_PREFIXES = ['/ajax/', '/ranking.php', '/touch/'];

async function handleWeb(env: Env, rest: string): Promise<Response> {
  if (!WEB_ALLOWED_PREFIXES.some((prefix) => rest.startsWith(prefix))) {
    return json({ error: 'path_not_allowed', path: rest }, 403);
  }
  const upstream = await fetch('https://www.pixiv.net' + rest, {
    headers: {
      'user-agent': BROWSER_UA,
      referer: 'https://www.pixiv.net/',
      'accept-language': ACCEPT_LANGUAGE,
      accept: 'application/json, text/plain, */*',
    },
    cf: { cacheEverything: true, cacheTtl: 300 },
  });
  const headers = new Headers(cors());
  headers.set('content-type', upstream.headers.get('content-type') ?? 'application/json; charset=utf-8');
  headers.set('cache-control', 'public, max-age=300');
  return new Response(upstream.body, { status: upstream.status, headers });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: cors() });
    }

    if (path === '/' || path === '/health') {
      return handleHealth(env);
    }

    if (path === '/tools/token') {
      return new Response(TOKEN_PAGE, {
        headers: { 'content-type': 'text/html; charset=utf-8', ...cors() },
      });
    }

    if (path === '/auth/token' && request.method === 'POST') {
      const body = await readBody(request);
      const grant = body.grant_type ?? '';
      if (grant !== 'authorization_code' && !hasKey(request, env)) {
        return json({ error: 'unauthorized', message: '该 grant 需要 X-App-Key' }, 401);
      }
      if (grant !== 'authorization_code' && grant !== 'refresh_token') {
        return json({ error: 'invalid_grant', message: '不支持的 grant_type' }, 400);
      }
      const form: Record<string, string> = { grant_type: grant };
      if (grant === 'authorization_code') {
        form.code = body.code ?? '';
        form.code_verifier = body.code_verifier ?? '';
      } else {
        form.refresh_token = body.refresh_token ?? '';
      }
      const upstream = await pixivToken(env, form);
      const headers = new Headers(cors());
      headers.set('content-type', upstream.headers.get('content-type') ?? 'application/json; charset=utf-8');
      return new Response(upstream.body, { status: upstream.status, headers });
    }

    if (path === '/admin/token' && request.method === 'POST') {
      if (!hasKey(request, env)) {
        return json({ error: 'unauthorized', message: '需要 X-App-Key' }, 401);
      }
      const body = await readBody(request);
      const refreshToken = body.refresh_token ?? '';
      if (!refreshToken) {
        return json({ error: 'missing_refresh_token' }, 400);
      }
      const upstream = await pixivToken(env, { grant_type: 'refresh_token', refresh_token: refreshToken });
      const payload = (await upstream.json()) as { response?: { user?: { id?: string; name?: string } } };
      if (!upstream.ok) {
        return json({ error: 'invalid_refresh_token', detail: payload }, 400);
      }
      await env.TOKENS.put(REFRESH_TOKEN_KEY, refreshToken);
      await storeTokenFromResponse(env, payload);
      return json({ ok: true, user: payload?.response?.user ?? null });
    }

    if (path.startsWith('/web/')) {
      if (!hasKey(request, env)) {
        return json({ error: 'unauthorized', message: '需要 X-App-Key' }, 401);
      }
      return handleWeb(env, path.slice('/web'.length) + url.search);
    }

    if (path.startsWith('/img/')) {
      if (!hasKey(request, env)) {
        return json({ error: 'unauthorized', message: '需要 X-App-Key' }, 401);
      }
      return handleImage(env, path.slice('/img/'.length) + url.search);
    }

    if (path.startsWith('/v1/') || path.startsWith('/v2/')) {
      if (!hasKey(request, env) && !request.headers.get('authorization')) {
        return json({ error: 'unauthorized', message: '需要 X-App-Key' }, 401);
      }
      return handleApi(request, env, path + url.search);
    }

    return json({ error: 'not_found', path }, 404);
  },
};

export const __testing = { APP_OS, APP_OS_VERSION, APP_UA, APP_VERSION, clientTime };
