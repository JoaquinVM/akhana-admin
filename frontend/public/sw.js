// MOV-03 (specs/mobile.md): the only thing this worker does is show offline.html when the server cannot be reached.
// It keeps that page and its icon, never screens or API data (CA-11), so a new version is used as soon as it opens.
// Change CACHE_NAME when offline.html or the icon change: otherwise phones keep the old copy.
const CACHE_NAME = 'akhana-offline-v1';
const OFFLINE_PAGE = '/offline.html';
const OFFLINE_ICON = '/icons/icon-192.png';
// With Tailscale on and the server down the request can hang instead of failing
const NAVIGATION_TIMEOUT_MS = 10000;
const GATEWAY_ERRORS = [502, 503, 504];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll([OFFLINE_PAGE, OFFLINE_ICON]))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(names => Promise.all(names.filter(name => name !== CACHE_NAME).map(name => caches.delete(name))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin || url.pathname.startsWith('/api/')) {
    return;
  }
  if (event.request.mode === 'navigate') {
    event.respondWith(navigate(event.request));
  } else if (url.pathname === OFFLINE_ICON) {
    event.respondWith(caches.match(OFFLINE_ICON).then(icon => icon || fetch(event.request)));
  }
});

// The page answers at the requested URL, so Reintentar is a plain reload of what was being opened
async function navigate(request) {
  try {
    const response = await Promise.race([fetch(request), timeout()]);
    return GATEWAY_ERRORS.includes(response.status) ? await offlinePage(response) : response;
  } catch {
    return offlinePage(Response.error());
  }
}

function timeout() {
  return new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), NAVIGATION_TIMEOUT_MS));
}

async function offlinePage(fallback) {
  return (await caches.match(OFFLINE_PAGE)) || fallback;
}
