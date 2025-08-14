self.addEventListener('install', (e)=>{
  e.waitUntil(caches.open('task-app-v1').then(c=>c.addAll([
    '/', '/index.html', '/styles.css', '/app.js', '/manifest.webmanifest'
  ])));
  self.skipWaiting();
});

self.addEventListener('fetch', (e)=>{
  const url = new URL(e.request.url);
  if (url.pathname.startsWith('/tasks')) return; // API 不快取
  e.respondWith(
    caches.match(e.request, {ignoreSearch:true}).then(r => r || fetch(e.request).then(res => {
      const clone = res.clone();
      caches.open('task-app-v1').then(c=>c.put(e.request, clone));
      return res;
    }))
  );
});

self.addEventListener('activate', (e)=>{
  e.waitUntil(self.clients.claim());
});


