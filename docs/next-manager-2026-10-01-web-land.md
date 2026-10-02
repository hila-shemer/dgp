# Handoff: land the web page on main (2026-10-01, dgp-HonestHornet)

## What this branch is
`web-land` = the 8 web-sites commits rebased onto hila-fork/main 2ad1176, plus one docs commit.
- web-ui is a prefix of web-sites, so one rebase covers both.
- `a6b74f1 web app spec doc` was dropped by the rebase: main already has it as dbe4c6a, with the same tree.
- The rebase was clean, and no merges were made.
- `web/index.html` at the tip is byte-identical to the live page in `~/.local/share/dgp-web/index.html` (#1983). Nothing live changes.
- The diff against main touches only `web/`, `run_tests.sh`, `CLAUDE.md` and `docs/`. `app/` and `linux/` are unchanged.

## Tests at the tip
- `web/run-tests.sh` is green: 218/218 vector checks (74 golden) and 106/106 UI checks, plus 7 serve.py tests.
- `linux/` pytest on the system python: 192 passed, 2 skipped and 4 failed, with gui/btc/ssh skipped.
  - The 4 failures are ssh/btc CLI tests that need `coincurve` and `nacl`, which flatpot's python lacks. linux/ is unchanged from main.
  - `pip install -e linux[test]` fails on this box because cffi will not build ("Expected exactly one LICENSE file").
- Gradle was not run, because app/ is unchanged from main.

## Landing
- The land button runs `git push hila-fork web-land:main`. Its check is that hila-fork/main is an ancestor of web-land, so the push fast-forwards.
- Also in a62d564:
  - docs/next-manager-2026-09-27-dgp-chrome-autofill.md, which was untracked in the master checkout;
  - docs/next-manager-2026-09-24-web-ui-flatpot.md, which was untracked in the web-ui worktree and would have been lost with it.

## After it lands
- Local `main` was reset to hila-fork/main 2ad1176 on 2026-10-01. The old 3f8ca91 stays reachable from imported/fedora/main and hila-fork/home/main.
- #3933 applied 2026-10-02: hila-fork main = a62d564. The five worktrees (chrome-autofill, chrome-linux, web-sites, web-ui, web-land) were removed after `git cherry` showed nothing unlanded and each tree was clean.
- The branch refs web-ui and web-sites are kept, because #1983 names 444461c.
- The deploy memory says "commit on web-ui". From now on, a web change goes on main.
- This handoff itself missed a62d564: the `git add docs/next-manager-2026-0*.md` glob did not match 2026-10. It lands on its own commit.
