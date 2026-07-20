package com.viscript.npc.gui.edit.page;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.view.InspectorView;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.registry.ILDLRegisterClient;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

public interface INpcEditorPage extends ILDLRegisterClient<INpcEditorPage, Supplier<INpcEditorPage>> {
    String ID = ViScriptNpc.MOD_ID + ":npc_editor_page";
    String TRANSLATION_GROUP = ViScriptNpc.MOD_ID + ".editor.page";

    default ResourceLocation pageId() {
        String name = name();
        return ResourceLocation.parse(name.contains(":") ? name : ViScriptNpc.id(name).toString());
    }

    default Component displayName() {
        return getChatComponent();
    }

    default int order() {
        return isLDLRegister() ? getRegisterUIClient().priority() : 0;
    }

    default IGuiTexture icon() {
        return IGuiTexture.EMPTY;
    }

    default View createLeftView(NpcEditor editor, NPCProject project) {
        return null;
    }

    default View createCenterView(NpcEditor editor, NPCProject project) {
        UIElement content = createContent(editor, project);
        if (content instanceof View view) {
            return view;
        }
        return editor.createPageContentView(this, project);
    }

    default View createRightView(NpcEditor editor, NPCProject project) {
        return createInspectorView(editor, project);
    }

    default View createBottomView(NpcEditor editor, NPCProject project) {
        return editor.getResourceView();
    }

    default InspectorView createInspectorView(NpcEditor editor, NPCProject project) {
        return editor.getInspectorView();
    }

    default void inspect(NpcEditor editor, NPCProject project, InspectorView inspectorView) {
        inspectorView.inspect(project.npc.createPageConfigurable(this),
                configurator -> onInspectorConfiguratorChanged(editor, project, configurator));
    }

    default void onInspectorConfiguratorChanged(NpcEditor editor, NPCProject project, Configurator configurator) {
    }

    default UIElement createContent(NpcEditor editor, NPCProject project) {
        return new NpcEditorPageView(editor, this);
    }

    default void onSelected(NpcEditor editor, NPCProject project) {
        editor.applyPageLayout(this, project);
    }
}
