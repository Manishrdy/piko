/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.fork.followerTracker

import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.FOLLOWER_TRACKER_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

@Suppress("unused")
val followerTrackerPatch =
    bytecodePatch(
        name = "Local follower/unfollower tracker",
        description = "Detects who followed/unfollowed you by comparing what you see each time you open your Followers/Following list. Never polls or makes extra requests.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, userDataEntity)

        execute {
            // Observe each page of the parsed followers/following list, passing
            // the already-parsed result through unchanged -- this only reads
            // data the app already fetched for its own UI, it never triggers
            // an extra request.
            //
            // Two capture points, because the response handler class carries two
            // entry points for the same data: the legacy JSON parser, and a
            // Pando/GraphQL one taking an InputStream. On 439 only the Pando one
            // actually runs -- the JSON parser is present, matches, and patches
            // cleanly, but is never called, which is why hooking it alone
            // captured nothing. Each is wrapped on its own so a build missing
            // either still patches with the other.
            runCatching {
                FollowListParseFingerprint.apply {
                    val instructionList = method.instructions.toList()
                    val returnIndex = instructionList.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
                    check(returnIndex >= 0) { "Follow list parser has no RETURN_OBJECT" }
                    val returnRegister = (instructionList[returnIndex] as OneRegisterInstruction).registerA

                    method.addInstructions(
                        returnIndex,
                        """
                        invoke-static {v$returnRegister}, $FOLLOWER_TRACKER_DESCRIPTOR->onListParsed(Ljava/lang/Object;)V
                        """.trimIndent(),
                    )
                }
            }

            // Matched by signature rather than by its obfuscated name (GC1 on
            // 439): it is the only sibling taking an InputStream and handing
            // back the parsed tree.
            runCatching {
                FollowListParseFingerprint.classDef.methods
                    .first {
                        it.parameterTypes.size == 2 &&
                            it.parameterTypes[1].toString() == "Ljava/io/InputStream;" &&
                            it.returnType == "Ljava/lang/Object;"
                    }.apply {
                        val returnInstruction = instructions.last { it.opcode == Opcode.RETURN_OBJECT }
                        val treeRegister = returnInstruction.registersUsed[0]

                        addInstructions(
                            returnInstruction.location.index,
                            """
                            invoke-static {v$treeRegister}, $FOLLOWER_TRACKER_DESCRIPTOR->onListTreeParsed(Ljava/lang/Object;)V
                            """.trimIndent(),
                        )
                    }
            }

            // Learn which "follow list" variant (followers, following, mutual,
            // group members, etc) is being opened, from the same FollowListData
            // object the fragment itself reads right after this write.
            FollowListFragmentEntryFingerprint.apply {
                val instructionList = method.instructions.toList()
                val putIndex =
                    instructionList.indexOfFirst {
                        it.opcode == Opcode.IPUT_OBJECT &&
                            ((it as ReferenceInstruction).reference as? FieldReference)?.type == FOLLOW_LIST_DATA_CLASS
                    }
                check(putIndex >= 0) { "FollowListData field write not found" }
                val dataRegister = (instructionList[putIndex] as TwoRegisterInstruction).registerA

                method.addInstructions(
                    putIndex + 1,
                    """
                    iget-object v$dataRegister, v$dataRegister, $FOLLOW_LIST_DATA_CLASS->A00:LX/0EfV;
                    if-eqz v$dataRegister, :piko_skip
                    iget-object v$dataRegister, v$dataRegister, LX/0EfV;->A00:Ljava/lang/String;
                    invoke-static {v$dataRegister}, $FOLLOWER_TRACKER_DESCRIPTOR->onListScreenOpened(Ljava/lang/String;)V
                    :piko_skip
                    nop
                    """.trimIndent(),
                )
            }

            enableSettings("followerTracker")
        }
    }
