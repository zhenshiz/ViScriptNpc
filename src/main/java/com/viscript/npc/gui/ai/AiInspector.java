package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.configurator.ConfiguratorAccessors;
import com.lowdragmc.lowdraglib2.configurator.accessors.IConfiguratorAccessor;
import com.lowdragmc.lowdraglib2.configurator.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.SearchComponent;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.utils.search.IResultHandler;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.AiOperations;
import com.viscript.npc.util.NpcActionFiles;
import com.viscript_lib.gui.components.search.*;
import com.viscript_lib.gui.components.DraggableUI;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 使用 LDLib2 的类型访问器生成参数表单，并提供变量和资源选择器。 */
public final class AiInspector {
    public static void build(AiGraphView view, AiNodeModel node, ConfiguratorGroup group) {
        for (var field : AiOperations.fields(node.data, view.model().declarations())) field(view, node, field, group);
        if (node.operation().equals("call")) {
            var program = view.actionResolver.apply(node.data.literal("file", AiValue.text("")).text);
            if (program != null) for (AiVariable variable : program.parameters) {
                var field = new AiOperations.Field("param:" + variable.id, variable.initial);
                field(view, node, field, group, AiVariables.displayName(variable), AiVariables.tips(variable));
            }
        }
        childOrder(view, node, group);
    }

