# RemodexAndroid AGENTS.md

This file records Android-specific guardrails and hard-won debugging notes.

## Persistent Reminder

- Before doing more Android emulator or local-bridge debugging, read this file first.
- When a new Android debugging constraint is discovered, write it back here instead of re-learning it in chat.
- Prefer matching iOS behavior by reading the iOS implementation and the screenshots in `RemodexAndroid/referance/` before rebuilding UI from memory.

## Source Of Truth

- Treat `CodexMobile/` as the product spec when Android parity is unclear.
- Use `RemodexAndroid/referance/` as the visual shell target.
- Keep the repo local-first. Do not reintroduce hosted relay defaults or hardcoded remote domains.
- Do not log live relay `sessionId` values or other bearer-like pairing identifiers.

## Android Parity Guardrails

- Preserve saved pairing and reconnect behavior. Do not clear saved relay info too early.
- Do not let onboarding or auto-reconnect race the manual QR scan flow.
- Keep thread actions, settings state, and turn timeline behavior aligned with iOS names and sections where practical.
- If a feature exists on iOS already, prefer implementing the same bridge RPC flow instead of inventing a new Android-only one.

## Build Environment

- The working Java setup here is:
  - `JAVA_HOME=/opt/homebrew/opt/openjdk@17`
  - `PATH="$JAVA_HOME/bin:$PATH"`
- The working Android SDK setup here is:
  - `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`
  - `ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools`
- `RemodexAndroid/local.properties` must point at that SDK:

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

- Known-good build command:

```bash
cd /Users/eyhn/remodex/RemodexAndroid
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug
```

## Emulator Runbook

- Known-good emulator device: `remodex_api35`
- Verify emulator presence with:

```bash
adb devices
adb shell dumpsys activity activities | rg "topResumedActivity|mResumedActivity"
```

- Install and launch with:

```bash
adb install -r /Users/eyhn/remodex/RemodexAndroid/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.remodex.android/.MainActivity
```

- After reinstall, Android will kill the old app process. If the device shows the launcher right after install, that is expected. Relaunch the activity and confirm `topResumedActivity` before assuming a crash.

## Local Bridge Runbook

- Use the repo-local local-first bridge stack, not an old global daemon.
- Known-good start command:

```bash
cd /Users/eyhn/remodex
./run-local-remodex.sh --hostname <LAN_IP> --port 9010
```

- Health check:

```bash
curl --silent http://127.0.0.1:9010/health
```

- Keep that bridge session alive while validating the Android app.
- If `~/.remodex/daemon-config.json` still points at `wss://api.phodex.app/relay`, your pairing payloads are stale even if `run-local-remodex.sh` is running. Re-run the repo-local bridge command from `/Users/eyhn/remodex/phodex-bridge` with `REMODEX_RELAY=ws://<LAN_IP>:9010/relay` and verify both `daemon-config.json` and `pairing-session.json` switched to the local relay before pairing Android again.
- `launchctl print gui/$(id -u)/com.remodex.bridge` should show `/Users/eyhn/remodex/phodex-bridge/bin/remodex.js` in the arguments. If it still points at a globally installed `remodex`, you are debugging the wrong bridge build.

## Pairing Failure Triage

- If pairing fails with `pairing_expired`, the payload is stale. Regenerate a fresh one from the currently running local bridge.
- If pairing fails with `phone_not_trusted` or the app says `This device is not trusted by the current bridge session`, the phone is trying `trusted_reconnect` against a different bridge run. Do not keep hitting `Connect`; use `Scan New QR Code` and import a fresh payload from the current local bridge session.
- If pairing fails with `update_required`, first suspect an old bridge/runtime process, not the Android UI.
- A physical phone can preserve an older remote relay across app reinstalls. If Android logs show `Connecting to relay: wss://api.phodex.app/relay/...` while the local bridge is healthy, the phone is not using the current local pairing even if the latest APK is installed.
- Local QR payloads expire quickly. Before importing a typed payload on device, verify `~/.remodex/pairing-session.json` has a future `expiresAt`; if not, run the repo-local `node ./bin/remodex.js up` again to publish a fresh pairing payload before retrying.
- Check for stale global config in:
  - `~/.remodex/daemon-config.json`
- A previously observed bad state was `relayUrl: "wss://api.phodex.app/relay"` in that global config. That is wrong for this repo now.
- Also check for an old `remodex run-service` process still running outside this repo. It can keep emitting stale pairing payloads and waste time.
- Prefer the fresh pairing session created by the repo-local stack, not older saved payloads.

## Scanner Debug Flow

- In debug builds, `QRScannerScreen.kt` has a manual payload input path. Use it instead of fighting camera automation.
- Feed it a fresh base64url-encoded payload from the local pairing session.
- The current local bridge writes that fresh payload to `~/.remodex/pairing-session.json`. For device-side debug import, converting `pairingPayload` to compact JSON and then base64url works with the `Import Typed Payload` field.
- If a manual payload import appears to do nothing, verify the payload came from the currently running local bridge, not an older file.
- On some emulators, a single `adb shell input text` call truncates long base64url payloads. If the debug field only fills partway, reopen the scanner and inject the payload in smaller chunks instead of assuming the parser is broken.
- On the real phone, a successful fresh import is easy to confirm from logs: Android should switch from the stale relay URL to the local `ws://<LAN_IP>:9010/relay/...`, then log `Secure channel established` and `Session initialized`, and the UI should leave the offline screen for the chat timeline.

