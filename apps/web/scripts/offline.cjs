const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const root=path.resolve(__dirname,'../out');
function walk(dir) { return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]); }
const files=walk(root).filter(f=>!f.endsWith('sw.js'));
const digest=crypto.createHash('sha256');
for(const file of files)digest.update(fs.readFileSync(file));
const urls=['/',...files.map(f=>'/'+path.relative(root,f).split(path.sep).join('/'))];
fs.writeFileSync(path.join(root,'sw.js'),`
const NAME='sevil-static-${digest.digest('hex').slice(0,16)}';
const FILES=${JSON.stringify(urls)};
self.addEventListener('install',event=>event.waitUntil(caches.open(NAME).then(cache=>cache.addAll(FILES)).then(()=>self.skipWaiting())));
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(names=>Promise.all(names.filter(n=>n.startsWith('sevil-static-')&&n!==NAME).map(n=>caches.delete(n)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',event=>{
 const url=new URL(event.request.url);
 if(event.request.method!=='GET'||url.origin!==self.location.origin)return;
 if(event.request.mode==='navigate')event.respondWith(fetch(event.request).catch(()=>caches.match('/')));
 else if(FILES.includes(url.pathname))event.respondWith(caches.match(url.pathname).then(saved=>saved||fetch(event.request)));
});
`);
console.log('Offline app assets prepared. Provider traffic is never cached.');
