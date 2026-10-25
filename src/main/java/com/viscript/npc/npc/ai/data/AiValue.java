package com.viscript.npc.npc.ai.data;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;

/** 类型化的参数或变量值；实体引用只保存维度和 UUID，不持有实体。 */
public class AiValue implements IPersistedSerializable {
    @Persisted public String type = "number";
    @Persisted public AiEntityRef entity = new AiEntityRef();
    @Persisted public double number;
    @Persisted public boolean flag;
    @Persisted public String text = "";
    @Persisted public net.minecraft.world.phys.Vec3 position = net.minecraft.world.phys.Vec3.ZERO;

    public static AiValue number(double value) { var v = new AiValue(); v.number = value; return v; }
    public static AiValue text(String value) { var v = new AiValue(); v.type = "string"; v.text = value; return v; }
    public static AiValue choice(String value, java.util.List<String> choices) {
        var v = text(value); v.type = "choice:" + String.join("|", choices); return v;
    }
    public static AiValue mobType(String value) { var v = text(value); v.type = "mob_type"; return v; }
    public static java.util.List<String> choices(String type) {
        return type.startsWith("choice:") ? java.util.List.of(type.substring(7).split("\\|")) : java.util.List.of();
    }
    public boolean textual() { return type.equals("string") || type.equals("mob_type") || type.startsWith("choice:"); }
    public static AiValue bool(boolean value) { var v = new AiValue(); v.type = "boolean"; v.flag = value; return v; }
    public static AiValue position(net.minecraft.world.phys.Vec3 value) { var v = new AiValue(); v.type = "position"; v.position = value; return v; }
    public static AiValue entity(net.minecraft.world.entity.Entity entity) { return entity(AiEntityRef.of(entity)); }
    public static AiValue entity(AiEntityRef ref) { var v = new AiValue(); v.type = "entity"; v.entity = ref.copy(); return v; }
    public AiValue copy() { var v = new AiValue(); v.type = type; v.number = number; v.flag = flag; v.text = text; v.position = position; v.entity = entity.copy(); return v; }
    @Override public String toString() {
        if (textual()) return text;
        return switch (type) { case "boolean" -> Boolean.toString(flag);
            case "entity" -> entity.toString(); case "position" -> position.toString(); default -> Double.toString(number); };
    }
}
