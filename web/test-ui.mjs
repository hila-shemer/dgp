#!/usr/bin/env node
/* Behaviour test for the page around the engine.
 *
 * web/test-vectors.mjs proves the derivation is right. This proves the thing
 * wrapped around it does not leak: that the seed reaches localStorage never,
 * that a derived password reaches it never, and that the keyring it does store
 * round-trips through export and import without losing fields this page does
 * not itself manage.
 *
 * There is no browser on this machine, so the page runs against a DOM shim
 * small enough to read in one sitting. It is not a browser and does not pretend
 * to be one - it covers wiring, storage and data flow, not layout or CSS.
 *
 *   node web/test-ui.mjs
 */
import { readFileSync } from "node:fs";
import { webcrypto } from "node:crypto";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const html = readFileSync(join(HERE, "index.html"), "utf8");

/* ----------------------------------------------------------------- the shim */
class El {
  constructor(tag) {
    this.tagName = String(tag).toUpperCase();
    this.children = [];
    this.parentNode = null;
    this.attrs = Object.create(null);
    this.dataset = Object.create(null);
    this.style = { cssText: "" };
    this.listeners = Object.create(null);
    this.classes = new Set();
    this.value = "";
    this.checked = false;
    this.hidden = false;
    this.disabled = false;
    this.type = "";
    this.text = "";
    this.focused = false;
  }
  get classList() {
    const s = this.classes;
    return {
      add: (...c) => c.forEach((x) => s.add(x)),
      remove: (...c) => c.forEach((x) => s.delete(x)),
      contains: (c) => s.has(c),
      toggle: (c, on) => (on === undefined ? (s.has(c) ? s.delete(c) : s.add(c))
                                           : (on ? s.add(c) : s.delete(c))),
    };
  }
  set className(v) { this.classes = new Set(String(v).split(/\s+/).filter(Boolean)); }
  get className() { return [...this.classes].join(" "); }
  set textContent(v) { this.text = String(v); this.children = []; }
  get textContent() {
    return this.children.length ? this.children.map((c) => c.textContent).join("") : this.text;
  }
  appendChild(c) { c.parentNode = this; this.children.push(c); return c; }
  remove() {
    if (this.parentNode) {
      this.parentNode.children = this.parentNode.children.filter((x) => x !== this);
      this.parentNode = null;
    }
  }
  setAttribute(k, v) { this.attrs[k] = String(v); if (k === "class") this.className = v; }
  getAttribute(k) { return k in this.attrs ? this.attrs[k] : null; }
  addEventListener(t, fn) { (this.listeners[t] ||= []).push(fn); }
  fire(type, ev = {}) {
    const event = { type, target: this, preventDefault() {}, ...ev };
    let node = this;
    while (node) {
      for (const fn of node.listeners[type] || []) fn(event);
      node = node.parentNode;
    }
    for (const fn of (doc.listeners[type] || [])) fn(event);
  }
  click() { this.fire("click"); }
  focus() { this.focused = true; }
  select() {}
  closest(sel) {
    const cls = sel.replace(/^\./, "");
    let n = this;
    while (n) { if (n.classes.has(cls)) return n; n = n.parentNode; }
    return null;
  }
}

const byId = new Map();
/* Build a stub for every id the page declares, carrying the attributes the
 * script actually reads back (type, value). Anything the page asks for that the
 * markup does not declare shows up immediately as a null dereference. */
for (const m of html.matchAll(/<([a-z0-9]+)\b([^>]*?)\bid="([^"]+)"([^>]*)>/gi)) {
  const [, tag, pre, id, post] = m;
  const el = new El(tag);
  const attrs = pre + post;
  const t = /\btype="([^"]+)"/.exec(attrs);
  if (t) el.type = t[1];
  const cls = /\bclass="([^"]+)"/.exec(attrs);
  if (cls) el.className = cls[1];
  if (/\bdisabled\b/.test(attrs)) el.disabled = true;
  if (/\bhidden\b/.test(attrs)) el.hidden = true;
  el.setAttribute("id", id);
  byId.set(id, el);
}

