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
 * Points some of Instagram's own alternate-icon aliases at the bundled artwork.
 *
 * Instagram's launcher entry is an activity-alias, and the app ships thirteen more
 * (all android:enabled="false") for its "aura" icon set. This patch reuses those rather
 * than adding aliases of its own: it only rewrites android:icon on aliases that already
 * exist, so the set of launcher components is exactly the one stock Instagram declares.
 *
 * That is deliberate. An earlier version cloned the stock alias once per bundled icon and
 * set android:enabled="false" on each clone. Whenever that attribute failed to take
 * effect, every added alias defaulted to enabled and the launcher showed one entry per
 * bundled icon -- five icons for four bundled ones. Adding no components makes that
 * failure unreachable: the patched app cannot present more launcher entries than the
 * unpatched app does, whatever happens to any single attribute.
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

                val launcherAliases =
                    application
                        .childElementsSequence()
                        .filter { it.tagName == "activity-alias" }
                        .filter { it.hasLauncherIntentFilter() }
                        // Instagram's internal/debug launcher carries MAIN+LAUNCHER too,
                        // but it is not an icon choice.
                        .filterNot { it.getAttribute("android:name").endsWith(".InternalLauncher") }
                        .toList()

                // The stock icon is the only launcher alias not shipped disabled.
                val stockAlias =
                    launcherAliases.firstOrNull { it.getAttribute("android:enabled") != "false" }
                        ?: error("Instagram's launcher activity-alias was not found")

                val alternates = launcherAliases.filter { it !== stockAlias }
                check(alternates.size >= bundledIcons.size) {
                    "Instagram declares ${alternates.size} alternate icon aliases, " +
                        "which is fewer than the ${bundledIcons.size} bundled icons"
                }

                bundledIcons.forEachIndexed { index, slug ->
                    val icon = "@mipmap/piko_icon_$slug"
                    alternates[index].apply {
                        setAttribute("android:icon", icon)
                        setAttribute("android:roundIcon", icon)
                        // The picker reads this for the row label. The alias keeps
                        // Instagram's own name, so the name is no longer a usable label.
                        appendChild(
                            document.createElement("meta-data").apply {
                                setAttribute("android:name", ICON_LABEL_MARKER)
                                setAttribute("android:value", slug.toDisplayLabel())
                            },
                        )
                    }
                }

                stockAlias.appendChild(
                    document.createElement("meta-data").apply {
                        setAttribute("android:name", DEFAULT_ICON_MARKER)
                        setAttribute("android:value", "true")
                    },
                )

                // The invariant the whole feature rests on. Getting this wrong is what
                // put duplicate icons in the app drawer, and it is silent at patch time
                // and expensive to notice on a device, so fail the build instead.
                val enabledLaunchers =
                    application
                        .childElementsSequence()
                        .filter { it.tagName == "activity-alias" || it.tagName == "activity" }
                        .filter { it.hasLauncherIntentFilter() }
                        .filter { it.getAttribute("android:enabled") != "false" }
                        .toList()
                check(enabledLaunchers.size == 1) {
                    "expected exactly one enabled launcher component, found " +
                        enabledLaunchers.size + ": " +
                        enabledLaunchers.joinToString { it.getAttribute("android:name") }
                }
            }
        }
    }

/** "dark_gradient" -> "Dark Gradient" */
private fun String.toDisplayLabel(): String =
    split('_')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

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
