package com.viscript.npc.util;

import com.viscript.npc.ViScriptNpc;
import com.viscript_lib.gui.editor.EditorFileFormat;

public final class NpcEditorFormats {
    public static final EditorFileFormat NPC = EditorFileFormat.compressed(ViScriptNpc.MOD_ID, "npc", "npc");

    private NpcEditorFormats() {
    }
}
