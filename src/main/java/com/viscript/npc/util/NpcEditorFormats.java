package com.viscript.npc.util;

import com.viscript.npc.ViScriptNpc;
import com.viscript_lib.gui.editor.EditorFileFormat;

public final class NpcEditorFormats {
    public static final EditorFileFormat NPC = EditorFileFormat.compressed(ViScriptNpc.MOD_ID, "npc", "npc");
    public static final EditorFileFormat AM_INTENTION = EditorFileFormat.compressed(
            ViScriptNpc.MOD_ID, "am_intention", "am_intention");

    private NpcEditorFormats() {
    }
}
