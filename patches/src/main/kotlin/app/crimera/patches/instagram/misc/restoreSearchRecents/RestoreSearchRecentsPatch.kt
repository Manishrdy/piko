/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.restoreSearchRecents

import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.addFlags
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val restoreSearchRecentsPatch =
    bytecodePatch(
        name = "Restore classic search recents",
        description = "Restores the old search with up to 25 recent profiles, replacing Meta AI suggestions and collapsed recents.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(hookFlagsPatch)
        execute {
            addFlags("restoreSearchRecentsFlags")
        }
    }