    private static void childOrder(AiGraphView view, AiNodeModel node, ConfiguratorGroup group) {
        if (AiOperations.get(node.operation()).maxChildren() <= 1) return;
        var children = view.model().children(node);
        if (children.isEmpty()) return;
        var order = new ConfiguratorGroup("viscript_npc.ai.child_order", false);
        order.setId("npc_ai_child_order");
        order.setTips("viscript_npc.ai.tip.child_order");
        var sortable = new DraggableUI<>(children.stream().map(child -> child.getUid().toString()).toList(), ids -> {
            var current = view.model().children(node).stream().map(child -> child.getUid().toString()).toList();
            if (!current.equals(ids)) view.edit("child_order", () -> node.data.children = new ArrayList<>(ids));
        });
        sortable.setId("npc_ai_child_order_list");
        sortable.setActive(!view.isReadOnly());
        sortable.layout(l -> l.flexDirection(FlexDirection.COLUMN).wrap(FlexWrap.NO_WRAP)
                .widthPercent(100).paddingAll(0).gapAll(2));
        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            var row = new Label();
            row.layout(l -> l.widthPercent(100).minHeight(14).paddingVertical(2).flexShrink(0));
            row.textStyle(s -> s.textAlignHorizontal(com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal.LEFT)
                    .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.ROLL));
            Component name = Component.translatable("viscript_npc.ai.node." + child.operation());
            if (child.operation().equals("call")) {
                String file = child.data.literal("file", AiValue.text("")).text;
                name = AiActionNames.displayName(file, view.actionResolver.apply(file));
            }
            if (child.operation().equals("stage")) {
                String stage = child.data.literal("name", AiValue.text("")).text;
                if (!stage.isBlank()) name = Component.translatable("viscript_npc.ai.child.named", name, stage);
            }
            row.setText(node.operation().equals("branch")
                    ? Component.translatable("viscript_npc.ai.child." + (i == 0 ? "true" : "false"), name)
                    : Component.translatable("viscript_npc.ai.child.step", i + 1, name));
            row.setId("npc_ai_child_" + child.getUid());
            sortable.addSortableCard(child.getUid().toString(), row);
        }
        order.configuratorContainer.addChild(sortable);
        group.addConfigurator(order);
    }

    private static void field(AiGraphView view, AiNodeModel node, AiOperations.Field field, ConfiguratorGroup group) {
        field(view, node, field, group, AiGraphNode.fieldName(node.operation(), field.id()),
                com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips.empty());
    }

    private static void field(AiGraphView view, AiNodeModel node, AiOperations.Field field,
                              ConfiguratorGroup group, Component label, com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips tips) {
        boolean callParameter = field.id().startsWith("param:");
        var wired = view.model().wiredBinding(node, field.id());
        boolean inherited = callParameter && !node.data.inputs.containsKey(field.id());
        AiBinding binding = node.data.inputs.getOrDefault(field.id(), AiBinding.literal(field.initial().copy()));
        var row = new Configurator();
        row.addClass("npc_ai_parameter_row");
        row.setLabel(label);
        row.setId("npc_ai_field_" + field.id().replace(':', '_'));
        if (field.id().equals("speed") && Set.of("navigate", "approach", "patrol").contains(node.operation()))
            row.setTips("viscript_npc.ai.parameter.speed.tips");
        if (node.operation().equals("set_target") && field.id().equals("entity"))
            row.setDisplay(!node.data.literal("entity", AiValue.text("")).text.isBlank());
        if (AiOperations.isPerception(node.operation()) && field.id().equals("entity_type")) {
            row.setDisplay(view.model().usesEntityTypeFilter(node));
            row.setTips("viscript_npc.ai.tip.mob_filter");
        }
        if (AiOperations.isPerception(node.operation()) && view.model().wiredBinding(node, "perception") == null) {
            var mode = node.data.inputs.get("perception");
            if (mode == null || mode.scope.equals("literal")) {
                String shape = mode == null ? (AiOperations.isContinuousPerception(node.operation()) ? "combined" : "range") : mode.value.text;
                if (field.id().equals("range")) row.setDisplay(!shape.equals("view"));
                if (field.id().equals("view_radius") || field.id().equals("view_angle")) row.setDisplay(!shape.equals("range"));
            }
        }
        if (AiOperations.isPerception(node.operation()) && Set.of("view_radius", "view_angle").contains(field.id()))
            row.setTips("viscript_npc.ai.tip." + field.id());
        if (wired != null) {
            var indicator = new com.lowdragmc.lowdraglib2.gui.ui.UIElement() {
                @Override public void screenTick() {
                    super.screenTick();
                    String source = wired.scope.equals("literal") ? Component.translatable("viscript_npc.ai.input.constant").getString()
                            : view.model().declarations().parameters.stream().filter(v -> v.id.equals(wired.variableId)).map(v -> AiVariables.displayName(v).getString())
                            .findFirst().orElseGet(() -> java.util.stream.Stream.concat(view.model().declarations().variables.stream(),
                                    view.model().declarations().npcVariables.stream()).filter(v -> v.id.equals(wired.variableId))
                                    .map(v -> AiVariables.displayName(v).getString()).findFirst().orElse(wired.variableId));
                    var actual = AiClientDebug.latest.inputs.get(node.data.id + ":" + field.id());
                    var tip = Component.translatable("viscript_npc.ai.tip.input_connected", source);
                    if (actual != null) tip.append("\n").append(Component.translatable("viscript_npc.ai.input.current", actual.toString()));
                    style(s -> s.tooltips(tip));
                }
            };
            indicator.setId("npc_ai_connected_" + field.id().replace(':', '_'));
            indicator.layout(l -> l.width(10).height(10).flexShrink(0));
            indicator.style(s -> s.backgroundTexture(AiIcons.of("input")));
            row.addInlineChild(indicator);
        }
        if (callParameter) {
            var explanation = tips.append(Component.translatable("viscript_npc.ai.tip.call_parameter"));
            row.style(s -> s.tooltips(explanation));
            row.setTips(explanation.tooltips());
        }
        Map<String, AiVariable> choices = new LinkedHashMap<>();
        var metadata = view.model().declarations();
        boolean anyType = node.operation().equals("debug_log") && field.id().equals("value");
        metadata.parameters.stream().filter(v -> anyType || v.initial.type.equals(field.initial().type)).forEach(v -> choices.put("parameter:" + v.id, v));
        metadata.variables.stream().filter(v -> anyType || v.initial.type.equals(field.initial().type)).forEach(v ->
                choices.put((metadata.kind.equals("tree") ? "blackboard:" : "local:") + v.id, v));
        metadata.npcVariables.stream().filter(v -> anyType || v.initial.type.equals(field.initial().type)).forEach(v -> choices.put("blackboard:" + v.id, v));
        if (!AiOperations.hasDataInput(field)) choices.clear();
        if (field.id().equals("output") || field.id().equals("variable")) {
            var declarations = field.id().equals("variable") && node.data.literal("scope", AiValue.text("local")).text.equals("blackboard")
                    ? metadata.npcVariables : metadata.variables;
            var available = declarations.stream().filter(v -> !field.id().equals("output") || v.initial.type.equals(AiOperations.outputType(node.data))).toList();
            row.addInlineChild(new SelectorConfigurator<>("", () -> binding.value.text, id -> {
                view.edit("bind", () -> {
                    node.data.input(field.id(), AiValue.text(id));
                    available.stream().filter(v -> v.id.equals(id)).findFirst().ifPresent(v -> {
                        if (field.id().equals("variable")) node.data.input("value", v.initial.copy());
                    });
                }); refresh(view, node);
            }, "", false, java.util.stream.Stream.concat(java.util.stream.Stream.of(""), available.stream().map(v -> v.id)).toList(),
                    id -> id.isEmpty() ? Component.translatable("viscript_npc.ai.output_none").getString() : available.stream().filter(v -> v.id.equals(id)).map(v -> v.name).findFirst().orElse(id)));
            group.addConfigurator(row); return;
        }
        List<String> sources = new ArrayList<>(callParameter ? List.of("default", "literal") : List.of("literal"));
        if (field.initial().type.equals("entity")) sources.addAll(List.of("context:self", "context:target", "context:hit", "context:source"));
        sources.addAll(choices.keySet());
        if (callParameter || sources.size() > 1) {
            var source = new SelectorConfigurator<>("",
                () -> inherited ? "default" : field.initial().type.equals("entity") && !node.data.inputs.containsKey(field.id())
                        ? "context:" + (Set.of("particle", "sound").contains(node.operation()) ? "self" : "target")
                        : binding.scope.equals("literal") ? "literal" : binding.scope + ":" + binding.variableId,
                key -> {
                    view.edit("bind", () -> {
                        if (key.equals("default")) { node.data.inputs.remove(field.id()); return; }
                        AiBinding changed = new AiBinding();
                        changed.value = field.initial().copy();
                        if (!key.equals("literal")) {
                            changed.scope = key.substring(0, key.indexOf(':'));
                            changed.variableId = key.substring(key.indexOf(':') + 1);
                        }
                        node.data.inputs.put(field.id(), changed);
                    });
                    refresh(view, node);
                }, "literal", false, sources,
                key -> key.equals("default") ? Component.translatable("viscript_npc.ai.parameter_default").getString()
                        : key.equals("literal") ? Component.translatable(callParameter ? "viscript_npc.ai.parameter_override" : "viscript_npc.ai.literal").getString()
                        : key.startsWith("context:") ? Component.translatable("viscript_npc.ai.entity." + key.substring(8)).getString()
                        : choices.containsKey(key) ? choices.get(key).name : key);
            source.selector.setId("npc_ai_source_" + field.id().replace(':', '_'));
            source.setActive(wired == null);
            row.addInlineChild(source);
        }
        if (binding.scope.equals("literal") && !(field.initial().type.equals("entity") && !node.data.inputs.containsKey(field.id()))) {
            Consumer<AiValue> update = value -> {
                view.edit(Set.of("file", "scope").contains(field.id()) ? "bind" : "parameter", () -> {
                    if (field.id().equals("file") && !node.data.literal("file", AiValue.text("")).text.equals(value.text))
                        node.data.inputs.keySet().removeIf(key -> key.startsWith("param:"));
                    node.data.inputs.put(field.id(), AiBinding.literal(value));
                });
                if (field.id().equals("scope") || field.id().equals("file") || field.id().equals("filter") || field.id().equals("perception") || field.id().equals("property")) refresh(view, node);
            };
            var editor = valueEditor(field.id(), binding.value.copy(), field.choices(), update, view.actionResolver);
            editor.setActive(!inherited && wired == null);
            if (editor instanceof NumberConfigurator number) number.textField.setId("npc_ai_value_" + field.id().replace(':', '_'));
            row.addInlineChild(editor);
        }
        group.addConfigurator(row);
    }

    public static Configurator valueEditor(String id, AiValue value, List<String> choices, Consumer<AiValue> update) {
        return valueEditor(id, value, choices, update, NpcActionFiles::loadLocal);
    }

    private static Configurator valueEditor(String id, AiValue value, List<String> choices, Consumer<AiValue> update,
                                            java.util.function.Function<String, AiProgram> actionResolver) {
        if (choices.isEmpty()) choices = AiValue.choices(value.type);
        final List<String> options = choices;
        return switch (value.type) {
            case "boolean" -> autoEditor(Boolean.class, () -> value.flag, v -> { value.flag = v; update.accept(value.copy()); });
            case "number" -> AiInspector.<Number>autoEditor(Double.class, () -> value.number,
                    v -> { value.number = v.doubleValue(); update.accept(value.copy()); });
            case "entity" -> {
                var ref = new Configurator();
                ref.addInlineChild(autoEditor(String.class, () -> value.entity.uuid, v -> { value.entity.uuid = v; update.accept(value.copy()); }).setLabel("UUID"));
                ref.addInlineChild(autoEditor(String.class, () -> value.entity.dimension, v -> { value.entity.dimension = v; update.accept(value.copy()); })
                        .setLabel(Component.translatable("viscript_npc.ai.field.dimension")));
                yield ref;
            }
            case "position" -> {
                var position = new Configurator();
                position.inlineContainer.layout(l -> l.flexDirection(FlexDirection.ROW).wrap(FlexWrap.WRAP).gapAll(2));
                for (int i = 0; i < 3; i++) {
                    int axis = i;
                    var coordinate = AiInspector.<Number>autoEditor(Double.class,
                            () -> axis == 0 ? value.position.x : axis == 1 ? value.position.y : value.position.z,
                            v -> {
                                Vec3 old = value.position;
                                value.position = new Vec3(axis == 0 ? v.doubleValue() : old.x, axis == 1 ? v.doubleValue() : old.y, axis == 2 ? v.doubleValue() : old.z);
                                update.accept(value.copy());
                            });
                    coordinate.setLabel(List.of("X", "Y", "Z").get(i));
                    coordinate.layout(l -> l.flex(1).minWidth(40));
                    position.addInlineChild(coordinate);
                }
                yield position;
            }
            default -> {
                if (!options.isEmpty()) yield new SelectorConfigurator<>("", () -> value.text,
                        v -> { value.text = v; update.accept(value.copy()); }, value.text, false, options,
                        v -> Component.translatable("viscript_npc.ai.choice." + v).getString());
                var resourceSearch = resourceSearch(value.type.equals("mob_type") ? "entity_type" : id, value.text);
                if (resourceSearch != null) yield resourceEditor(id, value, update, resourceSearch);
                if (id.equals("file")) {
                    List<String> resources = AiServerActions.choices();
                    Map<String, String> names = new LinkedHashMap<>();
                    for (String resource : resources) names.put(resource,
                            AiActionNames.displayName(resource, actionResolver.apply(resource)).getString());
                    var configurator = new Configurator();
                    var search = new SearchComponent<String>(new SearchComponent.ISearchUI<>() {
                        public String resultText(String result) { return names.getOrDefault(result, result); }
                        public void onResultSelected(String result) { if (result != null) { value.text = result; update.accept(value.copy()); } }
                        public void search(String word, IResultHandler<String> results) {
                            String query = word.toLowerCase(Locale.ROOT);
                            resources.stream().filter(v -> v.toLowerCase(Locale.ROOT).contains(query)
                                    || names.get(v).toLowerCase(Locale.ROOT).contains(query)).forEach(results::accept);
                        }
                    });
                    search.setCandidateUIProvider(UIElementProvider.text(
                            result -> Component.literal(result == null ? "" : names.getOrDefault(result, result))));
                    search.setId("npc_ai_resource_" + id);
                    search.setSelected(value.text, false);
                    search.layout(l -> l.widthPercent(100));
                    configurator.inlineContainer.addChild(search);
                    yield configurator;
                }
                yield autoEditor(String.class, () -> value.text, v -> { value.text = v; update.accept(value.copy()); });
            }
        };
    }

    // 动态节点复用 @Configurable 表单的类型访问器，无需为每个节点另建一套数据类。
    @SuppressWarnings("unchecked")
    private static <T> Configurator autoEditor(Class<?> type, Supplier<T> supplier, Consumer<T> update) {
        var accessor = (IConfiguratorAccessor<T>) ConfiguratorAccessors.findByClass(type);
        return accessor.create("", supplier, update, false, null, null);
    }

    private static RegistrySearchBox<?> resourceSearch(String field, String value) {
        var id = ResourceLocation.tryParse(value);
        return switch (field) {
            case "entity_type" -> new EntityTypeSearchBox(registered(BuiltInRegistries.ENTITY_TYPE, id))
                    .onlyMobs(Minecraft.getInstance().level);
            case "item" -> new ItemSearchBox(registered(BuiltInRegistries.ITEM, id));
            case "sound" -> new SoundEventSearchBox(registered(BuiltInRegistries.SOUND_EVENT, id));
            case "effect" -> new MobEffectSearchBox(id == null ? null : BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null));
            case "particle" -> new ParticleTypeSearchBox(registered(BuiltInRegistries.PARTICLE_TYPE, id))
                    .setCandidateFilter(particle -> particle instanceof SimpleParticleType);
            default -> null;
        };
    }

    private static <T> T registered(Registry<T> registry, ResourceLocation id) {
        return id != null && registry.containsKey(id) ? registry.get(id) : null;
    }

    private static <T> Configurator resourceEditor(String id, AiValue value, Consumer<AiValue> update, RegistrySearchBox<T> search) {
        var configurator = new Configurator();
        search.setId("npc_ai_resource_" + id);
        search.layout(l -> l.widthPercent(100));
        // 缺失模组的旧 ID 仍然显示并保留，直到作者主动选择新的候选项。
        if (search.getValue() == null && !value.text.isBlank()) {
            search.preview.addChild(new Label().setText(Component.literal(value.text)));
            search.textField.setText(value.text, false);
        }
        search.setOnValueChanged(selected -> {
            value.text = search.getIdString(selected);
            update.accept(value.copy());
        });
        configurator.addInlineChild(search);
        return configurator;
    }

    private static void refresh(AiGraphView view, AiNodeModel node) {
        view.inspectNode(node);
    }

}
