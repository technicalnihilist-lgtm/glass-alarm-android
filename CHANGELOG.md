# Changelog

## 1.1 — stability fixes

The 1.0 build could open to a blank page or die on launch. Fixed:

- **Added the `INTERNET` permission.** This was the big one. Android's WebView refuses to
  load *any* `http(s)` URL without it — including the `https://appassets.androidplatform.net`
  address the app serves its own page from. The page never loaded, so the app appeared to
  crash or sit on a blank screen. The app still makes no network requests of its own; nothing
  in the page points outside the APK.
- **Renderer death no longer kills the app.** The glass effect is GPU-heavy, and on a
  low-memory device Android can kill the WebView renderer. That used to take the whole
  process down. `onRenderProcessGone` now rebuilds the WebView instead.
- **Graceful load failures.** If the internal `https` origin fails on a particular device, the
  shell falls back to loading the same page from `file:///android_asset/`. If that fails too,
  it shows a readable message instead of a blank screen.
- **No WebView provider = a message, not a crash.** Devices with a disabled or broken
  Android System WebView now get an explanation with what to update.
- **`largeHeap`** so the WebView has headroom for the glass/backdrop-filter surfaces.
- **Back handling wrapped in `try`/`catch`** so a page that failed to load cannot make the
  back button throw.
- **Fixed a self-inflicted bug** where the navigation guard would have cancelled the
  `file://` fallback load.

Performance:

- **Displacement maps are built at a capped resolution** (long edge ≤ 192 px) and stretched
  by `feImage`. They are smooth by nature, so the refraction looks the same, but the cost per
  map dropped from ~117 ms to ~28 ms of main-thread work in testing, and the generated data
  URIs from ~41 KB to ~15 KB per filter — less jank at launch and less GPU memory held by
  each glass surface.
- The per-pixel loop no longer calls `Math.hypot` / `Math.pow`.

Verified in Chromium against the exact HTML in the APK: app opens, alarm list renders, the
new-alarm sheet and settings open and close, alarms persist across reload, an alarm rings at
its minute, snooze and stop work, and no JavaScript errors are raised.

## 1.0

First build.
