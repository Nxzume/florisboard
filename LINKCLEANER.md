# LinkCleaner (FlorisBoard)

- **Clipboard:** When FlorisBoard's IME is active, the system clipboard is sanitized on change (tracking params stripped, redirectors unwrapped). Implementation: `ClipboardManager.onPrimaryClipChanged()` → `LinkCleanerBridge.sanitize()`.
- **Open copied link:** When the clipboard contains a URL, an **Open link** quick action appears on the smartbar (and in the overflow panel). It fires `ACTION_VIEW` with the (cleaned) URL, same as tapping a link.
- **Floating bubble (keyboard hidden):** Settings → Clipboard → **Floating open-link button** (on by default). When the keyboard is not shown, a draggable overlay bubble appears after copying a URL. You must allow **Display over other apps** for FlorisBoard in system settings, or the bubble cannot appear.
- **Setting:** In FlorisBoard settings → `Clipboard`:
  - toggle **Link cleaning** to enable/disable URL cleaning
  - toggle **Aggressive link cleaning** to switch between conservative cleaning and aggressive query-param stripping (only enabled when link cleaning is ON)
- **Clicked links:** When you tap a link, if the system shows an "Open with" chooser, pick FlorisBoard. (On many devices you cannot set a non-browser as default for all links.) `OpenLinkActivity` receives the URL, cleans it, and opens the result in your browser.
- **Links that open in-app:** When a link opens inside the app (e.g. Twitter, Reddit, in-app browser) you don't get "Open with". Use **Share** → **FlorisBoard** from the in-app page or link menu. FlorisBoard cleans the URL and opens it in your browser (or copies to clipboard if it's not a single link). `ShareLinkActivity`.

Rules: `lib/linkcleaner/src/main/res/raw/linkcleaner_default_rules.json`.
