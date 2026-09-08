package com.viscript.npc.npc.ai.editor;

import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.NodeElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import org.jetbrains.annotations.NotNull;

public class NpcAiDebugNodeElement extends NodeElement {
    public NpcAiDebugNodeElement(AbstractNodeModel nodeModel) {
        super(nodeModel);
    }

    @Override
    public void drawBackgroundOverlay(@NotNull GUIContext guiContext) {
        if (NpcAiDebugClientState.isCurrent(getModel().getUid())) {
            guiContext.drawTexture(ColorPattern.GREEN.borderTexture(3),
                    getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        }
        if (NpcAiDebugClientState.isBreakpoint(getModel().getUid())) {
            guiContext.drawTexture(ColorPattern.BRIGHT_RED.borderTexture(1),
                    getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        }
        super.drawBackgroundOverlay(guiContext);
    }
}
