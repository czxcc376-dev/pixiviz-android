import { md5Hex } from './md5';

/** Public constants of the official pixiv Android client (needed for the OAuth handshake). */
export const APP_UA = 'PixivAndroidApp/5.0.234 (Android 11; Pixel 4)';
export const APP_OS = 'android';
export const APP_OS_VERSION = '11';
export const APP_VERSION = '5.0.234';
export const ACCEPT_LANGUAGE = 'zh-CN';
export const HASH_SALT =
  '28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c';

/** A desktop browser UA is required by i.pximg.net image endpoints. */
export const BROWSER_UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36';

export const PIXIV_APP_API = 'https://app-api.pixiv.net';
export const PIXIV_OAUTH_API = 'https://oauth.secure.pixiv.net';
export const PIXIV_IMAGE_HOST = 'https://i.pximg.net';

export function clientTime(): string {
  return new Date().toISOString().replace(/\.\d{3}Z$/, '+00:00');
}

/** Headers shared by every pixiv app-api / oauth request. */
export function pixivHeaders(): Record<string, string> {
  const time = clientTime();
  return {
    'User-Agent': APP_UA,
    'App-OS': APP_OS,
    'App-OS-Version': APP_OS_VERSION,
    'App-Version': APP_VERSION,
    'Accept-Language': ACCEPT_LANGUAGE,
    'X-Client-Time': time,
    'X-Client-Hash': md5Hex(time + HASH_SALT),
  };
}
