/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.settings.preference.fragments;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.patches.followerTracker.FollowerTrackerDiffEngine;
import app.morphe.extension.instagram.patches.followerTracker.FollowerTrackerSharedPref;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;

// Lives under settings.preference.fragments (not patches.followerTracker) --
// MaterialYouTheme classifies "piko settings activities" by package prefix,
// and only that classification skips overwriting the app-wide observed dark
// theme state with this activity's own reading. Placing this activity
// outside that prefix caused a real crash (NPE unboxing a null Boolean)
// once the underlying activity got marked stale and recreated -- matching
// BackupPrefActivity/RestorePrefActivity's location avoids it.
public class HistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

        root.addView(buildToolbar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                InstagramPreferenceStyle.dp(this, 70)
        ));
        root.addView(buildEventList(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);
    }

    private LinearLayout buildToolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());
        int padding = InstagramPreferenceStyle.dp(this, 15);
        toolbar.setPadding(padding, InstagramPreferenceStyle.dp(this, 10), padding, InstagramPreferenceStyle.dp(this, 8));

        int iconSize = InstagramPreferenceStyle.dp(this, 44);
        ImageView back = new ImageView(this);
        UI.setThemedIcon(back, UI.DRAWABLE_ARROW_BACK);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setPaddingRelative(0, 0, InstagramPreferenceStyle.dp(this, 16), 0);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        backParams.gravity = Gravity.CENTER_VERTICAL;
        back.setLayoutParams(backParams);
        back.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText(str("piko_follower_tracker_history_title"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        title.setMaxLines(1);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        titleParams.gravity = Gravity.CENTER_VERTICAL;
        titleParams.leftMargin = InstagramPreferenceStyle.dp(this, 7);
        title.setLayoutParams(titleParams);
        title.setTextColor(InstagramPreferenceStyle.primaryTextColor());

        toolbar.addView(back);
        toolbar.addView(title);
        return toolbar;
    }

    private View buildEventList() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int rowPadding = InstagramPreferenceStyle.dp(this, 15);
        list.setPadding(rowPadding, InstagramPreferenceStyle.dp(this, 10), rowPadding, InstagramPreferenceStyle.dp(this, 10));

        JSONArray events = FollowerTrackerSharedPref.getEventLogRaw();
        if (events.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText(str("piko_follower_tracker_history_empty"));
            empty.setTextColor(InstagramPreferenceStyle.primaryTextColor());
            empty.setPadding(0, InstagramPreferenceStyle.dp(this, 20), 0, 0);
            list.addView(empty);
        } else {
            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
            // Newest first.
            for (int i = events.length() - 1; i >= 0; i--) {
                try {
                    JSONObject event = events.getJSONObject(i);
                    list.addView(buildRow(event, dateFormat, timeFormat));
                } catch (Exception e) {
                    PikoUtils.logger(e);
                }
            }
        }

        scrollView.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return scrollView;
    }

    private TextView buildRow(JSONObject event, DateFormat dateFormat, DateFormat timeFormat) throws Exception {
        String type = event.getString("type");
        String listType = event.getString("listType");
        String username = event.getString("username");
        long timestamp = event.getLong("timestamp");
        Date date = new Date(timestamp);

        String actionKey = type.equals("FOLLOW")
                ? (listType.equals(FollowerTrackerDiffEngine.TYPE_FOLLOWERS)
                    ? "piko_follower_tracker_event_new_follower"
                    : "piko_follower_tracker_event_new_following")
                : (listType.equals(FollowerTrackerDiffEngine.TYPE_FOLLOWERS)
                    ? "piko_follower_tracker_event_unfollowed_you"
                    : "piko_follower_tracker_event_unfollowed");

        TextView row = new TextView(this);
        row.setText(str(actionKey, username, dateFormat.format(date), timeFormat.format(date)));
        row.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        row.setPadding(0, InstagramPreferenceStyle.dp(this, 10), 0, InstagramPreferenceStyle.dp(this, 10));
        return row;
    }
}
