/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.followerTracker;

import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.utils.Pref;

public class FollowerTrackerDiffEngine {

    public static final String TYPE_FOLLOWERS = "followers";
    public static final String TYPE_FOLLOWING = "following";

    // A "visit" is considered finished once no new page has arrived for this
    // long -- notifies/persists once per visit instead of once per page.
    // Generous on purpose: this timer cannot tell "backed out of the screen"
    // apart from "paused to read something", and firing early splits one long
    // scroll into several visits, each reporting the next chunk as new follows.
    private static final long VISIT_SETTLE_DELAY_MS = 15000;

    private static final Map<String, LinkedHashMap<String, FollowerListEntry>> visitAccumulators = new ConcurrentHashMap<>();
    private static final Map<String, Runnable> pendingFinalize = new ConcurrentHashMap<>();
    // List types whose current visit is the first ever capture. Held for the
    // whole visit rather than re-checked per finalize, so that a long first
    // scroll which settles part way through keeps seeding quietly instead of
    // reporting everything below that point as new follows.
    private static final Set<String> seedingVisits = ConcurrentHashMap.newKeySet();
    private static final Handler handler = new Handler(Looper.getMainLooper());

    private static volatile String currentListType = null;

    // Called from the Followers/Following fragment's entry point. Doubles as
    // the "which list is this parse for" context flag, since the response
    // parser class itself is a stateless singleton with nothing to read.
    public static void onListScreenOpened(String listType) {
        if (!isEnabled()) return;
        try {
            // Settle the previous visit before starting a new one, whichever
            // list it was for -- reopening the same list would otherwise wipe
            // an accumulator that still had a finalize pending against it.
            if (currentListType != null) {
                finalizeVisit(currentListType);
            }
            currentListType = listType;
            visitAccumulators.put(listType, new LinkedHashMap<>());
            if (FollowerTrackerSharedPref.hasBaseline(listType)) {
                seedingVisits.remove(listType);
            } else {
                seedingVisits.add(listType);
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

    // Called after each page of the followers/following list finishes parsing.
    public static void onListParsed(Object parsedResult) {
        if (!isEnabled()) return;
        try {
            String listType = currentListType;
            if (listType == null) return;

            List<FollowerListEntry> pageEntries = extractEntries(parsedResult);
            if (pageEntries.isEmpty()) return;

            LinkedHashMap<String, FollowerListEntry> visit =
                    visitAccumulators.computeIfAbsent(listType, k -> new LinkedHashMap<>());
            for (FollowerListEntry entry : pageEntries) {
                if (!visit.containsKey(entry.userId)) {
                    entry.depth = visit.size() + 1;
                    visit.put(entry.userId, entry);
                }
            }

            scheduleFinalize(listType);
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

    private static void scheduleFinalize(String listType) {
        Runnable previous = pendingFinalize.get(listType);
        if (previous != null) handler.removeCallbacks(previous);
        Runnable task = () -> finalizeVisit(listType);
        pendingFinalize.put(listType, task);
        handler.postDelayed(task, VISIT_SETTLE_DELAY_MS);
    }

    private static void finalizeVisit(String listType) {
        try {
            Runnable pending = pendingFinalize.remove(listType);
            if (pending != null) handler.removeCallbacks(pending);

            LinkedHashMap<String, FollowerListEntry> visit = visitAccumulators.get(listType);
            if (visit == null || visit.isEmpty()) return;

            // Nothing to compare against yet: capture the list quietly rather
            // than announcing every existing follower as a brand new one.
            if (seedingVisits.contains(listType) || !FollowerTrackerSharedPref.hasBaseline(listType)) {
                FollowerTrackerSharedPref.saveBaseline(listType, visit);
                return;
            }

            Map<String, FollowerListEntry> baseline = FollowerTrackerSharedPref.getBaseline(listType);

            List<String> newUsernames = new ArrayList<>();
            List<String> goneUsernames = new ArrayList<>();

            for (FollowerListEntry entry : visit.values()) {
                if (!baseline.containsKey(entry.userId)) {
                    newUsernames.add(entry.username);
                }
            }
            // Only declare someone gone if this visit scrolled at least as far
            // as where they were last confirmed present -- otherwise their
            // absence just means the user hasn't scrolled that far yet.
            for (FollowerListEntry old : baseline.values()) {
                if (old.depth <= visit.size() && !visit.containsKey(old.userId)) {
                    goneUsernames.add(old.username);
                }
            }

            mergeBack(listType, baseline, visit);

            if (newUsernames.isEmpty() && goneUsernames.isEmpty()) return;

            FollowerTrackerSharedPref.appendEvents(listType, newUsernames, goneUsernames);
            FollowerTrackerNotifier.notifyChanges(listType, newUsernames, goneUsernames);
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }

    private static void mergeBack(String listType, Map<String, FollowerListEntry> baseline, Map<String, FollowerListEntry> visit) {
        Map<String, FollowerListEntry> updated = new LinkedHashMap<>(baseline);
        for (FollowerListEntry entry : visit.values()) {
            updated.put(entry.userId, entry);
        }
        for (FollowerListEntry old : baseline.values()) {
            if (old.depth <= visit.size() && !visit.containsKey(old.userId)) {
                updated.remove(old.userId);
            }
        }
        FollowerTrackerSharedPref.saveBaseline(listType, updated);
    }

    // The parsed response's exact model class wasn't confirmed field-by-field
    // during research -- only the parser method itself was verified against
    // real bytecode. Reflectively scan for a Collection-typed field and treat
    // its elements as user rows rather than hardcoding an unconfirmed field name.
    private static List<FollowerListEntry> extractEntries(Object parsedResult) {
        List<FollowerListEntry> entries = new ArrayList<>();
        if (parsedResult == null) return entries;
        for (Field field : parsedResult.getClass().getDeclaredFields()) {
            try {
                if (!Collection.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value = field.get(parsedResult);
                if (!(value instanceof Collection)) continue;
                for (Object row : (Collection<?>) value) {
                    FollowerListEntry entry = toEntry(row);
                    if (entry != null) entries.add(entry);
                }
                if (!entries.isEmpty()) break;
            } catch (Exception ignored) {}
        }
        return entries;
    }

    private static FollowerListEntry toEntry(Object row) {
        try {
            UserData userData = new UserData(row);
            String username = userData.getUsername();
            String userId = userData.getUserId();
            if (username == null || userId == null) return null;
            return new FollowerListEntry(userId, username);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isEnabled() {
        return Pref.followerTrackerEnabled();
    }
}
