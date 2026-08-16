/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.appIcon;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.crimera.sharedPreference.SharedPref;

/**
 * Launcher icon switching, built on activity-aliases.
 *
 * Instagram's own launcher entry is already an alias
 * (com.instagram.android.activity.MainTabActivity -> InstagramMainActivity), and the app
 * ships thirteen more disabled aliases for its "aura" icon set. The patch appends one
 * alias per bundled piko icon in the same shape. Switching icons is therefore just
 * flipping which alias the package manager considers enabled.
 *
 * The list is discovered at runtime rather than hardcoded: querying MAIN/LAUNCHER with
 * MATCH_DISABLED_COMPONENTS returns every alias regardless of state. That keeps this
 * class working when Instagram adds or drops its own icons between versions, and it
 * survives the Clone patch renaming the package -- component names come back already
 * resolved against whatever package the app actually installed as.
 */
public final class AppIconManager {

    /**
     * Alias name prefix used by the "Change app icon" patch for icons piko bundles.
     * Deliberately outside the com.instagram.* namespace so the Clone patch, which
     * rewrites com.instagram.android in manifest attribute values, leaves it alone.
     */
    public static final String PIKO_ALIAS_PREFIX = "app.morphe.extension.instagram.appicon.";

    /**
     * Meta-data key the patch attaches to Instagram's own launcher alias.
     *
     * ActivityInfo.enabled would be the obvious way to spot the stock icon -- it is the
     * only launcher alias not declared android:enabled="false" -- but whether that field
     * reports the manifest declaration or the current runtime override for an alias is
     * not worth depending on, so the patch says so explicitly instead.
     */
    private static final String DEFAULT_ICON_MARKER = "app.morphe.piko.default_app_icon";

    /**
     * Instagram's internal/debug launcher. It carries MAIN+LAUNCHER and ships disabled,
     * so it shows up in the query, but it is not an icon choice.
     */
    private static final String INTERNAL_LAUNCHER_SUFFIX = ".InternalLauncher";

    /** Remembers the picked alias so a reinstall or update can put it back. */
    private static final String KEY_SELECTED_ICON = "piko_app_icon_selected";

    private AppIconManager() {
    }

    /**
     * Forces the invariant this feature depends on: exactly one launcher entry enabled.
     *
     * Selecting an icon flips two components, and anything that interrupts that -- a
     * failed binder call, a reinstall resetting overrides to the manifest defaults while
     * a stale pick is still stored -- can leave two of them enabled, which the launcher
     * shows as duplicate entries in the app drawer. Rather than trusting the flip to have
     * completed, every app start reasserts the intended state, so a duplicate can only
     * survive until the next launch.
     *
     * Cheap in the common case: one query returns the launcher entries that are *not*
     * disabled, and if that is already exactly the intended one this returns without
     * touching the package manager.
     */
    public static void reconcile() {
        try {
            Context context = PikoUtils.getContext();
            PackageManager pm = context.getPackageManager();

            List<Icon> all = listIcons();
            if (all.isEmpty()) {
                return;
            }

            ComponentName intended = intendedComponent(all);
            if (intended == null) {
                // Nothing stored and no alias carries the default marker. Leaving the
                // manifest's own state alone beats guessing, since guessing wrong here
                // means an app with no launcher entry at all.
                return;
            }

            List<ResolveInfo> live = pm.queryIntentActivities(launcherIntent(context), 0);
            if (live.size() == 1
                    && intended.getClassName().equals(live.get(0).activityInfo.name)) {
                return;
            }

            // Enable first: a window with nothing enabled would leave the app with no
            // way back onto the launcher if the process died mid-reconcile.
            setEnabled(pm, intended, true);
            for (Icon icon : all) {
                if (!icon.component.equals(intended)) {
                    setEnabled(pm, icon.component, false);
                }
            }
            PikoUtils.logger("App icon reconciled to " + intended.getClassName()
                    + " (was showing " + live.size() + " launcher entries)");
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

    /** The alias that should be the live one: the stored pick, else the stock icon. */
    private static ComponentName intendedComponent(List<Icon> icons) {
        String saved = SharedPref.getStringPref(KEY_SELECTED_ICON, "");

        if (saved != null && !saved.isEmpty()) {
            for (Icon icon : icons) {
                if (icon.component.getClassName().equals(saved)) {
                    return icon.component;
                }
            }
            // Stored pick is gone -- a bundled icon dropped from a later build, say.
            // Fall through to the stock icon rather than stranding the app.
        }

        for (Icon icon : icons) {
            if (icon.isDefault) {
                return icon.component;
            }
        }
        return null;
    }

    /** One selectable launcher icon. */
    public static final class Icon {
        public final ComponentName component;
        /** Human readable name derived from the alias, e.g. "Throwback". */
        public final String label;
        /** True for Instagram's stock icon (the only alias enabled in the manifest). */
        public final boolean isDefault;
        /** True for an icon bundled by piko rather than one Instagram already shipped. */
        public final boolean isPiko;
        public final boolean selected;

        private final ResolveInfo resolveInfo;

        private Icon(ComponentName component, String label, boolean isDefault,
                     boolean isPiko, boolean selected, ResolveInfo resolveInfo) {
            this.component = component;
            this.label = label;
            this.isDefault = isDefault;
            this.isPiko = isPiko;
            this.selected = selected;
            this.resolveInfo = resolveInfo;
        }

        public Drawable loadIcon(PackageManager pm) {
            return resolveInfo.loadIcon(pm);
        }
    }

    /**
     * Every launcher alias the installed app declares, stock icon first, then piko's
     * bundled icons, then Instagram's own alternates.
     */
    public static List<Icon> listIcons() {
        List<Icon> icons = new ArrayList<>();

        Context context = PikoUtils.getContext();
        PackageManager pm = context.getPackageManager();

        List<ResolveInfo> resolved = pm.queryIntentActivities(
                launcherIntent(context),
                PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.GET_META_DATA
        );

        for (ResolveInfo info : resolved) {
            ActivityInfo activityInfo = info.activityInfo;
            if (activityInfo == null || activityInfo.name == null) {
                continue;
            }
            if (activityInfo.name.endsWith(INTERNAL_LAUNCHER_SUFFIX)) {
                continue;
            }

            ComponentName component =
                    new ComponentName(activityInfo.packageName, activityInfo.name);
            boolean isDefault = activityInfo.metaData != null
                    && activityInfo.metaData.getBoolean(DEFAULT_ICON_MARKER, false);
            boolean isPiko = activityInfo.name.startsWith(PIKO_ALIAS_PREFIX);

            icons.add(new Icon(
                    component,
                    isDefault ? null : prettify(activityInfo.name),
                    isDefault,
                    isPiko,
                    isSelected(pm, component, isDefault),
                    info
            ));
        }

        // Stock icon first, then piko's, then Instagram's -- alphabetical within a group.
        Collections.sort(icons, new Comparator<Icon>() {
            @Override
            public int compare(Icon a, Icon b) {
                if (a.isDefault != b.isDefault) {
                    return a.isDefault ? -1 : 1;
                }
                if (a.isPiko != b.isPiko) {
                    return a.isPiko ? -1 : 1;
                }
                return String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label));
            }
        });

        return icons;
    }

