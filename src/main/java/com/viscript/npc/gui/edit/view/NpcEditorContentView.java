package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.viscript.npc.ViScriptNpc;
import org.jetbrains.annotations.Nullable;

public class NpcEditorContentView extends View {
    private final UIElement contentRoot = new UIElement();
    @Nullable
    private UIElement currentContent;

    public NpcEditorContentView() {
        super(String.format("%s.editor.view.content", ViScriptNpc.MOD_ID));
        contentRoot.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
        });
        addChild(contentRoot);
    }

    public NpcEditorContentView(@Nullable UIElement content) {
        this();
        setContent(content);
    }

    public void setContent(@Nullable UIElement content) {
        if (currentContent == content) {
            return;
        }
        contentRoot.clearAllChildren();
        currentContent = null;
        if (content != null) {
            contentRoot.addChild(content);
            currentContent = content;
        }
    }

    public void clearContent() {
        contentRoot.clearAllChildren();
        currentContent = null;
    }
}
