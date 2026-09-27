'use strict';
// DGP fill popup. Talks to the `dgp native-host` process; the seed never
// leaves it. The password is only held here long enough to inject it.
const HOST = 'io.github.hilashemer.dgp';

const listEl = document.getElementById('list');
const statusEl = document.getElementById('status');
const searchEl = document.getElementById('search');
let tab = null;
let tabOrigin = null;
let matches = [];
let all = null;

function ask(msg) {
  return new Promise((resolve) => {
    chrome.runtime.sendNativeMessage(HOST, msg, (reply) => {
      if (chrome.runtime.lastError || !reply) {
        resolve({ ok: false, error: (chrome.runtime.lastError || {}).message || 'no reply from dgp native host' });
      } else {
        resolve(reply);
      }
    });
  });
}

function setStatus(text) { statusEl.textContent = text || ''; }

function originOf(url) {
  try {
    const u = new URL(url);
    // Plain http only for this machine: anyone on the path could read the fill.
    const local = u.hostname === 'localhost' || u.hostname === '127.0.0.1' || u.hostname === '[::1]';
    return (u.protocol === 'https:' || (u.protocol === 'http:' && local)) ? u.origin : null;
  } catch (e) { return null; }
}

function render(services) {
  listEl.replaceChildren();
  for (const s of services) {
    const li = document.createElement('li');
    const b = document.createElement('button');
    b.textContent = s.name;
    const t = document.createElement('span');
    t.className = 'type';
    t.textContent = s.type;
    b.appendChild(t);
    b.addEventListener('click', () => fill(s.id));
    li.appendChild(b);
    listEl.appendChild(li);
  }
  if (!services.length) setStatus(searchEl.value ? 'No entries match.' : 'No match for this site; search all.');
  else setStatus('');
}

// Runs in the page (top frame only). Kept self-contained: it is serialized.
function fillInPage(password) {
  const isPw = (el) => el && el.tagName === 'INPUT' && el.type === 'password';
  let el = document.activeElement;
  if (!isPw(el)) el = document.querySelector('input[type=password]');
  if (!el) return false;
  el.focus();
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
  setter.call(el, password);
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.dispatchEvent(new Event('change', { bubbles: true }));
  return true;
}

async function fill(id) {
  setStatus('');
  const reply = await ask({ op: 'get', id });
  if (!reply.ok) { setStatus(reply.error); return; }
  // Re-read the tab: refuse if it navigated elsewhere while we asked.
  let now;
  try { now = await chrome.tabs.get(tab.id); } catch (e) { setStatus('Tab is gone.'); return; }
  if (originOf(now.url) !== tabOrigin) { setStatus('The page changed origin; not filling.'); return; }
  let res;
  try {
    res = await chrome.scripting.executeScript({
      target: { tabId: tab.id, frameIds: [0] },
      func: fillInPage,
      args: [reply.password],
    });
  } catch (e) { setStatus('Cannot fill this page.'); return; }
  if (res && res[0] && res[0].result) window.close();
  else setStatus('No password field on this page.');
}

async function onSearch() {
  const q = searchEl.value.trim().toLowerCase();
  if (!q) { render(matches); return; }
  if (all === null) {
    const reply = await ask({ op: 'list' });
    if (!reply.ok) { setStatus(reply.error); return; }
    all = reply.services;
  }
  render(all.filter((s) => s.name.toLowerCase().includes(q)));
}

async function main() {
  [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  tabOrigin = tab ? originOf(tab.url) : null;
  if (!tabOrigin) { setStatus('Not a web page.'); searchEl.disabled = true; return; }
  document.getElementById('origin').textContent = tabOrigin;
  searchEl.addEventListener('input', onSearch);
  const reply = await ask({ op: 'match', origin: tabOrigin });
  if (!reply.ok) { setStatus(reply.error); return; }
  matches = reply.services;
  render(matches);
  searchEl.focus();
}

main();
