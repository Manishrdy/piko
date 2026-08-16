/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.settings.preference.fragments;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.patches.followerTracker.FollowerListEntry;
import app.morphe.extension.instagram.patches.followerTracker.FollowerTrackerDiagnostics;
import app.morphe.extension.instagram.patches.followerTracker.FollowerTrackerDiffEngine;
import app.morphe.extension.instagram.patches.followerTracker.FollowerTrackerSharedPref;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.utils.Pref;

// Lives under settings.preference.fragments (not patches.followerTracker) --
// MaterialYouTheme classifies "piko settings activities" by package prefix,
// and only that classification skips overwriting the app-wide observed dark
// theme state with this activity's own reading. Placing this activity
// outside that prefix caused a real crash (NPE unboxing a null Boolean)
// once the underlying activity got marked stale and recreated -- matching
// BackupPrefActivity/RestorePrefActivity's location avoids it.
//
// Extends the platform Activity rather than AppCompatActivity because the
// manifest gives this screen its own Theme.DeviceDefault.NoActionBar, the
// same theme SettingsActivity uses -- AppCompat refuses to start under a
// non-AppCompat theme. Both are needed together: building views against
// Instagram's inherited application theme is what crashed this screen.
public class HistoryActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

        applySystemBarStyle();

        View eventList = buildEventList();

        root.addView(buildToolbar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                InstagramPreferenceStyle.dp(this, 70)
        ));
        root.addView(eventList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);

                eventList.setPadding(
                eventList.getPaddingLeft(),
                eventList.getPaddingTop(),
                eventList.getPaddingRight(),
                insets.getSystemWindowInsetBottom()
                );

                return insets;
            }
        });

        setContentView(root);
    }

    private void applySystemBarStyle() {
        getWindow().setStatusBarColor(InstagramPreferenceStyle.backgroundColor());
        getWindow().setNavigationBarColor(InstagramPreferenceStyle.backgroundColor());

        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (UI.isDarkMode()) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
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

        DateFormat capturedDateFormat = android.text.format.DateFormat.getDateFormat(this);
        DateFormat capturedTimeFormat = android.text.format.DateFormat.getTimeFormat(this);
        buildDiagnosticsSection(list);
        buildCapturedSection(list, capturedDateFormat, capturedTimeFormat);

        list.addView(buildSectionHeader(str("piko_follower_tracker_section_activity")));

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

    // Whether the injected hooks are running at all. Sits above everything else
    // because it is the only section with anything to say when capture is
    // broken, which is exactly when this screen gets opened in anger.
    private void buildDiagnosticsSection(LinearLayout list) {
        list.addView(buildSectionHeader(str("piko_follower_tracker_section_diagnostics")));

        TextView toggle = new TextView(this);
        toggle.setText(str("piko_follower_tracker_diag_toggle",
                Pref.followerTrackerEnabled() ? "on" : "off"));
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        toggle.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        list.addView(toggle);

        TextView probes = new TextView(this);
        probes.setText(String.join("\n", FollowerTrackerDiagnostics.summaryLines()));
        probes.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        probes.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
        probes.setPadding(0, InstagramPreferenceStyle.dp(this, 6), 0, InstagramPreferenceStyle.dp(this, 4));
        list.addView(probes);
    }

    // What actually got recorded, as opposed to what changed. Exists mainly to
    // answer "is capture working at all", so it reports the raw list type keys
    // Instagram handed us rather than mapping them onto the followers/following
    // constants -- if a key here reads as anything else, that mapping is wrong.
    private void buildCapturedSection(LinearLayout list, DateFormat dateFormat, DateFormat timeFormat) {
        list.addView(buildSectionHeader(str("piko_follower_tracker_section_captured")));

        List<String> listTypes = FollowerTrackerSharedPref.getTrackedListTypes();
        if (listTypes.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(str("piko_follower_tracker_captured_none"));
            none.setTextColor(InstagramPreferenceStyle.primaryTextColor());
            none.setPadding(0, InstagramPreferenceStyle.dp(this, 10), 0, InstagramPreferenceStyle.dp(this, 10));
            list.addView(none);
            return;
        }

        for (String listType : listTypes) {
            list.addView(buildCapturedRow(listType, dateFormat, timeFormat));
        }

        TextView hint = new TextView(this);
        hint.setText(str("piko_follower_tracker_captured_hint"));
        hint.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setPadding(0, InstagramPreferenceStyle.dp(this, 4), 0, InstagramPreferenceStyle.dp(this, 10));
        list.addView(hint);
    }

    private View buildCapturedRow(String listType, DateFormat dateFormat, DateFormat timeFormat) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, InstagramPreferenceStyle.dp(this, 10), 0, InstagramPreferenceStyle.dp(this, 10));

        Map<String, FollowerListEntry> baseline = FollowerTrackerSharedPref.getBaseline(listType);
        long capturedAt = FollowerTrackerSharedPref.getCapturedAt(listType);

        TextView title = new TextView(this);
        title.setText(listType);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(InstagramPreferenceStyle.primaryTextColor());

        TextView summary = new TextView(this);
        if (capturedAt > 0) {
            Date capturedDate = new Date(capturedAt);
            summary.setText(str(
                    "piko_follower_tracker_captured_summary",
                    baseline.size(),
                    dateFormat.format(capturedDate),
                    timeFormat.format(capturedDate)
            ));
        } else {
            summary.setText(str("piko_follower_tracker_captured_summary_no_time", baseline.size()));
        }
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        summary.setTextColor(InstagramPreferenceStyle.secondaryTextColor());

        // One view holding every name, not one view per name -- a full list can
        // run to thousands of entries, and that many child views would make the
        // screen crawl for no benefit.
        TextView names = new TextView(this);
        names.setText(String.join("\n", usernamesOf(baseline)));
        names.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        names.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
        names.setPadding(0, InstagramPreferenceStyle.dp(this, 8), 0, 0);
        names.setVisibility(View.GONE);

        container.addView(title);
        container.addView(summary);
        container.addView(names);
        container.setOnClickListener(v -> names.setVisibility(
                names.getVisibility() == View.GONE ? View.VISIBLE : View.GONE
        ));
        return container;
    }

    private static List<String> usernamesOf(Map<String, FollowerListEntry> baseline) {
        List<String> usernames = new ArrayList<>(baseline.size());
        for (FollowerListEntry entry : baseline.values()) {
            usernames.add(entry.username);
        }
        return usernames;
    }

    private TextView buildSectionHeader(String text) {
        TextView header = new TextView(this);
        header.setText(text);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
        header.setPadding(0, InstagramPreferenceStyle.dp(this, 14), 0, InstagramPreferenceStyle.dp(this, 4));
        return header;
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
