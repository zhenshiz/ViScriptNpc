package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.util.npc.NpcHelper;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ViScriptNpcClientUtil {

    @Info("客户端打开NPC编辑器")
    public static void openNpcEditor() {
        Minecraft minecraft = Minecraft.getInstance();
        EditorWindow editorWindow = new EditorWindow(NpcEditor::new);
        ModularUI ui = new ModularUI(UI.of(editorWindow, (size) -> size));
        if (!Platform.isDevEnv()) ui.shouldCloseOnEsc(false).shouldCloseOnKeyInventory(false);
        minecraft.setScreen(new ModularUIScreen(ui, Component.empty()));
        if (NpcHelper.cacheNpcProject != null && editorWindow.getCurrentEditor() != null) {
            editorWindow.getCurrentEditor().loadProject(NpcHelper.cacheNpcProject, null);
        }
    }
}
