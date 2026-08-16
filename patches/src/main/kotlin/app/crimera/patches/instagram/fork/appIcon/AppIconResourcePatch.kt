/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.fork.appIcon

import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.ResourceGroup
import app.morphe.util.childElementsSequence
import app.morphe.util.copyResources
import org.w3c.dom.Element

/**
 * Adds one launcher activity-alias per bundled icon, and marks Instagram's own launcher
 * alias so the runtime picker can tell which entry is the stock icon.
 *
 * Instagram's launcher entry is already an activity-alias, and the app ships more of them
 * (disabled) for its "aura" icon set. Rather than hand-writing a new alias, each one here
 * is a clone of the stock launcher alias with only the name, icon and enabled state
 * changed. That copies across the launch attributes, the shortcuts meta-data and -- most
 * importantly -- the instagram:// deeplink intent-filters, so switching to a bundled icon
 * cannot quietly break deeplinks the way a hand-built MAIN/LAUNCHER-only alias would.
 */
val appIconResourcePatch =
    resourcePatch {
        execute {
            if (bundledIcons.isNotEmpty()) {
                val sourceDir = "instagram/appicons"

                ICON_DENSITIES.forEach { density ->
                    copyResources(
                        sourceDir,
                        ResourceGroup(
                            "mipmap-$density",
                            *bundledIcons
                                .flatMap {
                                    listOf(
                                        "piko_icon_${it}_foreground.webp",
                                        "piko_icon_${it}_background.webp",
                                    )
                                }.toTypedArray(),
                        ),
                    )
                }

                copyResources(
                    sourceDir,
                    ResourceGroup(
                        "mipmap-anydpi-v26",
                        *bundledIcons.map { "piko_icon_$it.xml" }.toTypedArray(),
                    ),
                )
            }

            document("AndroidManifest.xml").use { document ->
                val application = document.getElementsByTagName("application").item(0) as Element

                val stockLauncherAlias =
                    application
                        .childElementsSequence()
                        .filter { it.tagName == "activity-alias" }
                        // The stock icon is the one launcher alias that is not shipped
                        // disabled; every alternate icon Instagram declares, and every
                        // alias added below, sets android:enabled="false".
                        .filter { it.getAttribute("android:enabled") != "false" }
                        .firstOrNull { it.hasLauncherIntentFilter() }
                        ?: error("Instagram's launcher activity-alias was not found")

                var insertAfter: Element = stockLauncherAlias

                // Cloned before the marker below is attached, so no clone inherits it.
                bundledIcons.forEach { slug ->
                    val alias = stockLauncherAlias.cloneNode(true) as Element
                    val icon = "@mipmap/piko_icon_$slug"

                    alias.setAttribute("android:name", "$PIKO_ALIAS_PREFIX$slug")
                    alias.setAttribute("android:enabled", "false")
                    alias.setAttribute("android:exported", "true")
                    alias.setAttribute("android:icon", icon)
                    alias.setAttribute("android:roundIcon", icon)
                    // android:label is deliberately left as the clone inherited it. The
                    // launcher draws it under the icon, so it has to stay the app's name.

                    application.insertBefore(alias, insertAfter.nextSibling)
                    insertAfter = alias
                }

                // The picker reads this back to label the stock icon and to know what to
                // fall back to. Deriving it from ActivityInfo.enabled instead would rest
                // on whether that field reports the manifest declaration or the current
                // override for an alias, which is not worth depending on.
                stockLauncherAlias.appendChild(
                    document.createElement("meta-data").apply {
                        setAttribute("android:name", DEFAULT_ICON_MARKER)
                        setAttribute("android:value", "true")
                    },
                )
            }
        }
    }

private fun Element.hasLauncherIntentFilter(): Boolean =
    childElementsSequence()
        .filter { it.tagName == "intent-filter" }
        .any { intentFilter ->
            val children = intentFilter.childElementsSequence().toList()
            children.any { it.tagName == "action" && it.getAttribute("android:name") == "android.intent.action.MAIN" } &&
                children.any {
                    it.tagName == "category" && it.getAttribute("android:name") == "android.intent.category.LAUNCHER"
                }
        }
