package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class ViScriptNpcClientUtil {
    public static NPCProject cacheNpcProject;

    @Info("客户端打开NPC编辑器")
    public static void openNpcEditor(@Nullable CompoundTag tag) {
        Minecraft minecraft = Minecraft.getInstance();
        EditorWindow editorWindow = new EditorWindow(NpcEditor::new);
        ModularUI ui = new ModularUI(UI.of(editorWindow));
        ui.shouldCloseOnKeyInventory(false);
        minecraft.setScreen(new ModularUIScreen(ui, Component.empty()));

        Editor editor = editorWindow.getCurrentEditor();
        if (editor == null) return;
        if (tag != null && !tag.isEmpty()) {
            var project = (NPCProject) NPCProject.PROVIDER.projectCreator.get();
            project.initNewProject();
            try {
                project.npc.deserializeNBT(Platform.getFrozenRegistry(), tag);
                editor.loadProject(project, null);
                return;
            } catch (Exception ignored) {
            }
        }
        if (cacheNpcProject != null) {
            editor.loadProject(cacheNpcProject, null);
        }
    }
}
