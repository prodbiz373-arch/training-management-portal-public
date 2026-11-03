// Optional real headless-browser smoke test. Credentials arrive only over stdin; no OS UI automation.
const fs = require('fs');
const { spawn } = require('child_process');
const path = require('path');
let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', c => { input += c; });
process.stdin.on('end', async () => {
  let chrome, ws;
  let stage = 'arguments';
  const sleep = ms => new Promise(r => setTimeout(r, ms));
  try {
    const config = JSON.parse(input); input = '';
    if (!fs.existsSync(config.chrome) || !config.base.startsWith('http://localhost:')) throw Error();
    const profile = path.join(config.evidence, 'chrome-profile');
    chrome = spawn(config.chrome, ['--headless=new', '--disable-background-networking', '--no-first-run', '--no-default-browser-check', '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=0', '--user-data-dir=' + profile, 'about:blank'], { stdio: 'ignore' });
    stage = 'browser-ready';
    const portFile = path.join(profile, 'DevToolsActivePort');
    for (let i = 0; !fs.existsSync(portFile) && i < 100; i++) await sleep(100);
    const port = Number(fs.readFileSync(portFile, 'utf8').split('\n')[0]);
    const pages = await (await fetch('http://127.0.0.1:' + port + '/json/list')).json();
    ws = new WebSocket(pages.find(p => p.type === 'page').webSocketDebuggerUrl);
    await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
    const requests = new Map(); let id = 0;
    ws.onmessage = event => { const m = JSON.parse(event.data); if (m.id && requests.has(m.id)) { const r = requests.get(m.id); requests.delete(m.id); m.error ? r.reject(Error()) : r.resolve(m.result); } };
    const send = (method, params = {}) => new Promise((resolve, reject) => { const n = ++id; requests.set(n, { resolve, reject }); ws.send(JSON.stringify({ id: n, method, params })); setTimeout(() => { if (requests.has(n)) { requests.delete(n); reject(Error()); } }, 15000).unref(); });
    const evaluate = async expression => { const r = await send('Runtime.evaluate', { expression, returnByValue: true }); if (r.exceptionDetails) throw Error(); return r.result.value; };
    const wait = async expression => { for (let i = 0; i < 150; i++) { if (await evaluate(expression)) return; await sleep(100); } throw Error(); };
    const go = async route => { await send('Page.navigate', { url: config.base + route }); await wait('document.readyState === "complete"'); };
    await send('Page.enable'); await send('Runtime.enable');
    await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1100, deviceScaleFactor: 1, mobile: false });
    stage = 'login'; await go('/'); await wait('!!document.querySelector("#employeeId")');
    for (const [selector, value] of [['#employeeId', config.employee], ['#password', config.password]]) { await evaluate('document.querySelector(' + JSON.stringify(selector) + ').focus()'); await send('Input.insertText', { text: value }); }
    config.password = '';
    await evaluate('document.querySelector("form").requestSubmit()');
    await wait('location.pathname === "/home" && !!document.querySelector("h1")');
    const checks = [];
    for (const [route, title] of [['/home', 'おかえりなさい'], ['/progress', '進捗・課題管理'], ['/schedule', 'スケジュール一覧'], ['/members', 'メンバ登録']]) {
      stage = route; await go(route);
      await wait('!!document.querySelector("h1") && document.querySelector("h1").innerText.includes(' + JSON.stringify(title) + ')');
      if (route === '/members') await wait('document.body.innerText.includes("Setup Instructor")');
      checks.push(route);
    }
    process.stdout.write(JSON.stringify({ realBrowser: true, login: true, pages: checks }));
  } catch { process.stderr.write('STOP browser smoke stage=' + stage + '\n'); process.exitCode = 1; }
  finally { if (ws) ws.close(); if (chrome) { chrome.kill('SIGTERM'); await sleep(500); } }
});
