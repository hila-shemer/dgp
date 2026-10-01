# dgp Chrome autofill / Android autofill / Chrome import: handoff (2026-09-27)

From Home Fleet Agent dgp-TrustyParrot. Design doc, with Hila's answers in its comments:
https://claude.ai/code/artifact/7b4958e9-30f3-48fe-bca2-147322c2d74d (rev 15).
The plan the code was built from is docs/chrome-autofill-plan.md on main.

## Landed (hila-fork main = 2ad1176)
| Button | What | Tip |
|---|---|---|
| #1984 | Items 1+2: sites field, SiteMatcher, Android AutofillService, DgpSession (stays unlocked), Linux extension + native host, and the security fixes | 644e138 |
| #1986 | Item 3: Import from Chrome on Android (Settings) and Linux (`dgp import-chrome`) | 4abd7d2 |
| #1983 | The web page keeps `sites`: web-ui = 444461c, and the live page was swapped | 444461c |
| #2009 | No autofill below API 28 (applied 09:24 UTC) | 2ad1176 |

## Pending
- Nothing. The post-fix test app (built from the landed 2ad1176) was installed on the phone at 13:10 phone time as `io.github.hilashemer.dgp.debug`. It starts clean, and Android lists its AutofillService. The phone's autofill service is still Google's, and the switch is Hila's to make.

## Needs Hila
1. **Release build.** The upload key named in local.properties, `/home/hila/proj/android-key/my-upload-key.jks`, is not on flatpot. Until she says where it lives, no release APK can update her installed DGP (1.2.0, sideloaded, installer=null).
2. **Local main reset.** Local `main` is a stale 3f8ca91, the same spec commit as the fork's, minus one .gitignore line. Proposal: `git -C /home/hila/proj/dgp branch -f main hila-fork/main`. It loses nothing; not run.
3. **Laptop Chrome** (mercury / shemer):
   - `pip install -e linux` from the repo;
   - `dgp chrome-install`;
   - chrome://extensions > Developer mode > Load unpacked > `linux/chrome-extension`;
   - extension ID `iagcagolicncaaanhhmdhfohinomcnhi`, shortcut Ctrl+Shift+L.
4. **First real tests.** None of these has run yet:
   - a phone fill in Chrome: turn on the test app, Settings > autofill service, then Chrome > Settings > Autofill services > "Autofill using another service";
   - a fill in an app;
   - the laptop extension;
   - an import of a real Google Password Manager CSV (she should delete the CSV afterwards).
5. **Q6 and Q7 had no answer.** The Chrome username goes in comment, and the web page has no import. Change either if she says so.

## Worth knowing
- **Build:** JDK 17 override and GitHub key are in project memory `project_android_build_on_flatpot`.
- **Shared matching table:** `linux/tests/fixtures/sitematch-cases.json`. Change the matching rules on both sides or neither.
- **Worktrees under .claude/worktrees:** `chrome-autofill` (now on autofill-min-api28), `chrome-linux`, `web-sites`. All are pushed and landed, so they can be removed.
- **Not done, and why:**
  - documentIds pinning in the popup: low risk, and it needs the webNavigation permission;
  - zeroing the seed String: this predates the work;
  - inline (keyboard) autofill suggestions: not built; the dropdown presentation is used.
