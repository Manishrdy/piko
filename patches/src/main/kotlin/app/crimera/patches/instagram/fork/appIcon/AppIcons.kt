/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.fork.appIcon

/**
 * Meta-data key naming a repurposed alias in the picker.
 *
 * The bundled icons reuse Instagram's own alternate-icon aliases, so an alias keeps
 * Instagram's name ("...MainTabActivity.kpop") no matter which artwork it now carries,
 * and the name cannot serve as the label. The patch writes the label here instead.
 * Must stay in sync with AppIconManager.ICON_LABEL_MARKER in the extension.
 */
internal const val ICON_LABEL_MARKER = "app.morphe.piko.icon_label"

/**
 * Meta-data key the patch attaches to Instagram's own launcher alias, so the picker can
 * tell the stock icon apart from the alternates.
 * Must stay in sync with AppIconManager.DEFAULT_ICON_MARKER in the extension.
 */
internal const val DEFAULT_ICON_MARKER = "app.morphe.piko.default_app_icon"

/**
 * Density buckets each bundled icon ships a raster for.
 *
 * Only the two buckets modern phones actually use. Lower-density devices scale down
 * from xxhdpi at no visible cost, and the bundle stays small -- the same trade the
 * Twitter app icon patch makes by shipping xxhdpi alone.
 */
internal val ICON_DENSITIES =
    arrayOf(
        "xxhdpi",
        "xxxhdpi",
    )

/**
 * Icons bundled with the patch, by slug.
 *
 * Each slug needs these files under patches/src/main/resources/instagram/appicons:
 *
 *   mipmap-anydpi-v26/piko_icon_<slug>.xml            adaptive icon wrapper
 *   mipmap-<density>/piko_icon_<slug>_foreground.webp artwork, one per density
 *   mipmap-<density>/piko_icon_<slug>_background.webp backdrop, one per density
 *
 * docs/instagram_app_icons.md covers the sizes, and the script that generates all of it
 * from a single square source image.
 *
 * The slug becomes the name shown in the picker: underscores turn into spaces and each
 * word is capitalised, so "retro_glow" reads as "Retro Glow".
 *
 * Leaving this empty is valid -- the picker then offers Instagram's own icon set, which
 * the app already ships aliases for.
 */
internal val bundledIcons =
    arrayOf<String>(
        "dark_gradient",
        "mono_black",
        "sunset_gradient",
        "vivid_gradient",
    )