const store = new Map();
const localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => void store.set(k, String(v)),
  removeItem: (k) => void store.delete(k),
};

const doc = {
  listeners: Object.create(null),
  documentElement: new El("html"),
  body: new El("body"),
  getElementById: (id) => byId.get(id) || null,
  createElement: (t) => new El(t),
  addEventListener(t, fn) { (this.listeners[t] ||= []).push(fn); },
  execCommand: () => true,
};

let downloaded = null;
const blobs = new Map();
const urlShim = {
  createObjectURL(blob) { const u = `blob:${blobs.size}`; blobs.set(u, blob); return u; },
  revokeObjectURL(u) { blobs.delete(u); },
};

let clipboard = null;
const navigatorShim = { clipboard: { writeText: async (t) => { clipboard = t; } } };

const win = {
  localStorage,
  listeners: Object.create(null),
  addEventListener(t, fn) { (this.listeners[t] ||= []).push(fn); },
  confirm: () => true,
};

/* The <a download> path in exportKeyring: capture instead of navigating. */
const realCreate = doc.createElement;
doc.createElement = (t) => {
  const el = realCreate(t);
  if (String(t).toLowerCase() === "a") {
    el.click = () => { downloaded = { name: el.download, blob: blobs.get(el.href) }; };
  }
  return el;
};

/* ------------------------------------------------------------------- run it */
const scriptSrc = (() => {
  const i = html.indexOf('<script>\n"use strict";');
  if (i < 0) throw new Error("could not find the page script");
  const j = html.indexOf("</scr" + "ipt>", i);
  return html.slice(i + "<script>".length, j);
})();

const run = new Function(
  "window", "document", "crypto", "navigator", "URL", "Blob",
  `${scriptSrc}\nreturn { dgpGenerate, dgpCheckWord, dgpDecryptExport, serializeEntry, parseKeyring, entries: () => entries };`
);

