package com.viscript.npc.gui.edit;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.editor.settings.AppearanceSettings;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import net.minecraft.client.Minecraft;

/** 隔离编辑器客户端类，避免服务器菜单注册触发客户端类加载。 */
public final class NpcEditorUiFactory {
    public static ModularUI create() {
        var minecraft = Minecraft.getInstance();
        int guiScale = minecraft.options.guiScale().get();
        var window = EditorWindow.open(NpcEditor.EDITOR_ID, NpcEditor::new);
        // LDLib2 恢复最小化窗口时会把「未设置」的 -1 当成自动缩放，保留玩家原来的比例。
        var editor = window.getCurrentEditor();
        if (editor != null) editor.getEditorSettings().getSettings(AppearanceSettings.ID).ifPresent(settings -> {
            if (settings instanceof AppearanceSettings appearance && appearance.getScreenScale() < 0
                    && minecraft.options.guiScale().get() != guiScale) {
                minecraft.options.guiScale().set(guiScale);
                minecraft.resizeDisplay();
            }
        });
        ModularUI ui = new ModularUI(UI.of(window,
                com.viscript.npc.ViScriptNpc.id("lss/ai.lss")))
                .shouldCloseOnKeyInventory(false);
        if (!Platform.isDevEnv()) ui.shouldCloseOnEsc(false);
        return ui;
    }
}
