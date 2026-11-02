import manifestText from '../../../../public/manifest.webmanifest' with { loader: 'text' };
import workerSource from '../../../../public/sw.js' with { loader: 'text' };
import offlinePage from '../../../../public/offline.html' with { loader: 'text' };
import { isInstalledApp, registerServiceWorker } from './installed-app';

const fakeWindow = (standaloneMedia: boolean | null, iosStandalone?: boolean) => {
  const register = vi.fn().mockResolvedValue(undefined);
  return {
    register,
    win: {
      matchMedia: standaloneMedia == null ? undefined : () => ({ matches: standaloneMedia }),
      navigator: { standalone: iosStandalone, serviceWorker: { register } }
    } as unknown as Window
  };
};

describe('isInstalledApp', () => {
  it('reconoce display-mode standalone y el navigator.standalone de iPhone', () => {
    expect(isInstalledApp(fakeWindow(true).win)).toBe(true);
    expect(isInstalledApp(fakeWindow(false, true).win)).toBe(true);
    expect(isInstalledApp(fakeWindow(false, false).win)).toBe(false);
  });

  it('sin matchMedia no se considera instalada', () => {
    expect(isInstalledApp(fakeWindow(null).win)).toBe(false);
  });
});

describe('registerServiceWorker (MOV-03)', () => {
  it('registra el worker solo en la app instalada', () => {
    const installed = fakeWindow(true);
    registerServiceWorker(installed.win);
    expect(installed.register).toHaveBeenCalledWith('/sw.js');

    const browser = fakeWindow(false);
    registerServiceWorker(browser.win);
    expect(browser.register).not.toHaveBeenCalled();
  });
});

describe('manifest (CA-03)', () => {
  const manifest = JSON.parse(manifestText);

  it('tiene nombre, standalone, raíz e íconos de 192 y 512', () => {
    expect(manifest.name).toBe('Akhana');
    expect(manifest.short_name).toBe('Akhana');
    expect(manifest.display).toBe('standalone');
    expect(manifest.start_url).toBe('/');
    expect(manifest.scope).toBe('/');
    const sizes = manifest.icons.filter((icon: { purpose: string }) => icon.purpose === 'any')
      .map((icon: { sizes: string }) => icon.sizes);
    expect(sizes).toEqual(['192x192', '512x512']);
  });
});

describe('offline.html (CA-09, CA-10)', () => {
  it('dice qué revisar y ofrece Reintentar, sin depender de la red', () => {
    expect(offlinePage).toContain('No se pudo conectar. Revisa tu internet y que Tailscale esté encendido.');
    expect(offlinePage).toContain('location.reload()');
    expect(offlinePage).toContain('>Reintentar<');
    expect(offlinePage).not.toContain('https://');
  });
});

