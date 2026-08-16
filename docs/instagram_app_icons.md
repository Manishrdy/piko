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

Two conventions hold it together, both mirrored between `AppIcons.kt` and `AppIconManager`:

- Aliases piko adds are named `app.morphe.extension.instagram.appicon.<slug>`. That prefix
  stays outside the `com.instagram.*` namespace so the Clone patch's rewriting cannot move
  an alias out from under the runtime.
- The patch tags Instagram's own launcher alias with an
  `app.morphe.piko.default_app_icon` meta-data entry, which is how the picker labels the
  stock icon and knows what the unset state resolves to.

Each bundled alias is a *clone* of the stock alias with the name, icon and enabled state
replaced. Cloning carries over the launch attributes, the shortcuts meta-data and the
`instagram://` deeplink intent-filters, so selecting a bundled icon does not silently break
deeplinks the way a hand-written `MAIN`/`LAUNCHER`-only alias would.

`android:label` is deliberately *not* set per icon. The launcher draws that text under the
icon, so it has to stay the app's name. Display names in the picker come from the alias
name instead: `throwback` reads as "Throwback", `retro_glow` as "Retro Glow".

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

## Known behaviour

- The icon can take a moment to change, and some launchers only redraw it after you leave
  and return to the home screen.
- Shortcuts pinned to the previously active alias may be dropped by the launcher when that
  alias is disabled. This is inherent to alias-based icon switching, not specific to piko.
- Leaving `bundledIcons` empty is valid. The picker then offers Instagram's own icon set,
  which needs no bundled artwork at all.
