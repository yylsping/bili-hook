# 4K/HFR investigation baseline — 2026-10-07

Phase 0 result: both unchanged HEADs built and passed existing JVM tests with JDK 17. This file preserves the initial baseline; later verified findings are in investigation-report.md.

| Item | Observed baseline |
| --- | --- |
| Device | PLQ110; Android 17 / API 37 |
| Host | `tv.danmaku.bili`, 7.42.0 / 7420400 |
| bili-hook | 1.7.0 / 13; HEAD `45e9d076a079e9373db599d10b1b620f60549633` |
| bili-part-fix | 1.8.0 / 12; HEAD `cd95c354c1067b8c7362a2558d0713d3b15da89d` |
| Target | `BV1P5411Q7Cj`, part 1 |
| Account | Logged-in non-VIP; no credentials or account IDs recorded |
| Display | Baseline active Android mode 2, approximately 60Hz; native resolution 1272×2800 supports 60/90/120/144/165Hz |
| Builds | `testDebugUnitTest assembleDebug`: bili-hook 43 tests, bili-part-fix 73 tests; zero failures/errors |
| Module activation | User enabled modules; overlays do not require GUI reactivation |
| MCP identity | Initial Frida health check passed but Android17 injection later failed; MT 0.2.0 workspace verified 7420400; JADX loaded 9130500 reference APK |

## Code and prior evidence reviewed

- bili-hook: exact-version main-process entry, 7040300 premium-qn request hooks, 7420400 owner/content transaction and scoped current-account VIP override, host-native auto decision, debug resource observation, ad filters, JVM state-machine tests.
- 7420400 hooks include UGC `t5/N0/e3/a/s3/i6` and PGC `t5/S1/B1/a/c2/Z6`; request builders are inspected for ABI availability, not globally rewritten. `Bili742DebugProbe` observes preference/resource lifecycle. Runtime installation was subsequently confirmed.
- bili-part-fix: exact-version routing; multipart/watch-later fixes; shared decoder profile/policy; ordinary-UGC identity scope; asset build `R` (7040300) / `S` (7420400); options hardware preference `g` / `i`. Legacy account/comment/article/PlayerUnite fixes are 7040300-only.
- Read local 7420400 quality notes and decoder mapping, and 7040300 player-codec-chain, Smart Auto V2 and native/fallback notes. Git trees retain the historical codec research documents; their original measurements are prior evidence, not new HFR measurements.
- Confirmed diagnostic defect in current source: `StreamInfoExtractor.findSelectedDash()` returns the first matching qn. Multiple representations cannot be reliably distinguished this way; actual multiplicity on the target is not established yet.

## Environment boundaries

- Existing host worktree change: bili-hook `.gitignore`; preserve it. Signing material stays private and is never printed or staged.
- Installed modules use release certificates; default DEBUG certificates differ. Any DEBUG overlay must use each project's existing release signing configuration, verify certificate equality, then use `adb push` + root `pm install` under `/data/local/tmp/processing/`. No uninstall/data clearing.
- No remote writes, device reboot, LSPosed daemon restart or database mutation. Module state changes only through LSPosed GUI when needed.
- Both old versions were subsequently exercised without clearing data; the 60Hz initial baseline alone does not establish a target display-layer root cause.

Follow-up: both hosts reached Branch B for the target, with playable representations at30.303fps. Final code validation passes48 hook +81 part-fix JVM tests, DEBUG and release builds. Scene later selects physical120Hz while idle presentation can remain60Hz. Frida is updated to17.22.2 on both ends, retained at the user's request, and excluded from current measurements because injection remains incompatible.
