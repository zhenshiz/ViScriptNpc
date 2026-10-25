package com.viscript.npc.gui.test;

import com.viscript.npc.npc.ai.test.NpcAiFixtures;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import com.viscript_lib.gui.components.search.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import com.viscript.npc.npc.NpcRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 验证 VSL 资源补全的实际搜索、候选选择、类型过滤和稳定 ID 保存。 */
@LDLRegisterClient(name = "npc_ai_resource_completion", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiResourceCompletionScenario implements UIScenario {
    private record Resource(String operation, String field, String query, String id, Class<? extends RegistrySearchBox<?>> type) {}
    private static final List<Resource> RESOURCES = List.of(
            new Resource("find_target", "entity_type", "entity.minecraft.zombie", "minecraft:zombie", EntityTypeSearchBox.class),
            new Resource("ranged", "item", "item.minecraft.diamond_sword", "minecraft:diamond_sword", ItemSearchBox.class),
            new Resource("effect", "effect", "effect.minecraft.speed", "minecraft:speed", MobEffectSearchBox.class),
            new Resource("sound", "sound", "entity.experience_orb.pickup", "minecraft:entity.experience_orb.pickup", SoundEventSearchBox.class),
            new Resource("particle", "particle", "end_rod", "minecraft:end_rod", ParticleTypeSearchBox.class));

    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(3).tags("ui", "ai", "regression");
    }

    @Override public void define(ScenarioBuilder s) {
        var fixture = NpcAiFixtures.action("find_target");
        fixture.nodes.stream().filter(n -> n.type.equals("find_target")).findFirst().orElseThrow().input("filter", AiValue.choice("living", java.util.List.of("hostile", "player", "living")));
        var sequence = new AiNodeData(); sequence.type = "sequence";
        sequence.children.add(fixture.node(fixture.root).children.getFirst());
        fixture.node(fixture.root).children.set(0, sequence.id);
        for (var resource : RESOURCES.subList(1, RESOURCES.size())) {
            var node = new AiNodeData(); node.type = resource.operation;
            sequence.children.add(node.id); fixture.nodes.add(node);
        }
        fixture.nodes.add(sequence);
        s.server("打开资源补全测试工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("载入资源参数节点", c -> {
                    var window = (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
                    var project = new NpcActionProject(); project.program = fixture;
                    window.createNewEditor(NpcEditor::new).loadProject(project, null);
                });

        for (var resource : RESOURCES) {
            String selector = "#npc_ai_resource_" + resource.field;
            s.step("选择资源参数节点 " + resource.field, c -> {
                        var node = view(c).model().getNodeModels().stream().filter(n -> n instanceof AiNodeModel ai
                                && ai.operation().equals(resource.operation)).map(AiNodeModel.class::cast).findFirst().orElseThrow();
                        view(c).inspectNode(node);
                        c.put("resource_node_" + resource.field, node.data.id);
                    }).ticks(2)
                    .check("使用 VSL 类型补全框 " + resource.field, c -> resource.type.isInstance(c.el(selector).element()))
                    .step("检查候选类型限制 " + resource.field, c -> {
                        if (!resource.field.equals("entity_type")) return;
                        var types = new ArrayList<EntityType<?>>();
                        c.el(selector).as(EntityTypeSearchBox.class).getSearchUI().search("", types::add);
                        c.check("Mob 补全包含原版与模组生物并排除玩家盔甲架和投射物", types.contains(EntityType.ZOMBIE)
                                && types.contains(EntityType.COW) && types.contains(NpcRegister.CUSTOM_NPC.get())
                                && !types.contains(EntityType.PLAYER) && !types.contains(EntityType.ARMOR_STAND)
                                && !types.contains(EntityType.ARROW) && !types.contains(EntityType.ITEM)
                                && types.stream().allMatch(type -> EntityTypeSearchBox.isEntityClass(type, Mob.class, c.mc().level)));
                    })
                    .click(selector).ticks(2)
                    .check("点击打开候选列表 " + resource.field, c -> picker(c, selector).isOpen())
                    .step("标记补全输入框 " + resource.field, c -> picker(c, selector).textField.setId("npc_ai_completion_input"))
                    .typeInto("#npc_ai_completion_input", Component.translatable(resource.query).getString())
                    .waitUntil("显示输入匹配的候选 " + resource.field, c -> c.query(".__search-component_dialog__ *")
                            .type(TextElement.class).withText(candidateName(resource)).visible().count() == 1)
                    .screenshot("completion_" + resource.field)
                    .step("标记补全候选按钮 " + resource.field, c -> {
                        var element = c.query(".__search-component_dialog__ *").type(TextElement.class)
                                .withText(candidateName(resource)).visible().one().element();
                        while (element.getChildren().stream().noneMatch(child -> child.getId().equals("selector#overlayButton"))) {
                            element = element.getParent();
                            if (element == null) throw new IllegalStateException("候选项缺少原生选择按钮");
                        }
                        element.getChildren().stream().filter(child -> child.getId().equals("selector#overlayButton"))
                                .findFirst().orElseThrow().setId("npc_ai_completion_candidate");
                    }).step("点击补全候选 " + resource.field, c -> {
                        var bounds = c.el("#npc_ai_completion_candidate").bounds();
                        c.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                        c.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                    }).ticks(2)
                    .check("选择译名保存稳定资源 ID " + resource.field, c -> value(c, resource).equals(resource.id)
                            && picker(c, selector).getSelectedIdString().equals(resource.id))
                    .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                    .check("资源选择可撤销 " + resource.field, c -> !value(c, resource).equals(resource.id))
                    .click("#npc_ai_graph_action #npc_ai_redo").ticks(2)
                    .check("资源选择可重做 " + resource.field, c -> value(c, resource).equals(resource.id));
        }

        s.step("检查粒子参数限制", c -> {
                    var particles = new ArrayList<Object>();
                    picker(c, "#npc_ai_resource_particle").getSearchUI().search("", particles::add);
                    c.check("只补全当前执行器支持的无额外参数粒子", !particles.isEmpty()
                            && particles.stream().allMatch(SimpleParticleType.class::isInstance));
                }).step("保存并重开资源参数", c -> {
                    var reopened = new AiGraph("action");
                    reopened.model().deserializeNBT(Platform.getFrozenRegistry(), view(c).model().serializeNBT(Platform.getFrozenRegistry()));
                    c.check("五类资源均按原 ID 保存", RESOURCES.stream().allMatch(resource -> reopened.model().compile()
                            .node(c.get("resource_node_" + resource.field)).literal(resource.field, AiValue.text("")).text.equals(resource.id)));
                }).step("检查缺失模组资源与空筛选", c -> {
                    var resource = RESOURCES.getFirst();
                    var node = (AiNodeModel) view(c).model().getModel(UUID.fromString(c.get("resource_node_" + resource.field)));
                    view(c).edit("parameter", () -> node.data.input("entity_type", AiValue.mobType("missing_mod:entity")));
                    view(c).inspectNode((AiNodeModel) view(c).model().getModel(node.getUid()));
                }).ticks(2)
                .check("缺失资源保留原 ID 且不替换为默认实体", c -> value(c, RESOURCES.getFirst()).equals("missing_mod:entity")
                        && picker(c, "#npc_ai_resource_entity_type").getValue() == null
                        && c.query("#npc_ai_resource_entity_type *").type(TextElement.class).withText("missing_mod:entity").count() == 1)
                .step("清除实体类型筛选", c -> {
                    var node = (AiNodeModel) view(c).model().getModel(UUID.fromString(c.get("resource_node_entity_type")));
                    view(c).edit("parameter", () -> node.data.input("entity_type", AiValue.mobType("")));
                    view(c).inspectNode((AiNodeModel) view(c).model().getModel(node.getUid()));
                }).ticks(2)
                .check("空实体筛选打开表单时保持为空", c -> value(c, RESOURCES.getFirst()).isEmpty()
                        && picker(c, "#npc_ai_resource_entity_type").getValue() == null)
                .closeScreen();
    }

    private static String candidateName(Resource resource) {
        return resource.field.equals("sound") || resource.field.equals("particle") ? resource.id
                : Component.translatable(resource.query).getString();
    }

    private static String value(TestContext c, Resource resource) {
        return view(c).model().compile().node(c.get("resource_node_" + resource.field)).literal(resource.field, AiValue.text("")).text;
    }

    private static RegistrySearchBox<?> picker(TestContext c, String selector) {
        return c.el(selector).as(RegistrySearchBox.class);
    }

    private static AiGraphView view(TestContext c) { return c.el("#npc_ai_graph_action").as(AiGraphView.class); }
}
