// Controla un Chrome sin ventana por el protocolo DevTools.
// Uso: node cdp.mjs <url> <guion.js> [ancho alto] — el guion exporta default async (page) => {}
import { spawn } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const [url, scriptPath, w = '390', h = '844'] = process.argv.slice(2);
const port = 9300 + Math.floor(Math.random() * 400);
const profile = fs.mkdtempSync(path.join(process.env.CDP_TMP ?? os.tmpdir(), 'nidaria-chrome-'));
const chrome = spawn('/usr/bin/google-chrome', [
  '--headless=new', `--remote-debugging-port=${port}`, '--no-sandbox', '--enable-unsafe-swiftshader',
  '--use-angle=swiftshader', '--ignore-gpu-blocklist', `--user-data-dir=${profile}`, '--hide-scrollbars', 'about:blank',
], { stdio: 'ignore' });

async function connect() {
  for (let i = 0; i < 100; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
      const page = list.find((t) => t.type === 'page');
      if (page) return page.webSocketDebuggerUrl;
    } catch {}
    await new Promise((r) => setTimeout(r, 100));
  }
  throw new Error('Chrome no responde');
}

const ws = new WebSocket(await connect());
await new Promise((r) => ws.addEventListener('open', r));
let seq = 0;
const pending = new Map();
ws.addEventListener('message', (event) => {
  const msg = JSON.parse(event.data);
  if (msg.id && pending.has(msg.id)) {
    const { resolve, reject } = pending.get(msg.id);
    pending.delete(msg.id);
    if (msg.error) reject(new Error(msg.error.message)); else resolve(msg.result);
  } else if (msg.method === 'Runtime.consoleAPICalled' && process.env.CDP_LOG) {
    console.error('[page]', ...msg.params.args.map((a) => a.value ?? a.description));
  } else if (msg.method === 'Runtime.exceptionThrown') {
    console.error('[page error]', msg.params.exceptionDetails.exception?.description ?? msg.params.exceptionDetails.text);
  }
});
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++seq;
  pending.set(id, { resolve, reject });
  ws.send(JSON.stringify({ id, method, params }));
});

const page = {
  send,
  async eval(expression) {
    const r = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description ?? r.exceptionDetails.text);
    return r.result.value;
  },
  async shot(file) {
    const r = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(file, Buffer.from(r.data, 'base64'));
  },
  wait: (ms) => new Promise((r) => setTimeout(r, ms)),
  async goto(target) {
    await send('Page.navigate', { url: target });
    await page.wait(600);
  },
  async size(width, height, scale = 2) {
    await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: scale, mobile: true });
  },
  async tap(x, y) {
    for (const type of ['mousePressed', 'mouseReleased']) {
      await send('Input.dispatchMouseEvent', { type, x, y, button: 'left', clickCount: 1 });
    }
  },
};

try {
  await send('Runtime.enable');
  await send('Page.enable');
  await page.size(Number(w), Number(h));
  await page.goto(url);
  const script = (await import(pathToFileURL(scriptPath).href)).default;
  await script(page);
} catch (error) {
  console.error(error);
  process.exitCode = 1;
} finally {
  ws.close();
  const gone = new Promise((resolve) => chrome.once('exit', resolve));
  chrome.kill();
  await gone;
  try {
    fs.rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
  } catch {
    // El perfil es temporal: si Chrome aún lo tiene abierto, se queda.
  }
}
