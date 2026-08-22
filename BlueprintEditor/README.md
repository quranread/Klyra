# Blueprint Editor — Android (Kotlin / Jetpack Compose)

Port of the original `blueprint_editor_v11.html` web tool into a native Android app.

## Status: Part 6 of 8 — Export (+ review/bugfix pass on Parts 1–5)

This commit contains:
- Gradle/Kotlin/Compose project skeleton (Android app module, `minSdk 24`, `targetSdk/compileSdk 34`)
- Dark theme + color palette ported 1:1 from the original CSS `:root` variables (`ui/theme/Color.kt`)
- Data models, box/edge/center math, ViewModel (undo/redo, pending line/box state)
- Image canvas: pinch-zoom + pan (anchored, matches the web version's math), Dot/Line/Box/Pan
  tools, magnifier while placing, dimension lines, quick-delete badge
- Edit bottom sheet, elements list sheet
- **Export**: JSON blueprint (Storage Access Framework save), annotated PNG (all
  dots/boxes/lines/center-marks burned in at full resolution), AI Instructions
  sheet with Copy All
- `.github/workflows/android-build.yml` — GitHub Actions CI that builds a debug APK
  on every push/PR to `main`, and an unsigned release APK on pushes to `main`.
  Both are uploaded as workflow artifacts (Actions tab → run → Artifacts).

### Bugfix / gap-check pass (this commit)

Before starting Part 6, the whole Parts 1–5 codebase was reviewed against the web
version's v10→v11 changelog. Found and fixed:

- **Hit-test pan bug (critical):** `hitTest()` computed dot/line/badge screen
  positions without adding the canvas's current pan offset, while rendering
  *did* apply it (via `translate(panX, panY) { drawAnnotations(...) }`).
  Since fit-to-screen itself sets a non-zero pan, tapping an existing element
  to select it — or tapping its quick-delete badge — was silently
  misregistering by the pan amount almost all the time. Fixed in `HitTest.kt`
  / `AnnotationPainter.kt`'s `quickDeleteBadgeCenter()` / `GestureHandling.kt`.
- **Missing nudge/fine-tune controls (v11 web feature):** the ↑↓←→ position
  pad (1px/5px/10px step) and the width/height +/− steppers were entirely
  absent from `EditSheet.kt`. Added — this is what lets a box's center be
  corrected pixel-exactly after the two-corner tap, instead of re-tapping and
  hoping.
- **No confirm-before-wipe on re-upload:** picking a new image while
  dots/lines were already mapped silently discarded them. Now shows an
  AlertDialog first (only when there's something to lose).
- **No Clear All button anywhere in the UI:** added, with a confirm dialog.

## Roadmap

1. **Project setup** ✅ — Gradle/Compose scaffold + GitHub Actions CI
2. **Data models** ✅ — Dot/Line/Box elements, Blueprint JSON schema, ViewModel/state
3. **Image canvas** ✅ — image picker, pan/zoom, pinch/drag gestures
4. **Drawing tools** ✅ — placing Dot/Line/Box, edit bottom sheet
5. **Elements list + selection** ✅ — edit/delete, dimension lines, magnifier
6. **Export** ✅ — JSON export, annotated PNG export, AI Instructions tab
7. **UI polish** — bundle Space Grotesk + JetBrains Mono fonts (`ui/theme/Type.kt`
   has a TODO for this), tighter visual parity with the web version
8. **Final packaging** — signed release build via GitHub Actions, testing

## How to build locally

Open this folder in Android Studio (Koala+ recommended) and let it sync, or run:

```bash
./gradlew assembleDebug
```

> Note: the Gradle wrapper jar binary isn't included in this scaffold (it can't be
> generated in this environment without network access). Android Studio will
> regenerate it automatically on first sync ("Sync Project with Gradle Files"),
> or you can run `gradle wrapper --gradle-version 8.7` locally once to create it.

## How CI works

Push this project to a GitHub repo (any branch, plus `main`) and the workflow in
`.github/workflows/android-build.yml` will automatically build and upload the APK —
no local Android Studio needed to get an installable APK.

