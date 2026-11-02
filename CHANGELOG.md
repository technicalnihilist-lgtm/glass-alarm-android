# Changelog

## 1.2 — the page no longer depends on anything at runtime

1.1 still crashed on the user's device, so this release removes the failure
surface instead of guessing at it.

- **The app now loads a single self-contained file over `file:///android_asset/`.**
  The `https://appassets.androidplatform.net` origin, the request interception,
  the custom `WebViewClient` and the `INTERNET` permission are all gone. There is
  nothing left to resolve, intercept or fail at load time.
- **`setAllowFileAccess(true)` is now set explicitly.** For apps targeting API 30+
  the WebView default is `false`, which blocks `file://` loads completely — this
  alone would have broken any fallback path.
- **The Outfit fonts are embedded in the HTML as `data:` URIs**, so the page is a
  single 144 KB file with zero subresources. Verified: rendering the file from
  `file://` with file access flags *disabled* produces no failed requests and the
  typography is unchanged.
- **Crash reporter.** Any uncaught exception is written to internal storage and
  shown on the next launch as selectable text, with an "Open app anyway" button.
  `onCreate` is wrapped too, so a failure at startup displays the stack trace
  instead of the app just closing. The report includes Android version, device
  model, app version and WebView version.
- **Renderer recovery is capped** at one rebuild, then a readable report, instead
  of an unbounded rebuild loop.
- **The screen is kept awake natively** with `FLAG_KEEP_SCREEN_ON`; the page's
  Wake Lock API needs a secure origin and no longer applies on `file://`.
- Cutout-mode theme attribute moved from `values-v27` to `values-v28` (the
  attribute is API 28).

If it still fails, the app now shows *why* — a screenshot of that screen is
enough to fix it.

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
