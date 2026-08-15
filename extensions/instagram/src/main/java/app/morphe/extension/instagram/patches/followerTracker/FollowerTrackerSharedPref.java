/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.followerTracker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
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

    public FollowerTrackerSharedPref() {
        super(Constants.PIKO + "_follower_tracker");
    }

    private static String baselineKey(String listType) {
        return "baseline_" + listType;
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
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

    public static void appendEvent(String type, String listType, String username) {
        try {
            JSONArray events = getEventLogRaw();
            JSONObject event = new JSONObject();
            event.put("type", type);
            event.put("listType", listType);
            event.put("username", username);
            event.put("timestamp", System.currentTimeMillis());
            events.put(event);

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