    /**
     * Makes {@code target} the launcher icon.
     *
     * The target is enabled before the others are disabled. Doing it the other way round
     * leaves a window with no enabled MAIN/LAUNCHER component at all, during which the
     * app has no launcher entry to relaunch from if the process dies mid-switch.
     */
    public static void applyIcon(ComponentName target) {
        Context context = PikoUtils.getContext();
        PackageManager pm = context.getPackageManager();

        // Stored before the flip, so that even if disabling the outgoing alias fails the
        // next app start knows which one was meant to win and can finish the job.
        SharedPref.setStringPref(KEY_SELECTED_ICON, target.getClassName());

        setEnabled(pm, target, true);

        boolean allDisabled = true;
        for (Icon icon : listIcons()) {
            if (!icon.component.equals(target)) {
                allDisabled &= setEnabled(pm, icon.component, false);
            }
        }

        if (!allDisabled) {
            // Two launcher entries are live right now, which is what shows up as a
            // duplicate in the app drawer. Say so instead of letting it look like it
            // worked -- reconcile() will clear it on the next launch.
            PikoUtils.logger("App icon: failed to disable a previous alias, "
                    + "the duplicate clears on next app start");
        }
    }

    private static boolean setEnabled(PackageManager pm, ComponentName component, boolean enabled) {
        try {
            pm.setComponentEnabledSetting(
                    component,
                    enabled
                            ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
            );
            return true;
        } catch (Exception e) {
            PikoUtils.logger(e);
            return false;
        }
    }

    private static Intent launcherIntent(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(context.getPackageName());
        return intent;
    }

    private static boolean isSelected(PackageManager pm, ComponentName component, boolean isDefault) {
        try {
            switch (pm.getComponentEnabledSetting(component)) {
                case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                    return true;
                case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
                    return false;
                default:
                    // Never overridden, so the manifest declaration still stands.
                    return isDefault;
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
            return isDefault;
        }
    }

    /**
     * Derives a display name from an alias name, which is all we have to go on --
     * android:label is deliberately left alone on these aliases, because the launcher
     * shows it under the icon and it must stay the app's name, not the icon's.
     *
     * com.instagram.android.activity.MainTabActivity.throwback -> "Throwback"
     * app.morphe.extension.instagram.appicon.retro_glow        -> "Retro Glow"
     */
    private static String prettify(String aliasName) {
        String slug = aliasName.substring(aliasName.lastIndexOf('.') + 1);
        String[] words = slug.split("[_\\s]+");

        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0)));
            label.append(word.substring(1));
        }
        return label.length() == 0 ? slug : label.toString();
    }
}
