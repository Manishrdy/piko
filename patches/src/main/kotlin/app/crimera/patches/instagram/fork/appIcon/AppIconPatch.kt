/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.fork.appIcon

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val appIconPatch =
    bytecodePatch(
        name = "Change app icon",
        description =
            "Adds an app icon picker to piko settings. " +
                "Offers the icons piko bundles alongside the alternate icons Instagram already ships.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, appIconResourcePatch)

        execute {
            enableSettings("appIcon")
        }
    }
