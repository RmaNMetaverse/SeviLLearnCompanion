// Serves static app files only. Libraries, media and provider keys never reach this server.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..', 'out');
const types = {'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json','.txt':'text/plain; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.ico':'image/x-icon','.woff2':'font/woff2'};
function serve(req,res) {
  let name;
  try { name=decodeURIComponent(new URL(req.url,'http://localhost').pathname); } catch { res.writeHead(400).end();return; }
  let target=path.resolve(root,'.'+name);
  if(target!==root && !target.startsWith(root+path.sep)) { res.writeHead(403).end();return; }
  if(fs.existsSync(target) && fs.statSync(target).isDirectory()) target=path.join(target,'index.html');
  if(!fs.existsSync(target) && fs.existsSync(target+'.html')) target+='.html';
  if(!fs.existsSync(target)||!fs.statSync(target).isFile()) { res.writeHead(404).end();return; }
  res.writeHead(200,{'Content-Type':types[path.extname(target)]||'application/octet-stream','Cache-Control':target.includes(path.sep+'_next'+path.sep)?'public,max-age=31536000,immutable':'no-cache','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer'});
  if(req.method==='HEAD') { res.end();return; }
  fs.createReadStream(target).pipe(res);
}
const server=http.createServer(serve);
server.listen(Number(process.env.PORT||3000),process.env.SEVIL_HOST||'127.0.0.1',()=>console.log(`SeviL: http://${process.env.SEVIL_HOST||'127.0.0.1'}:${process.env.PORT||3000}`));
