# AIO Founder Android R5

Portable Android endpoint for the AIO Founder Fabric.

## Current candidate — 0.7.0-candidate-r5

Implemented in source:

- natural Founder Dialogue through the paired Windows AIO path
- AIO-native Presence / projection membrane
- direct Presence transport plus R5 cloud fallback
- direct-first fallback only when the direct socket cannot open; no post-connect authority downgrade
- encrypted witness, lease, cloud admission key and tunnel key custody through Android Keystore wrapping
- transport-session replay scoping and source-session epoch tracking
- pinned E2E Windows gateway response verification
- persistent Founder-controlled `remoteMessaging` foreground node
- Activity ↔ foreground-service R5 link handoff and Activity recreation restore
- autonomous protected-state R5 reconnect with bounded backoff after process/link loss
- user-requested Android foreground-service Stop reconciliation
- endpoint-local peer-bound capability grants; grant identifiers never leave Android
- bounded typed capability request/reply protocol with separate unsolicited-node inbox
- screen observation through fresh per-session MediaProjection consent
- bounded JPEG `screen.capture` replies
- Founder-enabled Accessibility tap/swipe service with window-content retrieval disabled
- foreground-visible-only plain-text clipboard read/write with sensitive clipboard marking
- app-owned rate-limited remote notifications
- SAF file-tree custody with list/read/hash/write/rename/delete actions
- hardened SAF path normalization and ambiguous-name rejection
- local resource projection: battery, charging, thermal state, memory, storage and validated network
- bounded resource contribution admission plus fixed SHA-256 / DEFLATE worker kernels; no arbitrary code execution
- signed self-update staging: exact package, strictly newer version, signer match, SHA-256, private staging
- self-update install through Android PackageInstaller with AIO biometric approval and forced Android user confirmation
- Founder Voice Room / Android ASR-TTS boundary
- bounded persistent grant and receipt state
- strict fabric JSON and cloud-tunnel envelope bounds
- adversarial JVM courts across transport, authority, persistence, resource, screen, gesture, clipboard, file and update policies

## Physical / build qualification

The R5 cloud → Windows tunnel → live Presence 47103 path is physically PASS on the Windows side. That does **not** qualify the Android endpoint.

Current Android states:

- newest 0.7 GitHub source: IMPLEMENTED / BUILD QUALIFICATION HOLD
- GitHub Actions: external runner failure before step 1; no application-code signal
- Daniel Android tree: partially synchronized; do not build mixed source and call it qualified
- last fully executed earlier Android local preflights: PASS for their exact older source snapshots
- S25 Ultra USB ADB: NOT_CONNECTED
- S25 Ultra Wireless Debugging mDNS: NOT_CONNECTED
- Android → R5 → Windows natural Dialogue: NOT_VERIFIED
- Windows → R5 → Android typed capability invocation: NOT_VERIFIED
- persistent-node process/link recovery: source implemented; newest physical court NOT_VERIFIED
- MediaProjection screen observation: source implemented; fresh user consent required; physical court NOT_VERIFIED
- tap/swipe Accessibility gestures: source implemented; Founder must enable Accessibility service; physical court NOT_VERIFIED
- remote clipboard: source implemented; AIO Activity must be foreground-visible; physical court NOT_VERIFIED
- remote notifications: source implemented; Android notification permission + Founder grant required; physical court NOT_VERIFIED
- SAF file write/read/rename/delete: source implemented; Founder-selected tree + grant required; physical court NOT_VERIFIED
- bounded resource contribution: source implemented; charging/thermal/memory admission + Founder grant required; physical court NOT_VERIFIED
- signed self-update: source implemented; local APK selection + biometric + Android installer confirmation required; physical update court NOT_VERIFIED
- GPT live voice provider: CONFIG_REQUIRED

## Authority order

A connected R5 peer is never treated as identity.

Remote Android capability use requires:

1. authenticated R5 transport,
2. current transport-session identity,
3. pinned encrypted Windows gateway proof,
4. explicit endpoint-local Founder grant,
5. capability/tier/privacy validation,
6. live platform prerequisite (for example MediaProjection consent or Accessibility enablement),
7. bounded typed parameters,
8. a correlated typed receipt.

A replacement Windows R5 source session invalidates prior peer verification before the next capability dispatch.

## Explicit non-capabilities

The Android node does not expose:

- arbitrary shell or command execution
- arbitrary Java/Dex/native code execution
- arbitrary Accessibility actions
- arbitrary Activity/Intent launch from Windows
- silent Accessibility enablement
- cached/reused MediaProjection consent
- silent APK installation
- arbitrary package installation
- raw filesystem paths outside the Founder-selected SAF tree

## GitHub hygiene

No runtime secrets, pairing material, APK deliveries, `local.properties`, build caches, Android Keystore material, or endpoint credential files belong in GitHub.
