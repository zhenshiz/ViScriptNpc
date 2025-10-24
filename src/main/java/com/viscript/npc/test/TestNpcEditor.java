package com.viscript.npc.test;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.test.ui.IScreenTest;
import com.viscript.npc.gui.edit.NpcEditor;
import lombok.NoArgsConstructor;
import net.minecraft.world.entity.player.Player;

@LDLRegisterClient(name = "npc_editor", registry = "ldlib2:screen_test")
@NoArgsConstructor
public class TestNpcEditor implements IScreenTest {
    @Override
    public ModularUI createUI(Player entityPlayer) {
        var root = new EditorWindow(NpcEditor::new);
        return new ModularUI(UI.of(root, size -> size));
    }
}
