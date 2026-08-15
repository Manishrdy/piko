/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.fork.followerTracker

import app.morphe.patcher.Fingerprint

// Parses each page of the followers/following list response. Verified against
// the real 439.0.0.37.89 APK via d2j-dex2jar + javap constant-pool inspection
// (class X.0BhV, method unsafeParseFromJson) -- not guessed.
internal object FollowListParseFingerprint : Fingerprint(
    strings = listOf("big_list", "follow_ranking_token", "users", "next_max_id"),
    custom = { methodDef, _ -> methodDef.name.lowercase().contains("parsefromjson") },
)

// FollowListFragment's arguments-reading method (real, unobfuscated Bundle key
// strings) -- where the fragment learns whether it's showing followers,
// following, or one of Instagram's other "follow list" variants (mutual,
// group members, etc). Verified via apktool/baksmali against the real
// 439.0.0.37.89 APK.
internal object FollowListFragmentEntryFingerprint : Fingerprint(
    strings = listOf("FollowListFragment.EntryType", "FollowListFragment.FollowListData"),
)