## UI Verification Workflow

- Use screenshots plus `uiautomator dump` together. Do not trust taps alone.

```bash
adb exec-out screencap -p > /Users/eyhn/remodex/.tmp-screen.png
adb shell uiautomator dump /sdcard/view.xml >/dev/null
adb pull /sdcard/view.xml /Users/eyhn/remodex/.tmp-view.xml >/dev/null
```

- The most reliable way to verify Compose menus and drawers is by checking `content-desc` and text labels in the dumped XML.
- Useful content descriptions seen in this app:
  - `Open sidebar`
  - `More actions`
  - `Settings`
  - `Thread actions`
  - `Attachment options`
  - `Refresh status`
- Some taps may need to be retried once on this emulator. Do not assume the UI path is broken until a fresh dump confirms it.
- For current turn/composer verification, grepping the dumped XML for `Local`, `Context`, `Refresh status`, `Auto`, `On Request`, `Thinking`, and `Stop` is a fast sanity check that the new chat shell is actually on screen.

## Known-Good Connected-State Checks

- Connected sidebar should show local status text like `Connected to Mac` and `Local bridge ready`.
- Connected thread page should expose:
  - top bar title and repo subtitle
  - composer
  - stop/send button depending on turn state
  - overflow menu
- Current Android menu parity work includes:
  - `New chat`
  - `Rename`
  - `Archive`
  - `Delete`
  - `Update bridge`
  - `Scan QR code`
  - `Commit`
  - `Push`
  - `Commit & Push`
  - `Create PR`
  - `Pull`
  - `Settings`
- Current turn-screen parity work also includes:
  - composer image attachments with thumbnail preview
  - secondary status strip with `Local`, context usage, and refresh
  - structured user input cards in the timeline
  - richer subagent cards and command detail dialog

## Turn / Collaboration Notes

- Structured user input requests from the bridge come through `item/tool/requestUserInput`. If that path breaks, compare Android against the iOS plan-mode handling before changing the RPC shape.
- Plan and subagent timeline rows must be decoded both from `thread/read` history and from live notifications. If only one path is updated, Android will look correct briefly and then regress when sync refreshes history.
- Reasoning updates may arrive as deltas. Merge them into the existing thinking row instead of appending a new `Thinking...` row for every event.
- `thread/read` history is nested under `result.thread`, not directly under the top-level RPC result. If Android decodes the root object instead of `thread`, sidebar previews will load but the chat page will show an empty timeline for existing threads.
- History item text and image attachments come from `content[]` entries on modern runtimes. If you only read flat `text` / `images` fields, reopened chats will look empty or lose attachments even though the bridge returned turns.
- Do not use `drop(1)` on `connectionManager.isConnected` to detect reconnects. On app relaunch the first observed value may already be `true`, which suppresses `clientHello` and leaves Android stuck offline after the WebSocket opens. Compare previous/current state explicitly instead.
- If the first visible thread is auto-selected from `thread/list`, immediately trigger `loadThreadHistory()` for that resolved active thread. Otherwise a cold launch can show the correct title and composer but an empty timeline until the user manually reselects the thread from the sidebar.
- Known-good verification for the fix above: after `adb shell am force-stop com.remodex.android` and relaunching `com.remodex.android/.MainActivity`, `uiautomator dump` should already contain the thread title and real message text like `请你对比ios，看看android版本功能完全吗` without any manual sidebar tap.
- If `thread/read` / `thread/list` start timing out after a relay flap, the likely bug is transport state, not UI. The fix path that worked here was:
  `Connection lost -> cancel pending RPCs + stop sync loops + reset secure state -> on next socket open send a fresh clientHello -> only then restart initialize/sync`.
- After a bridge session restart, an old selected thread can still exist locally but fail `turn/start` with `thread not found`. If emulator validation needs a fresh prompt, create a new chat first instead of debugging timeline code on a dead thread id.
- Timeline/history verification is not done when the tool row appears once. Wait long enough for at least one idle `thread/read` refresh and confirm command/tool rows are still visible afterward; this is the check that catches history replacement regressions.

## Settings Parity Checks

- Settings should be checked against iOS section names first.
- Current important Android sections to preserve:
  - `Archived Chats`
  - `Appearance`
  - `Notifications`
  - `ChatGPT`
  - `Runtime Defaults`
  - `Usage`
  - `Bridge Version`
  - `Connection`

## Avoid Repeating These Mistakes

- Do not assume a pairing payload is valid just because the file exists.
- Do not assume landing on the launcher after reinstall means the app crashed.
- Do not assume a missing feature on Android needs a new protocol. Check the iOS RPC first.
- Do not switch back to remote relay defaults during debugging.
- Do not debug the camera scanner first when the debug payload import path is available.
