/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.followerTracker;

public class FollowerListEntry {
    public final String userId;
    public final String username;
    public int depth;

    public FollowerListEntry(String userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public FollowerListEntry(String userId, String username, int depth) {
        this.userId = userId;
        this.username = username;
        this.depth = depth;
    }
}
