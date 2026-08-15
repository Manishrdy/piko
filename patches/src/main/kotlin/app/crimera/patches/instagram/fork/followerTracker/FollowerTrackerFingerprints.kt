/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.fork.followerTracker

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

// Real, unobfuscated class name -- confirmed via apktool/baksmali against
// the real 439.0.0.37.89 APK.
internal const val FOLLOW_LIST_DATA_CLASS = "Lcom/instagram/follow/analytics/FollowListData;"

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
//
// The same two Bundle-key strings are ALSO used by a sibling method that
// builds a Bundle to launch a different FollowListFragment instance (for
// mutual-followers sub-navigation) -- it puts the same keys instead of
// reading them. The strings filter alone can't tell the two apart, so the
// custom check requires the actual IPUT_OBJECT that only the reading method
// has.
internal object FollowListFragmentEntryFingerprint : Fingerprint(
    strings = listOf("FollowListFragment.EntryType", "FollowListFragment.FollowListData"),
    custom = { methodDef, _ ->
        methodDef.implementation?.instructions?.any {
            it.opcode == Opcode.IPUT_OBJECT &&
                ((it as? ReferenceInstruction)?.reference as? FieldReference)?.type == FOLLOW_LIST_DATA_CLASS
        } == true
    },
)
