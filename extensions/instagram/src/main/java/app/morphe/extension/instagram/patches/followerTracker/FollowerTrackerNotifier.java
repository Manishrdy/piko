/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.followerTracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.List;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.settings.SettingsActivity;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.shared.Utils;

import static app.morphe.extension.instagram.utils.IgStr.str;

public class FollowerTrackerNotifier {
    private static final String CHANNEL_ID = "follower_tracker_channel";

    public static void notifyChanges(String listType, List<String> newUsernames, List<String> goneUsernames) {
        try {
            if (newUsernames.isEmpty() && goneUsernames.isEmpty()) return;

            Context context = Utils.getContext();
            if (context == null) return;
            Context applicationContext = context.getApplicationContext();
            if (applicationContext != null) context = applicationContext;

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        str("piko_category_follower_tracker"),
                        NotificationManager.IMPORTANCE_LOW
                );
                notificationManager.createNotificationChannel(channel);
            }

            String title = listType.equals(FollowerTrackerDiffEngine.TYPE_FOLLOWERS)
                    ? str("piko_follower_tracker_notif_title_followers")
                    : str("piko_follower_tracker_notif_title_following");
            String text = buildSummary(newUsernames, goneUsernames);

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(context, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(context);
            }
            builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setContentIntent(historyIntent(context));

            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

    private static android.app.PendingIntent historyIntent(Context context) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(Constants.PIKO_FRAGMENT_NAME, Constants.PIKO_FRAGMENT_FOLLOWER_TRACKER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
        }
        return android.app.PendingIntent.getActivity(context, 0, intent, flags);
    }

    private static String buildSummary(List<String> newUsernames, List<String> goneUsernames) {
        StringBuilder sb = new StringBuilder();
        if (!newUsernames.isEmpty()) {
            sb.append(str("piko_follower_tracker_notif_new", newUsernames.size(), String.join(", ", newUsernames)));
        }
        if (!goneUsernames.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(str("piko_follower_tracker_notif_gone", goneUsernames.size(), String.join(", ", goneUsernames)));
        }
        return sb.toString();
    }
}
