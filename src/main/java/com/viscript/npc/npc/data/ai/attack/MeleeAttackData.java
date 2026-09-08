package com.viscript.npc.npc.data.ai.attack;

import net.minecraft.nbt.CompoundTag;

public record MeleeAttackData(float damage, double range, int cooldownTicks, double knockback,
                              AttackEffectData effect) {
    public static final float DEFAULT_DAMAGE = 2.0F;
    public static final double DEFAULT_RANGE = 2.0D;
    public static final int DEFAULT_COOLDOWN = 20;

    public MeleeAttackData {
        damage = Math.max(0.0F, damage);
        range = Math.max(0.0D, range);
        cooldownTicks = Math.max(1, cooldownTicks);
        knockback = Math.max(0.0D, knockback);
        effect = effect == null ? AttackEffectData.NONE : effect;
    }

    public static MeleeAttackData fromTag(CompoundTag tag) {
        return new MeleeAttackData(
                tag.contains("damage") ? tag.getFloat("damage") : DEFAULT_DAMAGE,
                tag.contains("range") ? tag.getDouble("range") : DEFAULT_RANGE,
                tag.contains("cooldown") ? tag.getInt("cooldown") : DEFAULT_COOLDOWN,
                tag.getDouble("knockback"),
                AttackEffectData.fromTag(tag)
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("damage", damage);
        tag.putDouble("range", range);
        tag.putInt("cooldown", cooldownTicks);
        tag.putDouble("knockback", knockback);
        effect.write(tag);
        return tag;
    }
}
