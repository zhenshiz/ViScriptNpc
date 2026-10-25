package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Inspector;

/** 编辑器右侧的行为参数面板，与画布中的节点外观检查器独立。 */
public final class AiParametersView extends View {
    public final Inspector inspector = new Inspector();

    public AiParametersView() {
        super("viscript_npc.ai.editor.parameters");
        setId("npc_ai_parameters_view");
        inspector.layout(l -> l.widthPercent(100).flex(1).minHeight(0));
        addChild(inspector);
    }

    public void inspect(IConfigurable configurable) {
        inspector.inspect(configurable);
    }
}
