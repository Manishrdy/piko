/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.settings

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

val addSettingsActivityPatch =
    resourcePatch(
        description = "Adds SettingsActivity to the Android manifest.",
    ) {
        finalize {
            document("AndroidManifest.xml").use { document ->

                val application = document.getElementsByTagName("application").item(0) as Element

                var activity = document.createElement("activity")
                activity.setAttribute("android:name", "app.morphe.extension.instagram.settings.SettingsActivity")
                activity.setAttribute("android:label", "Settings")
                activity.setAttribute("android:theme", "@android:style/Theme.DeviceDefault.NoActionBar")
                activity.setAttribute("android:exported", "false")
                application.appendChild(activity)

                val service = document.createElement("service")
                service.setAttribute(
                    "android:name",
                    "app.morphe.extension.instagram.settings.SettingsTaskService",
                )
                service.setAttribute("android:exported", "false")
                service.setAttribute("android:stopWithTask", "false")
                application.appendChild(service)

                // These build no views of their own, so Instagram's application theme
                // (inherited when no android:theme is set) is good enough for them.
                listOf(
                    "app.morphe.extension.instagram.settings.preference.fragments.BackupPrefActivity",
                    "app.morphe.extension.instagram.settings.preference.fragments.RestorePrefActivity",
                    "app.morphe.extension.crimera.downloader.FolderPickerActivity",
                ).forEach { activityName ->
                    activity = document.createElement("activity")
                    activity.setAttribute("android:name", activityName)
                    activity.setAttribute("android:exported", "false")
                    application.appendChild(activity)
                }

                // These build their own views, so they need the same explicit platform theme
                // SettingsActivity uses. Inheriting Theme.Instagram.Splash instead crashed
                // constructing a plain TextView: that theme's default text appearance points
                // at an Instagram attribute which does not always resolve, and an unresolved
                // attribute makes TypedArray.getColorStateList throw.
                listOf(
                    "app.morphe.extension.instagram.settings.preference.fragments.IconPickerActivity",
                ).forEach { activityName ->
                    activity = document.createElement("activity")
                    activity.setAttribute("android:name", activityName)
                    activity.setAttribute("android:theme", "@android:style/Theme.DeviceDefault.NoActionBar")
                    activity.setAttribute("android:exported", "false")
                    application.appendChild(activity)
                }
            }
        }
    }
