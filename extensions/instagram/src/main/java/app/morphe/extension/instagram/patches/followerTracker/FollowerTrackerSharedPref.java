/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.followerTracker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.crimera.sharedPreference.BaseSharedPref;
import app.morphe.extension.instagram.constants.Constants;

public class FollowerTrackerSharedPref extends BaseSharedPref {

    private static final FollowerTrackerSharedPref INSTANCE = new FollowerTrackerSharedPref();
    // SharedPreferences isn't meant to hold unbounded growth -- oldest events
    // are dropped once the log passes this size.
    private static final int MAX_EVENTS = 500;
    private static final String EVENT_LOG_KEY = "event_log";
    private static final String DIAGNOSTICS_KEY = "diagnostics";

    public FollowerTrackerSharedPref() {
        super(Constants.PIKO + "_follower_tracker");
    }

    private static final String BASELINE_PREFIX = "baseline_";
    private static final String CAPTURED_AT_PREFIX = "captured_at_";

    private static String baselineKey(String listType) {
        return BASELINE_PREFIX + listType;
    }

    private static String capturedAtKey(String listType) {
        return CAPTURED_AT_PREFIX + listType;
    }

    // Every list type with a stored baseline, as the raw string Instagram
    // handed us. Deliberately not mapped onto TYPE_FOLLOWERS/TYPE_FOLLOWING --
    // those constants have never been confirmed against a real run, so the
    // history screen shows whatever actually arrived.
    public static List<String> getTrackedListTypes() {
        List<String> listTypes = new ArrayList<>();
        try {
            if (INSTANCE.sp == null) return listTypes;
            for (String key : INSTANCE.sp.preferences.getAll().keySet()) {
                if (key.startsWith(BASELINE_PREFIX)) {
                    listTypes.add(key.substring(BASELINE_PREFIX.length()));
                }
            }
            Collections.sort(listTypes);
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        return listTypes;
    }

    /** @return when this list was last captured, or 0 if that was never recorded. */
    public static long getCapturedAt(String listType) {
        try {
            String raw = INSTANCE.getString(capturedAtKey(listType), "");
            if (raw.isEmpty()) return 0L;
            return Long.parseLong(raw);
        } catch (Exception e) {
            PikoUtils.logger(e);
            return 0L;
        }
    }

    // Distinguishes "never captured this list before" from "captured it and it
    // was empty" -- the first visit seeds the baseline silently instead of
    // reporting every existing follower as brand new.
    public static boolean hasBaseline(String listType) {
        return !INSTANCE.getString(baselineKey(listType), "").isEmpty();
    }

    public static Map<String, FollowerListEntry> getBaseline(String listType) {
        Map<String, FollowerListEntry> result = new LinkedHashMap<>();
        try {
            String raw = INSTANCE.getString(baselineKey(listType), "");
            if (raw.isEmpty()) return result;
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String userId = o.getString("userId");
                result.put(userId, new FollowerListEntry(userId, o.getString("username"), o.getInt("depth")));
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        return result;
    }

    public static void saveBaseline(String listType, Map<String, FollowerListEntry> baseline) {
        try {
            JSONArray arr = new JSONArray();
            for (FollowerListEntry entry : baseline.values()) {
                JSONObject o = new JSONObject();
                o.put("userId", entry.userId);
                o.put("username", entry.username);
                o.put("depth", entry.depth);
                arr.put(o);
            }
            INSTANCE.setString(baselineKey(listType), arr.toString());
            // Stamped here rather than at the call sites so every path that
            // rewrites a baseline -- silent first capture and later merges
            // alike -- records when it happened.
            INSTANCE.setString(capturedAtKey(listType), Long.toString(System.currentTimeMillis()));
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

    // Writes a whole visit's worth of events in one read/serialize/store cycle.
    // Doing this per username instead re-parsed and rewrote the entire log once
    // for every name, which is a lot of main thread work on a long list.
    public static void appendEvents(String listType, List<String> newUsernames, List<String> goneUsernames) {
        try {
            JSONArray events = getEventLogRaw();
            long timestamp = System.currentTimeMillis();

            for (String username : newUsernames) {
                events.put(buildEvent("FOLLOW", listType, username, timestamp));
            }
            for (String username : goneUsernames) {
                events.put(buildEvent("UNFOLLOW", listType, username, timestamp));
            }

            if (events.length() > MAX_EVENTS) {
                JSONArray trimmed = new JSONArray();
                int dropCount = events.length() - MAX_EVENTS;
                for (int i = dropCount; i < events.length(); i++) {
                    trimmed.put(events.get(i));
                }
                events = trimmed;
            }

            INSTANCE.setString(EVENT_LOG_KEY, events.toString());
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

    private static JSONObject buildEvent(String type, String listType, String username, long timestamp) throws Exception {
        JSONObject event = new JSONObject();
        event.put("type", type);
        event.put("listType", listType);
        event.put("username", username);
        event.put("timestamp", timestamp);
        return event;
    }

    public static void saveDiagnostics(String snapshot) {
        try {
            INSTANCE.setString(DIAGNOSTICS_KEY, snapshot);
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

    public static String getDiagnostics() {
        try {
            return INSTANCE.getString(DIAGNOSTICS_KEY, "");
        } catch (Exception e) {
            PikoUtils.logger(e);
            return "";
        }
    }

    public static JSONArray getEventLogRaw() {
        try {
            String raw = INSTANCE.getString(EVENT_LOG_KEY, "");
            if (raw.isEmpty()) return new JSONArray();
            return new JSONArray(raw);
        } catch (Exception e) {
            PikoUtils.logger(e);
            return new JSONArray();
        }
    }
}
