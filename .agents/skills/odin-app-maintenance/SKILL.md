---
name: odin-app-maintenance
description: Check and safely maintain the user’s AYN Odin Android application stack when asked about installed emulator, frontend, or utility app updates. Use for version audits and explicitly authorized in-place app updates; not for ROM, BIOS, key, save, bootloader, root, or firmware changes.
---

# Odin app maintenance

Maintain the handheld’s installed application stack through ADB while preserving its game library and app data.

## Scope

- Confirm the connected ADB serial before inspecting or changing packages. The known Odin has serial `a782c9a1`; do not assume it is still connected or uniquely present.
- Read the installed package version from the device, then compare it with the project’s **official** release channel. Do not treat third-party APK mirrors as an update source.
- The default channel is **stable only**. Skip nightly, preview, development, and pre-release packages unless the user explicitly asks for one.
- The Android ES-DE app is paid and partially closed source, but it belongs to this handheld’s maintained frontend stack and may be checked when relevant.
- Treat emulator keys, BIOS, firmware, ROMs, saves, shader caches, and ES-DE media as out of scope unless the user specifically asks to manage them. Do not read key contents.

## Installed stack and authoritative channels

Use the actual package list as the source of truth. The usual maintained packages are:

| Role | Package | Official stable release channel |
|---|---|---|
| Frontend | `org.es_de.frontend` | ES-DE Patreon/package link supplied to the user |
| PS1 | `com.github.stenzek.duckstation` | `stenzek/duckstation` GitHub Releases; prefer a non-preview tagged release |
| GBA/GB/GBC/NDS | `com.sky.SkyEmu` | `skylersaleh/SkyEmu` GitHub Releases |
| PSP | `org.ppsspp.ppsspp` | `hrydgard/ppsspp` GitHub Releases |
| MAME | `com.seleuco.mame4droid` | `seleuco/MAME4droid-Current` GitHub Releases |
| Multi-system | `com.swordfish.lemuroid` | `Swordfish90/Lemuroid` GitHub Releases |
| PS2 | `com.armsx2` | `ARMSX2/ARMSX2` GitHub Releases |
| Switch | `dev.eden.eden_emulator` | Eden stable downloads; do not replace a newer nightly with an older stable build |
| Switch | `org.citron.citron_emu` | `citron-neo/emulator` GitHub Releases; CI builds are nightly only |
| LAN transfer | `org.localsend.localsend_app` | `localsend/localsend` GitHub Releases |
| Network utility | `com.nebula.karing` | `KaringX/karing` GitHub Releases; skip pre-releases by default |

`com.explusalpha.GbaEmu` (My Boy!) and `com.xiaoji.egggame` (Egg NS) are not open-source maintenance targets. Include them only if the user asks about them specifically.

## Update flow

1. Run `adb devices` and confirm the Odin is authorized. If more than one device is present, use an explicit serial after confirming the target.
2. Record the installed `versionName` and `versionCode` using `dumpsys package` or `pm dump`.
3. Check the project’s official release page. Categorize each result as: current stable, stable update available, or newer non-stable build only.
4. Before a user-authorized update, download only the matching Android ARM64 stable asset from the official release. Record its source URL and SHA-256 when the publisher provides one.
5. Update in place with `adb install -r <apk>`. This preserves app-private data. If Android reports a signature mismatch, package conflict, or downgrade, stop and report it; do not uninstall the existing app as a workaround unless the user separately authorizes that risk.
6. Query the device again to confirm the intended package and version. When a publisher has not changed an APK's Android `versionName` or `versionCode`, verify the installed base APK hash against the verified download and report the metadata discrepancy instead of treating it as a failed update. Do not open the app or inspect its data unless requested.

## Update guardrails

- Never update MAME4droid as part of a general batch when the user excluded it. A MAME core update can change ROM-set compatibility.
- Do not downgrade an app merely to reach a “stable” label. Report the mismatch and leave the device unchanged unless the user explicitly requests a rollback.
- Do not force-stop, clear data, uninstall, change permissions, or switch drivers as part of an update check.
- Report skipped apps with the reason: current stable, stable version unavailable, nightly/pre-release only, user excluded, or package/version cannot be safely compared.
- Report every completed change as `package: old version → new version`, plus verification outcome.
