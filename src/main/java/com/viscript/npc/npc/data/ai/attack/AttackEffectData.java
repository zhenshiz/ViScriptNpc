package com.viscript.npc.npc.data.ai.attack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public record AttackEffectData(Type type, String effectId, int seconds, int amplifier) {
    public static final AttackEffectData NONE = new AttackEffectData(Type.NONE, "minecraft:slowness", 0, 0);

    public static AttackEffectData fromTag(CompoundTag tag) {
        return new AttackEffectData(
                Type.parse(tag.getString("additional_effect")),
                tag.contains("effect_id") ? tag.getString("effect_id") : "minecraft:slowness",
                tag.getInt("effect_seconds"),
                Math.max(0, tag.getInt("effect_amplifier"))
        );
    }

    public void write(CompoundTag tag) {
        tag.putString("additional_effect", type.serializedName);
        tag.putString("effect_id", effectId);
        tag.putInt("effect_seconds", seconds);
        tag.putInt("effect_amplifier", amplifier);
    }

    public void apply(LivingEntity target) {
        switch (type) {
            case FIRE -> target.igniteForSeconds(seconds);
            case POTION -> {
                ResourceLocation id = ResourceLocation.tryParse(effectId);
                if (id != null) {
                    BuiltInRegistries.MOB_EFFECT.getHolder(id).ifPresent(effect ->
                            target.addEffect(new MobEffectInstance(effect, seconds * 20, amplifier, false, false)));
                }
            }
            case NONE -> {
            }
        }
    }

    public enum Type {
        NONE("none"),
        FIRE("fire"),
        POTION("potion");

        private final String serializedName;

        Type(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        public static Type parse(String value) {
            String normalized = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
            for (Type type : values()) {
                if (type.serializedName.equalsIgnoreCase(normalized) || type.name().equalsIgnoreCase(normalized)) {
                    return type;
                }
            }
            return NONE;
        }
    }
}
