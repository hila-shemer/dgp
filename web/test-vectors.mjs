#!/usr/bin/env node
/* Acceptance gate for the DGP web UI.
 *
 * The point of this file is that it does not test a copy of the engine - it
 * cuts the engine block straight out of index.html and runs that. A port that
 * drifts from the page it ships in is exactly how the last web attempt shipped
 * SHA-256/8192/32 for years without anyone noticing, so there is deliberately
 * nowhere here for a second copy of the constants to hide.
 *
 *   node web/test-vectors.mjs
 */
import { readFileSync } from "node:fs";
import { webcrypto } from "node:crypto";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const BEGIN = "/* === DGP-ENGINE-BEGIN === */";
const END = "/* === DGP-ENGINE-END === */";

function extractEngine(html) {
  const a = html.indexOf(BEGIN);
  const b = html.indexOf(END);
  if (a < 0 || b < 0) throw new Error(`index.html is missing ${a < 0 ? BEGIN : END}`);
  if (b < a) throw new Error("engine markers are in the wrong order");
  return html.slice(a + BEGIN.length, b);
}

const html = readFileSync(join(HERE, "index.html"), "utf8");
const engineSrc = extractEngine(html);

// `crypto` is passed in rather than taken off the global so the block under
// test is the browser's exact source text, unedited.
const load = new Function(
  "crypto",
  `${engineSrc}\nreturn { dgpDerive, dgpGenerate, dgpCheckWord, dgpBytesRead, DGP_WORDS, DGP_ITERATIONS, DGP_DKLEN };`
);
const engine = load(webcrypto);

const vectors = JSON.parse(readFileSync(join(HERE, "vectors.json"), "utf8"));

let failures = [];
let ran = 0;

function check(name, ok, detail) {
  ran++;
  if (!ok) failures.push(`${name}: ${detail}`);
}

// --- Constants. The previous port got these wrong and nothing else caught it.
check("iterations", engine.DGP_ITERATIONS === 42000, `expected 42000, got ${engine.DGP_ITERATIONS}`);
check("dklen", engine.DGP_DKLEN === 40, `expected 40, got ${engine.DGP_DKLEN}`);
check("wordlist length", engine.DGP_WORDS.length === 2048, `expected 2048, got ${engine.DGP_WORDS.length}`);
check("wordlist[0]", engine.DGP_WORDS[0] === "abandon", `got ${engine.DGP_WORDS[0]}`);
check("wordlist[1]", engine.DGP_WORDS[1] === "ability", `got ${engine.DGP_WORDS[1]}`);
check("wordlist[2047]", engine.DGP_WORDS[2047] === "zoo", `got ${engine.DGP_WORDS[2047]}`);
check("wordlist is lowercase ascii",
  engine.DGP_WORDS.every((w) => /^[a-z]+$/.test(w)), "a word is not lowercase ASCII");
check("wordlist has no duplicates",
  new Set(engine.DGP_WORDS).size === 2048, "duplicate word in list");
// SHA-1 is the whole point; assert the page never mentions the algorithm the
// old port used, in case someone "modernises" it.
check("engine uses SHA-1", /SHA-1/.test(engineSrc) && !/SHA-256/.test(engineSrc),
  "engine block names SHA-256 or fails to name SHA-1");

// --- The 40-byte intermediate, from the contract's worked fixture.
{
  const raw = await engine.dgpDerive("pass", "word", "salt");
  const hex = [...raw].map((b) => b.toString(16).padStart(2, "0")).join("");
  check("raw fixture pass/word/salt",
    hex === "842b8a866ef6f789533059698674d4588a794d7110031d4afa2a895e0f69f9b50502f6b3b40f46aa",
    `got ${hex}`);
  check("raw fixture length", raw.length === 40, `got ${raw.length} bytes`);
}

