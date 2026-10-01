# Handoff: dgp web UI on flatpot (2026-09-24, dgp-WarmAlpaca)

- Done: `web-ui` branch at 85f2d04 (serve.py + phone visual pass). Gate green. Deploy #775 filed.
- Full result: /home/hila/.claude/claude-control/results/20260924-161541-dgp-WarmAlpaca.md
- Waiting on: Hila to press #775. After that, open https://flatpot.tail0b59ad.ts.net:8453/ and confirm it derives.
- Pushed: web-ui on hila-shemer/dgp = 85f2d04. Not done: landing on master; follow-ups (a) PIN-export import and (b) vault read, deferred to Hila.
- Gotcha: crypto.subtle needs a secure context, so plain http on a LAN IP loads the page but can't derive. Never use `tailscale serve reset`.

## Update: #775 applied 16:30:27Z (ledger seq 778). Hila asked directly for "import from the android app".
- Android Settings > Export writes `dgp-export.enc` = base64(iv12 ‖ AES-256-GCM(services JSON)), key = PBKDF2-SHA256(PIN, "dgp-export-v1", 600000, 32).
  It holds the services list only: no seed, no account. (MainActivity.exportServices, ConfigCrypto, linux/dgp/exportcrypto.py)
- Design posted to Hila (bounded path); waiting for her yes. Plan: the Import keyring button accepts .enc too, asks for the PIN inline,
  decrypts in the browser, then goes through the existing merge-on-id. Fixture from linux/dgp/exportcrypto.py. Redeploy = a new button.

## Update 2: import shipped on web-ui @ 66a8755 (pushed). Deploy #785 (swap index.html, no restart) is waiting for Hila.
- Hila asked whether Android dgp can auto-fill like password managers via "our custom keyboard" (= ~/proj/tabs/paste).
  Answered, no code: recommend an AutofillService in dgp; don't put passwords through the networked tabs keyboard. It's her call whether to build it.

## Update 3: #785 live. Import made findable: web-ui @ 6e0b826, deploy #882 pending. Hila must run the import in her own browser (localStorage + her PIN).

## DONE: Hila imported her phone export on the live page. #882 applied 20:07:34Z. Follow-ups (vault read, autofill design, ff web-ui to master) are listed in the result file; none in progress.
