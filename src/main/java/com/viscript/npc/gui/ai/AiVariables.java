package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandle;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandleHelpers;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.ModifierFlags;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableScope;
import com.viscript.npc.npc.ai.data.AiValue;
import com.viscript.npc.npc.ai.data.AiVariable;
import com.viscript.npc.npc.ai.runtime.AiOperations;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 将 LDLib2 的类型化变量声明编译为服务端的行为输入、局部变量或 NPC 状态。 */
public final class AiVariables {
    /**
     * 为坐标类型提供 LDLib2 默认值和编辑器配置器。
     */
    public static final TypeHandle POSITION = TypeHandleHelpers.fromType(Vec3.class, "Vec3");
    public static final TypeHandle ENTITY = TypeHandleHelpers.fromType(com.viscript.npc.npc.ai.data.AiEntityRef.class, Component.translatable("viscript_npc.ai.type.entity").getString());
    private static final java.util.Map<String, TypeHandle> DOMAIN_TYPES = new java.util.LinkedHashMap<>();
    public static final List<TypeHandle> TYPES;

    static {
        TypeHandleHelpers.setCustomDefaultValue(POSITION, () -> Vec3.ZERO);
        TypeHandleHelpers.setCustomConfigurable(POSITION, (valueConfigurable, ignored) ->
                IConfigurable.create(group -> group.addConfigurator(positionConfigurator(valueConfigurable))));
        TypeHandleHelpers.setCustomDefaultValue(ENTITY, com.viscript.npc.npc.ai.data.AiEntityRef::new);
        TypeHandleHelpers.setCustomConfigurable(ENTITY, (value, ignored) -> IConfigurable.create(group ->
                group.addConfigurator(AiInspector.valueEditor("recipient", AiValue.entity(value.getValue() instanceof
                        com.viscript.npc.npc.ai.data.AiEntityRef ref ? ref : new com.viscript.npc.npc.ai.data.AiEntityRef()), List.of(), changed -> {
                    value.setValue(changed.entity.copy()); value.notifyValueChanged();
                }))));
        var types = new java.util.ArrayList<>(List.of(TypeHandles.DOUBLE, TypeHandles.BOOL, TypeHandles.STRING, POSITION, ENTITY));
        for (var operation : AiOperations.all()) for (var field : operation.fields()) {
            if (!field.initial().type.startsWith("choice:") && !field.initial().type.equals("mob_type")) continue;
            String id = field.initial().type;
            if (DOMAIN_TYPES.containsKey(id)) continue;
            var handle = TypeHandleHelpers.customType(String.class, "viscript_npc:" + id,
                    Component.translatable("viscript_npc.ai.field." + field.id()).getString());
            DOMAIN_TYPES.put(id, handle);
            TypeHandleHelpers.setCustomDefaultValue(handle, () -> field.initial().text);
            TypeHandleHelpers.setCustomConfigurable(handle, (value, ignored) -> IConfigurable.create(group -> {
                var initial = field.initial().copy();
                if (value.getValue() instanceof String text) initial.text = text;
                group.addConfigurator(AiInspector.valueEditor(field.id(), initial, field.choices(), changed -> {
                    value.setValue(changed.text); value.notifyValueChanged();
                }));
            }));
            types.add(handle);
        }
        TYPES = List.copyOf(types);
    }

    private static Configurator positionConfigurator(IFieldValueConfigurable field) {
        var configurator = new Configurator("");
        var x = new NumberConfigurator("x", () -> position(field).x,
                value -> setPosition(field, new Vec3(value.doubleValue(), position(field).y, position(field).z)),
                0D, field.forceUpdate());
        var y = new NumberConfigurator("y", () -> position(field).y,
                value -> setPosition(field, new Vec3(position(field).x, value.doubleValue(), position(field).z)),
                0D, field.forceUpdate());
        var z = new NumberConfigurator("z", () -> position(field).z,
                value -> setPosition(field, new Vec3(position(field).x, position(field).y, value.doubleValue())),
                0D, field.forceUpdate());
        configurator.inlineContainer.addChildren(x, y, z).layout(layout -> {
            layout.gapAll(2);
            layout.marginLeft(2);
            layout.flexDirection(FlexDirection.ROW);
            layout.wrap(FlexWrap.WRAP);
        });
        x.layout(layout -> { layout.flex(1); layout.minWidth(40); layout.height(14); });
        y.layout(layout -> { layout.flex(1); layout.minWidth(40); layout.height(14); });
        z.layout(layout -> { layout.flex(1); layout.minWidth(40); layout.height(14); });
        configurator.setCopiable(() -> {
            var current = position(field);
            return () -> new Vec3(current.x, current.y, current.z);
        });
        configurator.setPastable(Vec3.class, value -> setPosition(field, value));
        return configurator;
    }

