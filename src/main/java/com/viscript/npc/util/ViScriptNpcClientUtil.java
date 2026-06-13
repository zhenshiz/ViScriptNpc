package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.viscript.npc.gui.edit.NPCProject;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public class ViScriptNpcClientUtil {
    public static NPCProject cacheNpcProject;

    @Info("客户端打开NPC编辑器")
    public static void openNpcEditor(@Nullable CompoundTag tag) {
        EditorWindow editorWindow = getCurrentEditorWindow();
        if (editorWindow == null) return;
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

    @Nullable
    private static EditorWindow getCurrentEditorWindow() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ModularUIContainerScreen screen
                && screen.getMenu().getModularUI().ui.rootElement instanceof EditorWindow editorWindow) {
            return editorWindow;
        }
        if (minecraft.screen instanceof ModularUIScreen screen
                && screen.modularUI.ui.rootElement instanceof EditorWindow editorWindow) {
            return editorWindow;
        }
        return null;
    }
}