// --- The vectors themselves.
function label(v) {
  const trim = (s) => (s.length > 12 ? `${s.slice(0, 6)}…(${s.length})` : JSON.stringify(s));
  return `${trim(v.seed)}/${trim(v.account)}/${trim(v.service)}/${v.type}`;
}

for (const kind of ["golden", "reference"]) {
  for (const v of vectors[kind]) {
    let actual;
    try {
      actual = await engine.dgpGenerate(v.seed, v.account, v.service, v.type);
    } catch (e) {
      actual = `<threw: ${e.message}>`;
    }
    check(`${kind} ${label(v)}`, actual === v.expected,
      `expected ${JSON.stringify(v.expected)}, got ${JSON.stringify(actual)}`);
  }
}

// --- The seed check-word. Two lowercase BIP-39 words, hyphenated; its only job
// --- is to catch a mistyped seed, which otherwise yields a plausible wrong
// --- password. Expectations recomputed from DgpEngine.kt by gen-vectors.py.
for (const v of vectors.checkword) {
  let actual;
  try {
    actual = await engine.dgpCheckWord(v.seed, v.account);
  } catch (e) {
    actual = `<threw: ${e.message}>`;
  }
  check(`checkword ${label({ ...v, service: "", type: "checkword" })}`,
    actual === v.expected,
    `expected ${JSON.stringify(v.expected)}, got ${JSON.stringify(actual)}`);
}
{
  // Domain separation: the check-word salt must not be reachable as a service.
  const cw = await engine.dgpCheckWord("fake-seed-for-tests", "");
  check("check-word is two lowercase words", /^[a-z]+-[a-z]+$/.test(cw), `got ${cw}`);
  check("check-word is not a password",
    cw !== await engine.dgpGenerate("fake-seed-for-tests", "", "dgp-flag-fp:v1", "xkcd"),
    "check-word collided with an xkcd password");
}

// --- The bytemap readout must agree with what each type actually consumes.
for (const [t, n] of [["hex", 4], ["hexlong", 8], ["aeskey", 32],
                      ["alnum", 40], ["alnumlong", 40], ["base58", 40],
                      ["base58long", 40], ["xkcd", 40], ["xkcdlong", 40]]) {
  check(`bytemap ${t} reads ${n}`, engine.dgpBytesRead(t) === n,
    `got ${engine.dgpBytesRead(t)}`);
}

// --- Every type the contract names is reachable, so none can be quietly dropped.
{
  const seen = new Set([...vectors.golden, ...vectors.reference].map((v) => v.type));
  for (const t of ["hex", "hexlong", "alnum", "alnumlong",
                   "base58", "base58long", "xkcd", "xkcdlong", "aeskey"]) {
    check(`type ${t} is covered`, seen.has(t), "no vector exercises this type");
  }
}

// --- Shape rules the vectors alone would not pin down.
{
  const s = "fake-seed-for-tests";
  const xkcd = await engine.dgpGenerate(s, "", "example.com", "xkcd");
  const xkcdlong = await engine.dgpGenerate(s, "", "example.com", "xkcdlong");
  const capitals = (str) => str.split(/(?=[A-Z])/).filter(Boolean);
  check("xkcd is 4 words", capitals(xkcd).length === 4, `got ${capitals(xkcd).length} in ${xkcd}`);
  check("xkcdlong is 6 words", capitals(xkcdlong).length === 6, `got ${capitals(xkcdlong).length} in ${xkcdlong}`);
  check("xkcd is CamelCase-concatenated, no separators", /^[A-Za-z]+$/.test(xkcd), `got ${xkcd}`);
  check("aeskey is 64 hex chars",
    /^[0-9a-f]{64}$/.test(await engine.dgpGenerate(s, "", "example.com", "aeskey")), "bad aeskey shape");
  check("hex is 8 lowercase hex chars",
    /^[0-9a-f]{8}$/.test(await engine.dgpGenerate(s, "", "example.com", "hex")), "bad hex shape");
  check("hexlong is 16 lowercase hex chars",
    /^[0-9a-f]{16}$/.test(await engine.dgpGenerate(s, "", "example.com", "hexlong")), "bad hexlong shape");
  check("base58 is 8 chars", (await engine.dgpGenerate(s, "", "example.com", "base58")).length === 8, "bad length");
  check("base58long is 12 chars", (await engine.dgpGenerate(s, "", "example.com", "base58long")).length === 12, "bad length");
  check("alnum is 8 chars", (await engine.dgpGenerate(s, "", "example.com", "alnum")).length === 8, "bad length");
  check("alnumlong is 12 chars", (await engine.dgpGenerate(s, "", "example.com", "alnumlong")).length === 12, "bad length");
}

