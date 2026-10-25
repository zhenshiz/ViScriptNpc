package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortOrientation;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.viscript.npc.npc.ai.runtime.AiOperations;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** AI 节点只声明纵向控制端口；参数集中在选中节点的属性面板。 */
public abstract class AiGraphNode extends Node {
    public final String operation;
    protected AiGraphNode(String operation) { this.operation = operation; }
    @Override public Component getDisplayName() { return Component.translatable("viscript_npc.ai.node." + operation); }
    @Override public float getNodeWidth() { return 130; }

    /**
     * 创建节点选择面板和行为检查器使用的本地化说明。
     *
     * @return 每次创建的独立界面，包含用途、执行结果、连接规则和参数名称
     */
    @Override public UIElement createDescriptionUI() {
        var content = new UIElement().layout(l -> l.widthPercent(100).gapAll(5));
        content.setId("npc_ai_node_info_" + operation);
        content.addClass("npc_ai_node_info");
        content.addChild(text(getDisplayName().copy().withStyle(ChatFormatting.BOLD)));
        content.addChild(text(Component.translatable("viscript_npc.ai.description." + operation)));
        content.addChild(text(Component.translatable("viscript_npc.ai.info.execution").withStyle(ChatFormatting.BOLD)));
        content.addChild(text(Component.translatable("viscript_npc.ai.behavior." + operation)));
        var definition = AiOperations.get(operation);
        String connection = operation.equals("root") ? "root" : operation.equals("branch") ? "branch"
                : definition.maxChildren() == 0 ? "leaf" : definition.maxChildren() == 1 ? "single" : "ordered";
        content.addChild(text(Component.translatable("viscript_npc.ai.info.connections").withStyle(ChatFormatting.BOLD)));
        content.addChild(text(Component.translatable("viscript_npc.ai.info.connections." + connection)));
        if (!definition.fields().isEmpty()) {
            content.addChild(text(Component.translatable("viscript_npc.ai.info.parameters").withStyle(ChatFormatting.BOLD)));
            for (var field : definition.fields()) {
                content.addChild(text(fieldName(operation, field.id())));
            }
        }
        return content;
    }

    static Component fieldName(String operation, String field) {
        return Component.translatable("viscript_npc.ai.field." +
                (operation.equals("ranged") && field.equals("speed") ? "projectile_speed" : field));
    }

    private static Label text(Component component) {
        var label = new Label();
        label.setText(component);
        label.layout(l -> l.widthPercent(100).flexShrink(0));
        label.textStyle(s -> s.textWrap(TextWrap.WRAP).adaptiveHeight(true));
        return label;
    }

    @Override public void onDefinePorts(IPortDefinitionContext context) {
        if (!operation.equals("root")) context.addInputPort("parent", TypeHandles.EXECUTION_FLOW)
                .withDisplayName(Component.empty()).withOrientation(PortOrientation.Vertical).build();
        if (AiOperations.hasHitBranch(operation)) context.addOutputPort("hit", TypeHandles.EXECUTION_FLOW)
                .withDisplayName(Component.translatable("viscript_npc.ai.on_hit")).build();
        if (AiOperations.get(operation).maxChildren() > 0) context.addOutputPort("children", TypeHandles.EXECUTION_FLOW)
                .withDisplayName(Component.empty()).withOrientation(PortOrientation.Vertical).build();
    }
}
