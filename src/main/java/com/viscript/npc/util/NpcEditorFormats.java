package com.viscript.npc.util;

import com.viscript.npc.ViScriptNpc;
import com.viscript_lib.gui.editor.EditorFileFormat;

public final class NpcEditorFormats {
    public static final EditorFileFormat NPC = EditorFileFormat.compressed(ViScriptNpc.MOD_ID, "npc", "npc");
    public static final EditorFileFormat NPC_ACTION = EditorFileFormat.compressed(ViScriptNpc.MOD_ID, "action", "npcaction");
    public static final EditorFileFormat NPC_TEST_SCENE = EditorFileFormat.compressed(
            ViScriptNpc.MOD_ID, "test_scene", "npcscene");

    private NpcEditorFormats() {
    }
}