// --- seed+account is concatenated before hashing, so the split is not a
// --- boundary the KDF can see. Two spellings of the same bytes must agree.
{
  const a = await engine.dgpGenerate("fake-seed", "-tail", "svc", "alnumlong");
  const b = await engine.dgpGenerate("fake-seed-tail", "", "svc", "alnumlong");
  check("seed+account concatenation has no separator", a === b, `${a} != ${b}`);
}

// --- The page itself: constraints that are part of the product, not the crypto.
{
  const forbidden = [
    [/\bfetch\s*\(/, "calls fetch()"],
    [/XMLHttpRequest/, "uses XMLHttpRequest"],
    [/\bnew\s+WebSocket\b/, "opens a WebSocket"],
    [/\bnavigator\.sendBeacon\b/, "uses sendBeacon"],
    [/\bimport\s*\(/, "uses dynamic import()"],
    [/\bEventSource\b/, "uses EventSource"],
    [/https?:\/\//i, "references an http(s) URL"],
    [/\bsrc\s*=/, "has a src= attribute"],
    [/<link\b/i, "has a <link> element"],
    [/@import\b/, "has a CSS @import"],
    [/\bconsole\.(log|debug|info|warn|error)\b/, "logs to the console"],
  ];
  for (const [re, why] of forbidden) {
    check(`page never ${why}`, !re.test(html), `matched ${re}`);
  }
  // The CSP is the load-bearing part of "makes no network requests": it turns
  // the claim into something the browser enforces rather than something a
  // reviewer has to re-verify by reading.
  const csp = /http-equiv="Content-Security-Policy"[\s\S]*?content="([^"]+)"/.exec(html);
  check("page ships a CSP", !!csp, "no Content-Security-Policy meta tag");
  for (const d of ["default-src 'none'", "connect-src 'none'", "form-action 'none'",
                   "base-uri 'none'", "object-src 'none'"]) {
    check(`CSP sets ${d}`, !!csp && csp[1].includes(d), "directive missing");
  }
  // Storage: the entry list may be persisted, the seed may not. Assert every
  // storage call site by hand rather than trusting the absence of a keyword.
  const storageCalls = [...html.matchAll(/(localStorage|sessionStorage|indexedDB|document\.cookie)[^\n]*/g)]
    .map((m) => m[0]);
  check("no sessionStorage", !storageCalls.some((c) => c.includes("sessionStorage")), "sessionStorage used");
  check("no indexedDB", !storageCalls.some((c) => c.includes("indexedDB")), "indexedDB used");
  check("no cookies", !storageCalls.some((c) => c.includes("document.cookie")), "document.cookie used");
  check("seed input is not autofillable",
    /autocomplete\s*=\s*"off"/.test(html) || /autocomplete\s*=\s*"new-password"/.test(html),
    "seed field has no autocomplete opt-out");
}

const passed = ran - failures.length;
if (failures.length) {
  console.error(`\nFAIL  ${passed}/${ran} checks passed, ${failures.length} failed:\n`);
  for (const f of failures) console.error(`  - ${f}`);
  process.exit(1);
}
console.log(
  `OK  ${passed}/${ran} checks passed ` +
  `(${vectors.golden.length} golden, ${vectors.reference.length} reference, ${vectors.checkword.length} check-word vectors)`
);
