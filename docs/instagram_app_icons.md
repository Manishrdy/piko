# Instagram app icons

The `Change app icon` patch adds an icon picker to piko settings and lets the user switch
the launcher icon without reinstalling. This page covers how it works and how to add a new
icon to the bundled set.

## How the switching works

Instagram's launcher entry is an `<activity-alias>`, not an `<activity>`:

```
com.instagram.android.activity.MainTabActivity  ->  com.instagram.mainactivity.InstagramMainActivity
```

The app also ships thirteen more aliases of its own, all `android:enabled="false"`, for its
"aura" icon set (`kpop`, `floral`, `flame`, `slime`, `neon`, `metal`, `haruko`, `felipe`,
`humberto`, `zipeng`, `uzo`, `ricky`, `throwback`).

So switching icons is just choosing which alias the package manager treats as enabled.
`AppIconManager` does that with `PackageManager.setComponentEnabledSetting`, enabling the
chosen alias before disabling the others so there is never a moment with no launcher entry.

The picker does not hold a hardcoded icon list. It queries `MAIN`/`LAUNCHER` with
`MATCH_DISABLED_COMPONENTS`, so it shows whatever aliases the installed app actually
declares. That keeps working when Instagram changes its own icon set between versions, and
when the Clone patch installs the app under a different package name.

Two meta-data keys hold it together, both mirrored between `AppIcons.kt` and
`AppIconManager`:

- `app.morphe.piko.default_app_icon` tags Instagram's own launcher alias, which is how the
  picker labels the stock icon and knows what the unset state resolves to.
- `app.morphe.piko.icon_label` tags an alias repointed at bundled artwork, and carries the
  label to show for it.

**The patch adds no launcher components.** It repoints `android:icon` on aliases
Instagram already declares — one of its aura aliases per bundled icon — and nothing else.

That is a deliberate correction. The first version cloned the stock alias once per bundled
icon and set `android:enabled="false"` on each clone. When that attribute failed to take
effect the clones defaulted to *enabled*, and the launcher showed one entry per bundled
icon: five icons for four bundled ones, on a fresh install, before the picker was ever
opened. Reusing existing aliases makes that unreachable — the patched app declares exactly
the launcher components stock Instagram does, so it cannot present more of them than stock
Instagram would, whatever happens to any single attribute.

The patch also asserts, after rewriting the manifest, that exactly one launcher component
is left without `android:enabled="false"`, and fails the build otherwise. That invariant is
silent at patch time and expensive to discover on a device.

`android:label` is deliberately *not* set per icon. The launcher draws that text under the
icon, so it has to stay the app's name. Because a repointed alias keeps Instagram's own
name, the picker label comes from an `app.morphe.piko.icon_label` meta-data entry the patch
writes alongside the icon; aliases piko has not touched still fall back to their name, so
`throwback` reads as "Throwback".

## Adding an icon

Instagram is `minSdkVersion 28`, so icons are adaptive-only — no legacy square fallback is
needed. An adaptive icon is a 108dp square of which only the inner 72dp is guaranteed
visible; launcher masks crop the rest.

1. Start from a square source image, ideally 1024x1024 PNG with a transparent background.

2. Set up the generator once:

```bash
python3 -m venv docs/tools/.venv && docs/tools/.venv/bin/pip install Pillow
```

3. Generate the resources:

```bash
docs/tools/.venv/bin/python docs/tools/generate_app_icons.py retro_glow logos/retro.png
```

   The slug must be lowercase letters, digits and underscores.

   The default `--mode tile` suits a source that is already a finished app icon — a
   filled square or rounded square with a transparent margin around it, which is what
   icon packs usually give you. It trims the margin, places the tile at safe-zone size
   in the foreground, and fills the background with a zoomed copy over the tile's own
   sampled edge colour, so a launcher mask always has matching colour to bite into.

   Use `--mode logo --background "#1E88E5"` instead when the source is bare artwork on
   transparency, with no tile of its own. The artwork is then inset into the safe zone
   over that flat colour.

   This writes, under `patches/src/main/resources/instagram/appicons`:

   ```
   mipmap-anydpi-v26/piko_icon_<slug>.xml
   mipmap-{xxhdpi,xxxhdpi}/piko_icon_<slug>_foreground.webp
   mipmap-{xxhdpi,xxxhdpi}/piko_icon_<slug>_background.webp
   ```

   Only those two density buckets are shipped; lower-density devices scale down from
   xxhdpi. Foregrounds are lossless WebP to keep their alpha edge clean, backgrounds
   lossy WebP since they are opaque and carry only smooth colour.

4. Add the slug to `bundledIcons` in
   `patches/src/main/kotlin/app/crimera/patches/instagram/fork/appIcon/AppIcons.kt`.

5. Rebuild: `./gradlew generatePatchesList buildAndroid`.

Nothing else needs editing. The alias, the manifest entry and the picker row all follow
from the slug.

## Keeping exactly one launcher entry

The feature rests on one invariant: exactly one `MAIN`/`LAUNCHER` component enabled at a
time. Two enabled at once is what the launcher renders as **duplicate entries in the app
drawer**, and it is reachable in more than one way — a `setComponentEnabledSetting` call
that fails partway through the two-component flip, or a reinstall resetting overrides to
the manifest defaults while the app still intends a different icon.

So the flip is not trusted to have worked. `AppIconManager.reconcile()` runs on every app
start, off the main thread, from `SettingsStatus.load()`:

- the pick is written to `SharedPreferences` *before* the flip, so the intent survives a
  half-completed switch, an update, and a process death;
- one query lists the launcher entries that are not disabled, and if that is already
  exactly the intended one it returns without touching the package manager;
- otherwise it enables the intended alias and disables every other one.

The intended alias is the stored pick if it still exists, else whichever alias carries the
`app.morphe.piko.default_app_icon` marker. If neither resolves it does nothing — guessing
wrong there would mean an app with no launcher entry at all. The intended alias is always
enabled before the others are disabled, so there is never a window with none enabled.

A duplicate can therefore outlive at most one app launch.

## Known behaviour

- The icon can take a moment to change, and some launchers only redraw it after you leave
  and return to the home screen.
- A clean uninstall wipes `SharedPreferences` too, so the icon returns to stock. Only an
  update preserves the pick.
- Shortcuts pinned to the previously active alias may be dropped by the launcher when that
  alias is disabled. This is inherent to alias-based icon switching, not specific to piko.
- Leaving `bundledIcons` empty is valid. The picker then offers Instagram's own icon set,
  which needs no bundled artwork at all.