let failures = [];
let ran = 0;
function check(name, ok, detail) {
  ran++;
  if (!ok) failures.push(`${name}: ${detail}`);
}
function eq(name, actual, expected) {
  check(name, Object.is(actual, expected) || JSON.stringify(actual) === JSON.stringify(expected),
    `expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
}
/* PBKDF2 at 42000 iterations is a real wait, and two of them can be in flight
 * at once (a derive and a check-word). Poll rather than guess a tick count. */
const settle = () => new Promise((r) => setTimeout(r, 0));
async function waitFor(what, cond, ms = 5000) {
  const until = Date.now() + ms;
  while (Date.now() < until) {
    if (cond()) return true;
    await settle();
  }
  check(`waiting for ${what}`, false, `timed out after ${ms}ms`);
  return false;
}
const idle = () => g("copy-btn").disabled === false;

let app;
try {
  app = run(win, doc, webcrypto, navigatorShim, urlShim, Blob);
} catch (e) {
  console.error(`FAIL  the page script threw on load: ${e.message}`);
  process.exit(1);
}
check("page script loads against a bare DOM", true, "");

const g = (id) => byId.get(id);
const SEED = "fake-seed-not-a-real-one";

// --- Cold start.
eq("keyring starts empty", app.entries().length, 0);
check("empty keyring invites an entry",
  /keyring is empty/i.test(g("keyring").textContent), g("keyring").textContent);
check("empty keyring points at the phone import",
  /Import from phone/.test(g("keyring").textContent), g("keyring").textContent);
{
  // Import used to live only at the foot of the page, under the colophon, which
  // is where nobody looks for it.
  let opened = 0;
  g("import-file").addEventListener("click", () => { opened++; });
  g("phone-import-btn").click();
  eq("the keyring panel's import button opens the file picker", opened, 1);
}
check("lock is disabled with no seed", g("lock-btn").disabled === true, "enabled");
check("copy is disabled with no result", g("copy-btn").disabled === true, "enabled");
check("bytemap draws 40 cells", g("bytemap").children.length === 40,
  `${g("bytemap").children.length} cells`);

// --- Seed entry lights up the check-word and the lock.
g("seed").value = SEED;
g("seed").fire("input");
{
  const expected = await app.dgpCheckWord(SEED, "");
  await waitFor("the check-word", () => g("checkword").textContent === expected);
  eq("check-word matches the engine", g("checkword").textContent, expected);
  check("lock enables once a seed is present", g("lock-btn").disabled === false, "still disabled");
}

// --- Derive.
g("service").value = "example.com";
g("service").fire("input");
g("type").value = "alnum";
g("type").fire("change");
g("derive-btn").click();
{
  const expected = await app.dgpGenerate(SEED, "", "example.com", "alnum");
  await waitFor("the first derive", idle);
  eq("slab shows the derived password", g("slab").textContent, expected);
  check("copy enables after a derive", g("copy-btn").disabled === false, "still disabled");
  check("slab is no longer in its idle style", !g("slab").classes.has("is-idle"), "still idle");
}

// --- Copy.
g("copy-btn").click();
await waitFor("the clipboard write", () => clipboard !== null);
eq("copy puts the password on the clipboard", clipboard,
   await app.dgpGenerate(SEED, "", "example.com", "alnum"));

// --- Any input change invalidates a stale result.
g("type").value = "xkcdlong";
g("type").fire("change");
check("changing type clears the result", g("copy-btn").disabled === true, "result survived");
check("changing type relights the bytemap",
  [...g("bytemap").children].filter((c) => c.classes.has("is-lit")).length === 40, "wrong lit count");
g("type").value = "hex";
g("type").fire("change");
check("hex lights only the first four bytes",
  [...g("bytemap").children].filter((c) => c.classes.has("is-lit")).length === 4, "wrong lit count");
g("type").value = "alnum";
g("type").fire("change");

// --- Save an entry.
g("comment").value = "test entry";
g("tags").value = "one, two";
g("pinned").checked = true;
g("save-btn").click();
eq("entry is added", app.entries().length, 1);
{
  const stored = JSON.parse(localStorage.getItem("dgp.keyring.v1"));
  eq("stored shape is a bare array of one", stored.length, 1);
  eq("stored key order matches the config contract",
    Object.keys(stored[0]),
    ["id", "name", "type", "comment", "archived", "pinned", "tags"]);
  eq("stored name", stored[0].name, "example.com");
  eq("stored tags", stored[0].tags, ["one", "two"]);
  check("encryptedSecret is omitted when absent", !("encryptedSecret" in stored[0]), "present");
  check("account is omitted when absent", !("account" in stored[0]), "present");
  check("id looks like a uuid", /^[0-9a-f-]{36}$/.test(stored[0].id), stored[0].id);
}
check("keyring lists the entry",
  g("keyring").textContent.includes("example.com"), g("keyring").textContent);

// --- Second entry, with a per-entry account override.
g("new-btn").click();
g("service").value = "bank.example";
g("service").fire("input");
g("entry-account").value = "joint";
g("entry-account").fire("input");
g("type").value = "xkcd";
g("type").fire("change");
g("save-btn").click();
eq("second entry is added", app.entries().length, 2);
{
  const stored = JSON.parse(localStorage.getItem("dgp.keyring.v1"));
  const bank = stored.find((e) => e.name === "bank.example");
  eq("per-entry account is stored", bank.account, "joint");
  eq("account is the last key so the contract prefix is untouched",
    Object.keys(bank).slice(0, 6), ["id", "name", "type", "comment", "archived", "pinned"]);
}

// --- The override wins over the identity account when deriving.
g("account").value = "personal";
g("account").fire("input");
g("derive-btn").click();
await waitFor("the override derive", idle);
eq("per-entry account overrides the identity account",
   g("slab").textContent, await app.dgpGenerate(SEED, "joint", "bank.example", "xkcd"));

// --- Selecting an entry fills the form.
{
  const target = [...g("keyring").children]
    .map((li) => li.children[0])
    .find((btn) => btn && btn.textContent.startsWith("example.com"));
  check("the saved entry is clickable", !!target, "no entry button found");
  target.fire("click", { target });
  eq("selecting fills the service", g("service").value, "example.com");
  eq("selecting fills the type", g("type").value, "alnum");
  eq("selecting fills the comment", g("comment").value, "test entry");
  eq("selecting fills the tags", g("tags").value, "one, two");
  eq("selecting fills the pin", g("pinned").checked, true);
  eq("selecting clears the override", g("entry-account").value, "");
  check("selecting clears any showing password", g("copy-btn").disabled === true, "result survived");

  /* Stacked layout puts the derive panel under the whole keyring; a tap must
   * bring it into view there, and must leave the page alone side by side. */
  let scrolls = 0;
  g("derive-panel").scrollIntoView = () => { scrolls++; };
  for (const [narrow, want] of [[true, 1], [false, 0]]) {
    scrolls = 0;
    win.matchMedia = (q) => ({ matches: q.includes("max-width") ? narrow : false });
    target.fire("click", { target });
    eq(`selecting ${narrow ? "stacked" : "side by side"} scrolls to the result ${want}x`,
       scrolls, want);
  }
  delete win.matchMedia;
}

// --- Filter.
g("filter").value = "bank";
g("filter").fire("input");
const listed = () => [...g("keyring").children].map((li) => li.children[0])
  .filter(Boolean).map((b) => b.children[0].textContent);
eq("filter narrows the list to the match", listed(), ["bank.example"]);
eq("filter shows a count", g("keyring-count").textContent, "1/2");
g("filter").value = "";
g("filter").fire("input");
eq("clearing the filter restores both", listed().sort(), ["bank.example", "example.com"]);

// --- Export.
g("export-btn").click();
check("export produced a file", !!downloaded && !!downloaded.blob, "nothing downloaded");
check("export is named for the day",
  /^dgp-keyring-\d{4}-\d{2}-\d{2}\.json$/.test(downloaded.name), downloaded.name);
const exported = await downloaded.blob.text();
{
  const arr = JSON.parse(exported);
  check("export is a bare array, as the other apps expect", Array.isArray(arr), typeof arr);
  eq("export carries both entries", arr.length, 2);
}

// --- Import: fields this page does not manage must survive untouched.
{
  const foreign = JSON.stringify([{
    id: "11111111-2222-3333-4444-555555555555",
    name: "vaulted.example", type: "alnumlong", comment: "from the phone",
    archived: true, pinned: false, tags: ["work"],
    encryptedSecret: "ZmFrZS1ub3QtcmVhbA==",
  }]);
  byId.get("import-file").files = [{ text: async () => foreign }];
  byId.get("import-file").fire("change");
  await waitFor("the import", () => app.entries().length === 3);
  eq("import appended the foreign entry", app.entries().length, 3);
  const stored = JSON.parse(localStorage.getItem("dgp.keyring.v1"));
  const v = stored.find((e) => e.name === "vaulted.example");
  eq("encryptedSecret survives a round-trip", v.encryptedSecret, "ZmFrZS1ub3QtcmVhbA==");
  eq("archived survives a round-trip", v.archived, true);
  eq("tags survive a round-trip", v.tags, ["work"]);
  check("import reports what it did", /Imported 1 new/.test(g("status").textContent),
    g("status").textContent);
}

// --- Import: a type this page cannot derive becomes the documented default.
{
  byId.get("import-file").files = [{ text: async () => JSON.stringify([{
    id: "99999999-8888-7777-6666-555555555555",
    name: "odd.example", type: "aeskey", comment: "", archived: false, pinned: false,
  }]) }];
  byId.get("import-file").fire("change");
  await waitFor("the odd-type import", () => app.entries().length === 4);
  const stored = JSON.parse(localStorage.getItem("dgp.keyring.v1"));
  eq("an underivable type falls back to alnum",
     stored.find((e) => e.name === "odd.example").type, "alnum");
  entries: {
    const target = [...g("keyring").children].map((li) => li.children[0])
      .find((b) => b && b.textContent.startsWith("odd.example"));
    target.fire("click", { target });
    eq("and the form agrees with the file", g("type").value, "alnum");
  }
}

// --- Import: a re-import of our own export must not duplicate anything.
{
  const before = app.entries().length;
  byId.get("import-file").files = [{ text: async () => exported }];
  byId.get("import-file").fire("change");
  await waitFor("the re-import", () => /Imported 0 new/.test(g("status").textContent));
  eq("re-importing merges on id rather than duplicating", app.entries().length, before);
}

// --- Import: garbage is refused without disturbing the keyring.
{
  const before = JSON.stringify(app.entries());
  byId.get("import-file").files = [{ text: async () => "not json at all" }];
  byId.get("import-file").fire("change");
  await waitFor("the rejection", () => /not a DGP keyring/.test(g("status").textContent));
  eq("a bad file changes nothing", JSON.stringify(app.entries()), before);
  check("a bad file says so", /not a DGP keyring/.test(g("status").textContent),
    g("status").textContent);
}

// --- Import: the phone's PIN-encrypted export (Settings > Export, dgp-export.enc).
/* Made by linux/dgp/exportcrypto.encrypt_export, the Python side of the format
 * Android's ConfigCrypto is proven wire-compatible with by
 * linux/tests/test_android_compat.py. Synthetic: PIN 2468, no real services. */
const PHONE_PIN = "2468";
const PHONE_BLOB = "Yhk9xxjhfJCzu/pAGXJe+lbTIR7BpmrlBBnMdZXHpXXbY+993QvjhxzLwvXY19qLeUZukeQY1Kf7YZ4XqGEUNwn0pPs82NCDJXtPilBJi9UQJecVW2p8q9oT/AX2bXfgOm6d6VSduuhrAhQbL7HStdVtX1DnoEXCdv/9SLYAO0/xN92iXpbHaKq9MvaIrytcaGt/e/juKzLy+s5bEtEAQVxLDxlkvyfEFelQtfsXuhz4BFxitkfiWURdusmewFTloum1EZM1thBUhiR3KYib2HObbcAG5dGeaNekPWW2qKxSo/mKX3jC59HL69gFXuWY78wEXv2mdrvZs8w3/bHOTMnEkwUa1yJ7dhIlE7knxX8GpRzzpDcUywvaxeQohljZKyuFNteWAc/WFa2qO7q6CJiW8IhKA79IwwvYbQq1pu/t2BxAobVqEqrPLMjzhu31FvcYjosfanEkvv2ERn75QqGwkzvCEFshk3AV8l9VrZWY22cdAVGx978VaemGGHWXXqWS7sGOjvW7pNDYuIWzIhYhyOm/bSxbG+Nq06bCHQmRyQDIRx05SbXjRCUmd1gpqHzCc4vj5dLxiXJjIwoRfE+ffuHwdcQ2pGQqcW89/S8E2oRIlq8KgzzhjIKziPR3jf4+9ZbL9jo=";
const PHONE_SERVICES = [
  { id: "a1", name: "github.com", type: "alnumlong", comment: "work", archived: false, pinned: true, tags: ["dev"] },
  { id: "b2", name: "bank.example", type: "xkcdlong", comment: "", archived: false, pinned: false },
  { id: "c3", name: "old-router", type: "vault", comment: "legacy", archived: true, pinned: false, encryptedSecret: "AAECAwQFBgcICQoLDA0ODxAREhM=" },
  { id: "d4", name: "café.example", type: "hex", comment: "ünicöde", archived: false, pinned: false },
];
{
  eq("the phone's export decrypts with its PIN",
     JSON.parse(await app.dgpDecryptExport(PHONE_BLOB, PHONE_PIN)), PHONE_SERVICES);
  eq("a wrong PIN decrypts nothing", await app.dgpDecryptExport(PHONE_BLOB, "2469"), null);
  eq("a newline-wrapped export still decrypts",
     JSON.parse(await app.dgpDecryptExport(PHONE_BLOB.replace(/(.{76})/g, "$1\n") + "\n", PHONE_PIN)),
     PHONE_SERVICES);
  eq("a truncated export decrypts nothing",
     await app.dgpDecryptExport(PHONE_BLOB.slice(0, 40), PHONE_PIN), null);
  check("the file picker offers .enc files", /id="import-file"[^>]*accept="[^"]*\.enc/.test(html),
    "accept= would grey out dgp-export.enc");

  eq("the PIN row starts hidden", g("pin-row").hidden, true);
  const before = JSON.stringify(app.entries());
  const pick = () => {
    byId.get("import-file").files = [{ text: async () => PHONE_BLOB }];
    byId.get("import-file").fire("change");
  };

  pick();
  await waitFor("the PIN request", () => g("pin-row").hidden === false);
  check("an encrypted file asks for its PIN", /PIN/.test(g("status").textContent),
    g("status").textContent);
  g("pin-cancel").click();
  eq("cancel hides the PIN row", g("pin-row").hidden, true);
  eq("cancel changes nothing", JSON.stringify(app.entries()), before);

  pick();
  await waitFor("the PIN request again", () => g("pin-row").hidden === false);
  g("import-pin").value = "2469";
  g("pin-ok").click();
  await waitFor("the wrong-PIN refusal", () => /Wrong PIN/.test(g("status").textContent));
  eq("a wrong PIN changes nothing", JSON.stringify(app.entries()), before);
  eq("a wrong PIN clears the field", g("import-pin").value, "");
  eq("a wrong PIN leaves the row up for another try", g("pin-row").hidden, false);
  check("a wrong PIN is said beside the PIN field", /Wrong PIN/.test(g("pin-note").textContent),
    g("pin-note").textContent);

  g("import-pin").value = PHONE_PIN;
  g("import-pin").fire("keydown", { key: "Enter" });
  await waitFor("the encrypted import", () => /Imported 4 new/.test(g("status").textContent));
  eq("the right PIN hides the row", g("pin-row").hidden, true);
  eq("the right PIN clears the field", g("import-pin").value, "");
  const stored = JSON.parse(localStorage.getItem("dgp.keyring.v1"));
  const vault = stored.find((e) => e.id === "c3");
  eq("a vault entry keeps its type", vault.type, "vault");
  eq("a vault entry keeps its secret", vault.encryptedSecret, "AAECAwQFBgcICQoLDA0ODxAREhM=");
  eq("unicode names survive", stored.find((e) => e.id === "d4").name, "café.example");
  check("the PIN never reaches storage",
    ![...store.values()].some((v) => v.includes(PHONE_PIN)), "found the PIN in localStorage");

  // A vault entry is a stored secret, not a derivation: deriving one would show a
  // plausible password that is not the secret.
  const target = [...g("keyring").children].map((li) => li.children[0])
    .find((b) => b && b.textContent.startsWith("old-router"));
  target.fire("click", { target });
  eq("the form shows the vault type", g("type").value, "vault");
  g("derive-btn").click();
  await settle();
  check("deriving a vault entry is refused", /vault/i.test(g("status").textContent),
    g("status").textContent);
  eq("deriving a vault entry shows nothing", g("copy-btn").disabled, true);

  // Its secret is keyed on name + account; only the phone can re-encrypt it.
  g("service").value = "new-router";
  g("service").fire("input");
  g("save-btn").click();
  check("renaming a vault entry is refused", /phone/i.test(g("status").textContent),
    g("status").textContent);
  eq("the vault entry keeps its name",
     app.entries().find((e) => e.id === "c3").name, "old-router");
  g("service").value = "old-router";
  g("service").fire("input");
  g("comment").value = "still legacy";
  g("save-btn").click();
  const saved = app.entries().find((e) => e.id === "c3");
  eq("a vault entry's comment can still be edited", saved.comment, "still legacy");
  eq("and it is still a vault entry", saved.type, "vault");
  eq("and its secret is untouched", saved.encryptedSecret, "AAECAwQFBgcICQoLDA0ODxAREhM=");

  g("new-btn").click();
  eq("a new entry does not inherit the vault type", g("type").value, "alnum");
}

// --- Deriving with a field missing says which one, and derives nothing.
g("seed").value = "";
g("seed").fire("input");
g("service").value = "";
g("service").fire("input");
g("derive-btn").click();
check("no seed is refused by name", /Enter your seed/.test(g("status").textContent),
  g("status").textContent);
check("no seed derives nothing", g("copy-btn").disabled === true, "a result appeared");
g("seed").value = SEED;
g("seed").fire("input");
g("derive-btn").click();
check("no service is refused by name", /Enter a service name/.test(g("status").textContent),
  g("status").textContent);
check("no service derives nothing", g("copy-btn").disabled === true, "a result appeared");

// --- Lock.
g("service").value = "example.com";
g("service").fire("input");
g("derive-btn").click();
await waitFor("the pre-lock derive", idle);
g("lock-btn").click();
await waitFor("the lock to clear the check-word", () => g("checkword").textContent === "—");
eq("lock empties the seed field", g("seed").value, "");
eq("lock re-masks the seed field", g("seed").type, "password");
check("lock clears the result", g("copy-btn").disabled === true, "result survived");
eq("lock clears the check-word", g("checkword").textContent, "—");
check("lock disables itself", g("lock-btn").disabled === true, "still enabled");

// --- A reload must not bring the seed back.
g("seed").value = SEED;
for (const fn of win.listeners.pageshow || []) fn({ type: "pageshow" });
eq("pageshow blanks a restored seed", g("seed").value, "");

// --- The whole point: nothing secret is ever written down.
{
  const all = [...store.entries()].map(([k, v]) => `${k}=${v}`).join("\n");
  check("no storage key holds the seed", !all.includes(SEED), "the seed is in localStorage");
  const derived = await app.dgpGenerate(SEED, "", "example.com", "alnum");
  check("no storage key holds a derived password", !all.includes(derived),
    "a derived password is in localStorage");
  eq("only the keyring and the theme are persisted",
    [...store.keys()].sort(), ["dgp.keyring.v1", "dgp.theme"]);
}

// --- Theme cycles and is remembered.
{
  eq("theme starts on auto", doc.documentElement.getAttribute("data-theme"), "auto");
  g("theme-btn").click();
  eq("theme cycles to light", doc.documentElement.getAttribute("data-theme"), "light");
  g("theme-btn").click();
  eq("theme cycles to dark", doc.documentElement.getAttribute("data-theme"), "dark");
  eq("theme is remembered", localStorage.getItem("dgp.theme"), "dark");
  g("theme-btn").click();
  eq("theme cycles back to auto", doc.documentElement.getAttribute("data-theme"), "auto");
}

const passed = ran - failures.length;
if (failures.length) {
  console.error(`\nFAIL  ${passed}/${ran} checks passed, ${failures.length} failed:\n`);
  for (const f of failures) console.error(`  - ${f}`);
  process.exit(1);
}
console.log(`OK  ${passed}/${ran} UI checks passed`);
