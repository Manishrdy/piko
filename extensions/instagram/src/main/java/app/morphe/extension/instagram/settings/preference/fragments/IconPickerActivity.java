/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.fragments;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

import java.util.List;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.patches.appIcon.AppIconManager;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.shared.Utils;

// Lives under settings.preference.fragments for the same reason HistoryActivity does --
// MaterialYouTheme classifies "piko settings activities" by the
// app.morphe.extension.instagram.settings. package prefix, and only that classification
// skips overwriting the app-wide observed dark theme state with this activity's reading.
//
// Extends the platform Activity, not AppCompatActivity: the patch gives this screen its
// own Theme.DeviceDefault.NoActionBar, and AppCompat refuses to start under a
// non-AppCompat theme.
public class IconPickerActivity extends Activity {

    private static final int COLUMNS = 3;

    private LinearLayout grid;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(InstagramPreferenceStyle.backgroundColor());

        applySystemBarStyle();

        View content = buildGrid();

        root.addView(buildToolbar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                InstagramPreferenceStyle.dp(this, 70)
        ));
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);

                content.setPadding(
                        content.getPaddingLeft(),
                        content.getPaddingTop(),
                        content.getPaddingRight(),
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
        title.setText(str("piko_app_icon_title"));
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

    private View buildGrid() {
        ScrollView scrollView = new ScrollView(this);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int sidePadding = InstagramPreferenceStyle.dp(this, 10);
        container.setPadding(sidePadding, InstagramPreferenceStyle.dp(this, 6), sidePadding, InstagramPreferenceStyle.dp(this, 20));

        TextView hint = new TextView(this);
        hint.setText(str("piko_app_icon_hint"));
        hint.setTextColor(InstagramPreferenceStyle.secondaryTextColor());
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        int hintPadding = InstagramPreferenceStyle.dp(this, 8);
        hint.setPadding(hintPadding, hintPadding, hintPadding, InstagramPreferenceStyle.dp(this, 14));
        container.addView(hint);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        container.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        populateGrid();

        scrollView.addView(container, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return scrollView;
    }

    private void populateGrid() {
        grid.removeAllViews();

        List<AppIconManager.Icon> icons;
        try {
            icons = AppIconManager.listIcons();
        } catch (Exception e) {
            PikoUtils.logger(e);
            return;
        }

        PackageManager pm = getPackageManager();
        LinearLayout row = null;

        for (int i = 0; i < icons.size(); i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));
            }
            row.addView(buildCell(icons.get(i), pm));
        }

        // Keep the last row's cells the same width as a full row's.
        int remainder = icons.size() % COLUMNS;
        if (row != null && remainder != 0) {
            for (int i = remainder; i < COLUMNS; i++) {
                View filler = new View(this);
                row.addView(filler, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f
                ));
            }
        }
    }

    private View buildCell(AppIconManager.Icon icon, PackageManager pm) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        int cellPadding = InstagramPreferenceStyle.dp(this, 12);
        cell.setPadding(cellPadding, cellPadding, cellPadding, cellPadding);
        cell.setBackground(cellBackground(icon.selected));

        LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        int margin = InstagramPreferenceStyle.dp(this, 4);
        cellParams.setMargins(margin, margin, margin, margin);
        cell.setLayoutParams(cellParams);

        ImageView preview = new ImageView(this);
        try {
            preview.setImageDrawable(icon.loadIcon(pm));
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        int previewSize = InstagramPreferenceStyle.dp(this, 62);
        preview.setLayoutParams(new LinearLayout.LayoutParams(previewSize, previewSize));

        TextView label = new TextView(this);
        label.setText(icon.isDefault ? str("piko_app_icon_default") : icon.label);
        label.setTextColor(icon.selected
                ? InstagramPreferenceStyle.primaryTextColor()
                : InstagramPreferenceStyle.secondaryTextColor());
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setPadding(0, InstagramPreferenceStyle.dp(this, 8), 0, 0);

        cell.addView(preview);
        cell.addView(label);

        cell.setOnClickListener(v -> onIconChosen(icon));
        return cell;
    }

    private GradientDrawable cellBackground(boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(InstagramPreferenceStyle.dp(this, 12));
        background.setColor(Color.TRANSPARENT);
        if (selected) {
            background.setStroke(
                    InstagramPreferenceStyle.dp(this, 2),
                    InstagramPreferenceStyle.primaryTextColor()
            );
        }
        return background;
    }

    private void onIconChosen(AppIconManager.Icon icon) {
        if (icon.selected) {
            return;
        }
        try {
            AppIconManager.applyIcon(icon.component);
            // Launchers pick the new icon up asynchronously, and some only redraw it on
            // their next refresh, so say so rather than letting it look like nothing happened.
            Utils.showToastShort(str("piko_app_icon_applied"));
        } catch (Exception e) {
            PikoUtils.logger(e);
            Utils.showToastShort(e.toString());
        }
        populateGrid();
    }
}
