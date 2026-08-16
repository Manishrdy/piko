/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.followerTracker;

import org.json.JSONObject;

import app.morphe.extension.crimera.PikoUtils;

// Records whether the tracker's two injected hooks are actually firing, so an
// empty history screen can say *which* link in the chain broke instead of just
// showing nothing. Both hooks are silent by design when anything goes wrong --
// a null list type, a parse that yields no rows, a disabled toggle -- and all
// three look identical from the outside.
//
// Counters live in memory and are cheap enough to keep even when the tracker is
// switched off, which is the point: it distinguishes "hook never fired" from
// "hook fired but the feature was off". Persisting is the selective part --
// always for screen opens (rare), only when enabled for parses (frequent), so
// a user who never turns the feature on never pays for disk writes.
public final class FollowerTrackerDiagnostics {

    private static volatile int screenOpenedCount;
    private static volatile String lastListType = "<none>";
    private static volatile boolean lastEnabled;
    private static volatile int parsedCount;
    private static volatile String lastParsedClass = "<none>";
    private static volatile int lastEntryCount = -1;
    private static volatile int finalizedCount;
    private static volatile int lastSavedCount = -1;
    private static volatile String lastError = "";

    private FollowerTrackerDiagnostics() {
    }

    static void recordScreenOpened(String listType, boolean enabled) {
        screenOpenedCount++;
        lastListType = listType == null ? "<null>" : listType;
        lastEnabled = enabled;
        persist();
    }

    static void recordParsed(Object parsedResult, int entryCount, boolean enabled) {
        parsedCount++;
        lastParsedClass = parsedResult == null
                ? "<null>"
                : parsedResult.getClass().getName();
        lastEntryCount = entryCount;
        lastEnabled = enabled;
        if (enabled) persist();
    }

    static void recordFinalized(int savedCount) {
        finalizedCount++;
        lastSavedCount = savedCount;
        persist();
    }

    static void recordError(Throwable error) {
        if (error == null) return;
        String message = error.getClass().getSimpleName();
        if (error.getMessage() != null) {
            message = message + ": " + error.getMessage();
        }
        lastError = message;
        persist();
    }

    /** Human readable lines for the history screen, newest state first. */
    public static String[] summaryLines() {
        JSONObject stored = load();
        return new String[]{
                "list screen hook: " + value(stored, "screenOpened", screenOpenedCount)
                        + "x, last type: " + value(stored, "lastListType", lastListType),
                "parser hook: " + value(stored, "parsed", parsedCount)
                        + "x, last extracted: " + value(stored, "lastEntryCount", lastEntryCount),
                "parsed class: " + value(stored, "lastParsedClass", lastParsedClass),
                "saved: " + value(stored, "finalized", finalizedCount)
                        + "x, last size: " + value(stored, "lastSavedCount", lastSavedCount),
                "enabled at last hook: " + value(stored, "enabled", lastEnabled),
                "last error: " + emptyAsNone(String.valueOf(value(stored, "lastError", lastError))),
        };
    }

    // In-memory wins when this process saw the hook itself; the stored value is
    // the fallback for a fresh process that only has what a previous run left.
    private static Object value(JSONObject stored, String key, Object live) {
        boolean liveIsUnset = live == null
                || live.equals(0)
                || live.equals(-1)
                || "".equals(live)
                || "<none>".equals(live);
        if (!liveIsUnset) return live;
        if (stored == null) return live;
        return stored.opt(key) == null ? live : stored.opt(key);
    }

    private static String emptyAsNone(String value) {
        return value == null || value.isEmpty() || "null".equals(value) ? "none" : value;
    }

    private static void persist() {
        try {
            JSONObject snapshot = new JSONObject();
            snapshot.put("screenOpened", screenOpenedCount);
            snapshot.put("lastListType", lastListType);
            snapshot.put("parsed", parsedCount);
            snapshot.put("lastParsedClass", lastParsedClass);
            snapshot.put("lastEntryCount", lastEntryCount);
            snapshot.put("finalized", finalizedCount);
            snapshot.put("lastSavedCount", lastSavedCount);
            snapshot.put("enabled", lastEnabled);
            snapshot.put("lastError", lastError);
            FollowerTrackerSharedPref.saveDiagnostics(snapshot.toString());
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

    private static JSONObject load() {
        try {
            String raw = FollowerTrackerSharedPref.getDiagnostics();
            if (raw.isEmpty()) return null;
            return new JSONObject(raw);
        } catch (Exception e) {
            PikoUtils.logger(e);
            return null;
        }
    }
}
