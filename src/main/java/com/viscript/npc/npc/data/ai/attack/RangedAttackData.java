package com.viscript.npc.npc.data.ai.attack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record RangedAttackData(double range, int cooldownTicks, double damage, double knockback,
                               float projectileSpeed, float inaccuracy, int projectilesPerShot,
                               float visualScale, boolean affectedByGravity, String projectileItem,
                               String shootSound, String hitSound, float explosionPower,
                               boolean explosionBreaksBlocks, String trailType, AttackEffectData effect) {
    public RangedAttackData {
        range = Math.max(0.0D, range);
        cooldownTicks = Math.max(1, cooldownTicks);
        damage = Math.max(0.0D, damage);
        knockback = Math.max(0.0D, knockback);
        projectileSpeed = Math.max(0.1F, projectileSpeed);
        inaccuracy = Math.max(0.0F, inaccuracy);
        projectilesPerShot = Math.max(1, Math.min(64, projectilesPerShot));
        visualScale = Math.max(0.1F, visualScale);
        projectileItem = projectileItem == null || projectileItem.isBlank() ? "minecraft:arrow" : projectileItem;
        shootSound = shootSound == null ? "" : shootSound;
        hitSound = hitSound == null ? "" : hitSound;
        explosionPower = Math.max(0.0F, explosionPower);
        trailType = trailType == null ? "none" : trailType;
        effect = effect == null ? AttackEffectData.NONE : effect;
    }

    public static RangedAttackData fromTag(CompoundTag tag) {
        return new RangedAttackData(
                tag.contains("range") ? tag.getDouble("range") : 16.0D,
                tag.contains("cooldown") ? tag.getInt("cooldown") : 20,
                tag.contains("damage") ? tag.getDouble("damage") : 2.0D,
                tag.getDouble("knockback"),
                tag.contains("projectile_speed") ? tag.getFloat("projectile_speed") : 1.6F,
                tag.getFloat("inaccuracy"),
                tag.contains("projectiles_per_shot") ? tag.getInt("projectiles_per_shot") : 1,
                tag.contains("visual_scale") ? tag.getFloat("visual_scale") : 1.0F,
                !tag.contains("affected_by_gravity") || tag.getBoolean("affected_by_gravity"),
                tag.contains("projectile_item") ? tag.getString("projectile_item") : "minecraft:arrow",
                tag.contains("shoot_sound") ? tag.getString("shoot_sound") : "minecraft:entity.skeleton.shoot",
                tag.contains("hit_sound") ? tag.getString("hit_sound") : "minecraft:entity.arrow.hit",
                tag.getFloat("explosion_power"),
                tag.getBoolean("explosion_breaks_blocks"),
                tag.contains("trail_type") ? tag.getString("trail_type") : "none",
                AttackEffectData.fromTag(tag)
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("range", range);
        tag.putInt("cooldown", cooldownTicks);
        tag.putDouble("damage", damage);
        tag.putDouble("knockback", knockback);
        tag.putFloat("projectile_speed", projectileSpeed);
        tag.putFloat("inaccuracy", inaccuracy);
        tag.putInt("projectiles_per_shot", projectilesPerShot);
        tag.putFloat("visual_scale", visualScale);
        tag.putBoolean("affected_by_gravity", affectedByGravity);
        tag.putString("projectile_item", projectileItem);
        tag.putString("shoot_sound", shootSound);
        tag.putString("hit_sound", hitSound);
        tag.putFloat("explosion_power", explosionPower);
        tag.putBoolean("explosion_breaks_blocks", explosionBreaksBlocks);
        tag.putString("trail_type", trailType);
        effect.write(tag);
        return tag;
    }

    public ItemStack projectileItemStack() {
        ResourceLocation id = ResourceLocation.tryParse(projectileItem);
        Item item = id == null ? Items.ARROW : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.ARROW);
        return new ItemStack(item);
    }

    public SoundEvent shootSoundEvent() {
        return soundOrDefault(shootSound, SoundEvents.SKELETON_SHOOT);
    }

    public SoundEvent hitSoundEvent() {
        return soundOrDefault(hitSound, SoundEvents.ARROW_HIT);
    }

    public static SoundEvent soundOrDefault(String soundId, SoundEvent fallback) {
        ResourceLocation id = ResourceLocation.tryParse(soundId == null ? "" : soundId);
        return id == null ? fallback : BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(fallback);
    }
}