    private static Vec3 position(IFieldValueConfigurable field) {
        var value = field.getValue();
        return value instanceof Vec3 vec3 ? vec3 : Vec3.ZERO;
    }

    private static void setPosition(IFieldValueConfigurable field, Vec3 value) {
        field.setValue(value);
        field.notifyValueChanged();
    }

    public static TypeHandle type(String type) {
        if (DOMAIN_TYPES.containsKey(type)) return DOMAIN_TYPES.get(type);
        return switch (type) {
            case "boolean" -> TypeHandles.BOOL;
            case "string" -> TypeHandles.STRING;
            case "entity" -> ENTITY;
            case "position" -> POSITION;
            default -> TypeHandles.DOUBLE;
        };
    }

    public static Object value(AiValue value) {
        if (value.textual()) return value.text;
        return switch (value.type) {
            case "boolean" -> value.flag;
            case "string" -> value.text;
            case "entity" -> value.entity.copy();
            case "position" -> value.position;
            default -> value.number;
        };
    }

    public static AiVariable compile(VariableDeclarationModelBase declaration) {
        var result = new AiVariable();
        result.id = declaration.getUid().toString();
        result.name = declaration.getName();
        if (declaration.getGraphModel() instanceof AiGraphModel graph) {
            var presentation = graph.variablePresentation.get(result.id);
            if (presentation != null) {
                result.displayNameKey = presentation.displayNameKey;
                result.tipsKey = presentation.tipsKey;
            }
        }
        var constant = declaration.getInitializationModel();
        Object value = constant == null ? declaration.getDataTypeHandle().getDefaultValue() : constant.getValue();
        var type = declaration.getDataTypeHandle();
        if (DOMAIN_TYPES.containsValue(type)) {
            result.initial = AiValue.text(value instanceof String text ? text : "");
            result.initial.type = DOMAIN_TYPES.entrySet().stream().filter(e -> e.getValue().equals(type)).findFirst().orElseThrow().getKey();
        }
        else if (type.equals(TypeHandles.BOOL)) result.initial = AiValue.bool(Boolean.TRUE.equals(value));
        else if (type.equals(TypeHandles.STRING)) result.initial = AiValue.text(value instanceof String text ? text : "");
        else if (type.equals(ENTITY)) result.initial = AiValue.entity(value instanceof com.viscript.npc.npc.ai.data.AiEntityRef ref ? ref : new com.viscript.npc.npc.ai.data.AiEntityRef());
        else if (type.equals(POSITION)) result.initial = AiValue.position(value instanceof Vec3 position ? position : Vec3.ZERO);
        else {
            result.initial = AiValue.number(value instanceof Number number ? number.doubleValue() : 0);
            if (!type.equals(TypeHandles.DOUBLE)) result.initial.type = type.getIdentification();
        }
        boolean compatible = DOMAIN_TYPES.containsValue(type) || type.equals(TypeHandles.STRING) ? value instanceof String
                : type.equals(TypeHandles.BOOL) ? value instanceof Boolean
                : type.equals(ENTITY) ? value instanceof com.viscript.npc.npc.ai.data.AiEntityRef
                : type.equals(POSITION) ? value instanceof Vec3 : value instanceof Number;
        if (!compatible || constant != null && constant.isDeserializeFailed()) result.initial.type = "invalid";
        return result;
    }

    public static String scope(VariableDeclarationModelBase declaration, String kind) {
        if (kind.equals("tree")) return "blackboard";
        if (declaration.getModifiers() == ModifierFlags.READ) return "parameter";
        return declaration.getScope() == VariableScope.EXPOSED ? "blackboard" : "local";
    }

    /**
     * 解析变量的本地化名称，并在翻译缺失时回退到内部名称。
     *
     * @param variable 包含内部名称和可选翻译键的变量
     * @return 用于参数标签和变量选择器的名称组件
     */
    public static Component displayName(AiVariable variable) {
        return variable.displayNameKey.isBlank() ? Component.literal(variable.name)
                : Component.translatableWithFallback(variable.displayNameKey, variable.name);
    }

    /**
     * 解析可选的变量用途说明，省略尚未提供翻译的说明。
     *
     * @param variable 包含可选说明翻译键的变量
     * @return 包含用途说明或为空的悬浮提示
     */
    public static Tooltips tips(AiVariable variable) {
        if (variable.tipsKey.isBlank()) return Tooltips.empty();
        var description = Component.translatableWithFallback(variable.tipsKey, "");
        return description.getString().isBlank() ? Tooltips.empty() : Tooltips.of(description);
    }

    public static void setScope(VariableDeclarationModelBase declaration, String scope) {
        declaration.setScope(scope.equals("blackboard") ? VariableScope.EXPOSED : VariableScope.LOCAL);
        declaration.setModifiers(scope.equals("parameter") ? ModifierFlags.READ : ModifierFlags.NONE);
    }
}
