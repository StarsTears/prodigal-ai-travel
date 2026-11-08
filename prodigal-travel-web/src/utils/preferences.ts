/**
 * 用户偏好与浏览器定位状态。
 *
 * 拆成独立模块的原因：偏好设置弹窗、聊天 hook、输入区都要读同一份状态，
 * 避免各自维护一份 localStorage 键名与不一致的默认值。
 */

export const DISPLAY_NAME_KEY = 'prodigal-travel-display-name';
/**
 * 标记「显示名是否被用户在本机显式设置过」。
 * 有标记 → 用本机存的值；无标记 → 回退到登录响应里的 username（含历史遗留脏值 prodigal）。
 */
export const DISPLAY_NAME_CUSTOM_KEY = 'prodigal-travel-display-name-custom';
export const AUTO_LOCATE_KEY = 'prodigal-travel-auto-locate';

/** 助手 / 智能体统一形象（用户提供的「大肥鱼」立绘，透明底） */
export const ASSISTANT_AVATAR_ALT = '大肥鱼';
/** 脸部特写：用于消息头像与欢迎卡，小尺寸下仍能看清 */
export const ASSISTANT_AVATAR_URL = '/big-fat-fish-avatar.png';
/** 半身像：用于偏好设置里的形象展示 */
export const ASSISTANT_PORTRAIT_URL = '/big-fat-fish.png';

export type GeoStatus =
  | 'idle'
  | 'pending'
  | 'granted'
  | 'denied'
  | 'unsupported'
  | 'timeout'
  | 'error'
  | 'off';

export interface GeoState {
  status: GeoStatus;
  latitude?: number;
  longitude?: number;
}

let geoState: GeoState = { status: 'idle' };
const geoListeners = new Set<(next: GeoState) => void>();

export function getGeoState(): GeoState {
  return geoState;
}

export function setGeoState(next: GeoState): void {
  geoState = next;
  geoListeners.forEach((fn) => fn(next));
}

export function subscribeGeo(fn: (next: GeoState) => void): () => void {
  geoListeners.add(fn);
  return () => {
    geoListeners.delete(fn);
  };
}

function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string | null): void {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    /* 隐私模式下 localStorage 不可用，按默认值运行即可 */
  }
}

export function readDisplayName(): string {
  return (readStorage(DISPLAY_NAME_KEY) ?? '').trim();
}

/** 用户是否在本机显式设置过显示名（而非沿用登录响应的 username）。 */
export function readDisplayNameCustom(): boolean {
  return readStorage(DISPLAY_NAME_CUSTOM_KEY) === 'true';
}

export function writeDisplayName(value: string): void {
  const v = value.trim();
  writeStorage(DISPLAY_NAME_KEY, v || null);
  // 清空时同时撤销自定义标记，回退到登录响应的 username
  writeStorage(DISPLAY_NAME_CUSTOM_KEY, v ? 'true' : null);
}

/** 默认开启：进入旅游助手时自动获取当前位置，用于天气与附近推荐。 */
export function readAutoLocate(): boolean {
  return readStorage(AUTO_LOCATE_KEY) !== 'false';
}

export function writeAutoLocate(enabled: boolean): void {
  writeStorage(AUTO_LOCATE_KEY, enabled ? 'true' : 'false');
}

let pendingRequest: Promise<GeoState> | null = null;

/**
 * 主动获取一次浏览器定位。默认定位开启时才真正请求；关闭时把状态标记为 off。
 * 权限被拒绝 / 超时只记录状态，不弹提示，后端会用 IP 做粗略定位。
 */
export function requestGeolocation(options: { force?: boolean } = {}): Promise<GeoState> {
  if (!options.force && !readAutoLocate()) {
    const off: GeoState = { status: 'off' };
    setGeoState(off);
    return Promise.resolve(off);
  }
  if (pendingRequest) return pendingRequest;

  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    const unsupported: GeoState = { status: 'unsupported' };
    setGeoState(unsupported);
    return Promise.resolve(unsupported);
  }

  setGeoState({ status: 'pending' });
  pendingRequest = new Promise<GeoState>((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const next: GeoState = {
          status: 'granted',
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
        };
        setGeoState(next);
        resolve(next);
      },
      (err: GeolocationPositionError) => {
        const status: GeoStatus =
          err.code === err.PERMISSION_DENIED
            ? 'denied'
            : err.code === err.TIMEOUT
              ? 'timeout'
              : 'error';
        const next: GeoState = { status };
        setGeoState(next);
        resolve(next);
      },
      { enableHighAccuracy: false, timeout: 15000, maximumAge: 300_000 }
    );
  }).finally(() => {
    pendingRequest = null;
  });
  return pendingRequest;
}

export const GEO_STATUS_TEXT: Record<GeoStatus, string> = {
  idle: '尚未获取位置',
  pending: '正在获取当前位置…',
  granted: '已获取当前位置',
  denied: '定位未授权，后端将使用 IP 粗略定位',
  unsupported: '当前浏览器不支持定位，后端将使用 IP 粗略定位',
  timeout: '定位超时，后端将使用 IP 粗略定位',
  error: '定位失败，后端将使用 IP 粗略定位',
  off: '已关闭自动定位，后端将使用 IP 粗略定位',
};