describe('service worker (MOV-03, CA-09, CA-11)', () => {
  const ORIGIN = 'https://akhana.example.ts.net';
  const OFFLINE = new Response('offline');
  let handlers: Record<string, (event: any) => void>;
  let stored: string[];
  let cacheNames: string[];
  let networkFetch: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    handlers = {};
    stored = [];
    cacheNames = ['akhana-offline-old', 'other-old'];
    networkFetch = vi.fn();
    const caches = {
      open: vi.fn(async (name: string) => {
        cacheNames.push(name);
        return { addAll: async (urls: string[]) => { stored.push(...urls); } };
      }),
      keys: vi.fn(async () => cacheNames),
      delete: vi.fn(async (name: string) => { cacheNames = cacheNames.filter(c => c !== name); return true; }),
      match: vi.fn(async (url: string) => url === '/offline.html' ? OFFLINE : undefined)
    };
    const self = {
      location: { origin: ORIGIN },
      addEventListener: (type: string, handler: (event: any) => void) => { handlers[type] = handler; },
      skipWaiting: vi.fn(async () => undefined),
      clients: { claim: vi.fn(async () => undefined) }
    };
    new Function('self', 'caches', 'fetch', workerSource)(self, caches, networkFetch);
  });

  // Runs the fetch handler; undefined means the worker left the request to the browser
  const intercept = (path: string, mode: string): Promise<Response> | undefined => {
    let responded: Promise<Response> | undefined;
    handlers['fetch']({ request: { url: `${ORIGIN}${path}`, mode }, respondWith: (r: Promise<Response>) => { responded = r; } });
    return responded;
  };
  const wait = async (type: string) => {
    let done: Promise<unknown> = Promise.resolve();
    handlers[type]({ waitUntil: (p: Promise<unknown>) => { done = p; } });
    await done;
  };

  it('CA-11: solo guarda la página sin conexión y su ícono', async () => {
    await wait('install');
    expect(stored).toEqual(['/offline.html', '/icons/icon-192.png']);
    expect(workerSource).not.toContain('cache.put');
  });

  it('CA-11: al activarse borra las cachés de versiones anteriores', async () => {
    await wait('install');
    await wait('activate');
    expect(cacheNames).toEqual(['akhana-offline-v1']);
  });

  it('CA-11: no intercepta la API, los bundles ni otros orígenes', () => {
    expect(intercept('/api/dashboard', 'cors')).toBeUndefined();
    expect(intercept('/api/report.pdf', 'navigate')).toBeUndefined();
    expect(intercept('/main-ABC123.js', 'cors')).toBeUndefined();
    let responded = false;
    handlers['fetch']({ request: { url: 'https://fonts.googleapis.com/css', mode: 'navigate' }, respondWith: () => { responded = true; } });
    expect(responded).toBe(false);
  });

  it('CA-11: con conexión, abrir una pantalla siempre va a la red', async () => {
    const page = new Response('index');
    networkFetch.mockResolvedValue(page);
    expect(await intercept('/dashboard', 'navigate')).toBe(page);
    expect(networkFetch).toHaveBeenCalled();
  });

  it('CA-09: si la red falla muestra la página sin conexión', async () => {
    networkFetch.mockRejectedValue(new TypeError('Failed to fetch'));
    expect(await intercept('/', 'navigate')).toBe(OFFLINE);
  });

  it('CA-09: si el servidor no responde (502) también', async () => {
    networkFetch.mockResolvedValue(new Response('', { status: 502 }));
    expect(await intercept('/', 'navigate')).toBe(OFFLINE);
  });

  // Like the real fetch: stays pending until its signal aborts it
  const hungFetch = (_: unknown, init: { signal: AbortSignal }) => new Promise<Response>((_resolve, reject) => {
    init.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
  });

  it('CA-09: si la petición queda colgada, a los 10 s también', async () => {
    vi.useFakeTimers();
    try {
      networkFetch.mockImplementation(hungFetch);
      const response = intercept('/', 'navigate')!;
      await vi.advanceTimersByTimeAsync(10000);
      expect(await response).toBe(OFFLINE);
    } finally {
      vi.useRealTimers();
    }
  });

  it('CA-02: al vencer los 10 s aborta la petición colgada, no antes', async () => {
    vi.useFakeTimers();
    try {
      networkFetch.mockImplementation(hungFetch);
      const response = intercept('/', 'navigate')!;
      const { signal } = networkFetch.mock.calls[0][1];
      await vi.advanceTimersByTimeAsync(9999);
      expect(signal.aborted).toBe(false);
      await vi.advanceTimersByTimeAsync(1);
      expect(signal.aborted).toBe(true);
      await response;
    } finally {
      vi.useRealTimers();
    }
  });

  it('CA-03: una respuesta que ya llegó no se corta al pasar los 10 s', async () => {
    vi.useFakeTimers();
    try {
      const page = new Response('index');
      networkFetch.mockResolvedValue(page);
      expect(await intercept('/', 'navigate')).toBe(page);
      await vi.advanceTimersByTimeAsync(10000);
      expect(networkFetch.mock.calls[0][1].signal.aborted).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it.each([404, 500])('un error de la app (%i) no se confunde con estar sin conexión', async status => {
    const appError = new Response('', { status });
    networkFetch.mockResolvedValue(appError);
    expect(await intercept('/', 'navigate')).toBe(appError);
  });
});
