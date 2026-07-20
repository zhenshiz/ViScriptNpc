package com.viscript.npc.npc.data.attributes;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSelector;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.RegistrySearchComponent;
import com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.LocalizationUtils;
import com.viscript.npc.gui.edit.texture.MobEffectTexture;
import com.viscript.npc.npc.data.INpcData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Data
public class RangedConfig implements INpcData {
    private static final ResourceLocation DEFAULT_DEBUFF_EFFECT = ResourceLocation.withDefaultNamespace("slowness");
    private static final String DEFAULT_SHOOT_SOUND = "minecraft:entity.skeleton.shoot";
    private static final String DEFAULT_HIT_SOUND = "minecraft:entity.arrow.hit";

    @Configurable(name = "npcConfig.npcAttributes.rangedConfig.damage")
    @ConfigNumber(range = {0, 2048}, wheel = 0.1)
    private double damage = 2;
    @Configurable(name = "npcConfig.npcAttributes.rangedConfig.knockback")
    @ConfigNumber(range = {0, 5}, wheel = 0.1)
    private double knockback = 0;
    @Configurable(name = "npcConfig.npcAttributes.rangedConfig.speed")
    @ConfigNumber(range = {0.1, 16}, wheel = 0.1)
    private float speed = 1.6f;
    @Configurable(name = "npcConfig.npcAttributes.rangedConfig.visualScale")
    @ConfigNumber(range = {0.1, 8}, wheel = 0.1)
    private float visualScale = 1.0f;
    @Configurable(name = "npcConfig.npcAttributes.rangedConfig.affectedByGravity")
    private boolean affectedByGravity = true;
    @Configurable(name = "npcConfig.npcAttributes.rangedConfig.projectileItem")
    private ItemStack projectileItem = new ItemStack(Items.ARROW);
    @Configurable(name = "npcConfig.npcAttributes.rangedConfig.shootSound")
    private String shootSound = DEFAULT_SHOOT_SOUND;
    @Configurable(name = "npcConfig.npcAttributes.rangedConfig.hitSound")
    private String hitSound = DEFAULT_HIT_SOUND;
    @Configurable(name = "npcConfig.npcAttributes.rangedConfig.explosionPower")
    @ConfigNumber(range = {0, 32}, wheel = 0.1)
    private float explosionPower = 0;
    @Configurable(name = "npcConfig.npcAttributes.rangedConfig.explosionBreaksBlocks")
    private boolean explosionBreaksBlocks = false;
    @Configurable(name = "npcConfig.npcAttributes.rangedConfig.trailType")
    private TrailType trailType = TrailType.NONE;
    @Configurable(name = "npcConfig.npcAttributes.rangedConfig.additionalEffects")
    @ConfigSelector(subConfiguratorBuilder = "additionalEffectsSubConfiguratorBuilder")
    private AdditionalEffects additionalEffects = AdditionalEffects.NONE;
    @Persisted
    private String debuffEffect = DEFAULT_DEBUFF_EFFECT.toString();
    @Persisted
    private Number seconds = 0;
    @Persisted
    private Number amplifier = 0;

    public ItemStack getProjectileItemForDisplay() {
        return projectileItem == null || projectileItem.isEmpty() ? new ItemStack(Items.ARROW) : projectileItem.copyWithCount(1);
    }

    public SoundEvent getShootSoundEvent() {
        return soundOrDefault(shootSound, SoundEvents.SKELETON_SHOOT);
    }

    public SoundEvent getHitSoundEvent() {
        return soundOrDefault(hitSound, SoundEvents.ARROW_HIT);
    }

    public static SoundEvent soundOrDefault(String soundId, SoundEvent fallback) {
        ResourceLocation key = ResourceLocation.tryParse(soundId == null ? "" : soundId);
        return key == null ? fallback : BuiltInRegistries.SOUND_EVENT.getOptional(key).orElse(fallback);
    }

    public void additionalEffectsSubConfiguratorBuilder(AdditionalEffects additionalEffects, ConfiguratorGroup group) {
        switch (additionalEffects) {
            case POTION -> {
                group.addConfigurator(this.createMobEffectSearchComponentConfigurator());
                group.addConfigurator(new NumberConfigurator("npcConfig.npcAttributes.rangedConfig.seconds", this::getSeconds, this::setSeconds, seconds, true)
                        .setRange(-1, Integer.MAX_VALUE)
                        .setWheel(1)
                        .setTips("npcConfig.npcAttributes.rangedConfig.seconds.tips"));
                group.addConfigurator(new NumberConfigurator("npcConfig.npcAttributes.rangedConfig.amplifier", this::getAmplifier, this::setAmplifier, amplifier, true)
                        .setRange(0, Integer.MAX_VALUE)
                        .setWheel(1));
            }
            case FIRE -> group.addConfigurator(new NumberConfigurator("npcConfig.npcAttributes.rangedConfig.seconds", this::getSeconds, this::setSeconds, seconds, true)
                    .setRange(-1, Integer.MAX_VALUE)
                    .setWheel(1)
                    .setTips("npcConfig.npcAttributes.rangedConfig.seconds.tips"));
        }
    }

    private SearchComponentConfigurator<MobEffect> createMobEffectSearchComponentConfigurator() {
        RegistrySearchComponent<MobEffect> configurator = new RegistrySearchComponent<>(
                "npcConfig.npcAttributes.rangedConfig.debuffEffect",
                this::getDebuffEffectValue,
                effect -> debuffEffect = Objects.toString(BuiltInRegistries.MOB_EFFECT.getKey(effect), DEFAULT_DEBUFF_EFFECT.toString()),
                getDebuffEffectValue(),
                false,
                BuiltInRegistries.MOB_EFFECT,
                UIElementProvider.iconText(
                        MobEffectTexture::new,
                        effect -> Component.translatable(effect.getDescriptionId())
                )
        );
        configurator.setFilter(effect -> effect != null && !effect.isBeneficial());
        configurator.setTranslator(effect -> LocalizationUtils.format(effect.getDescriptionId()));
        configurator.setTips("npcConfig.npcAttributes.rangedConfig.debuffEffect.tips");
        return configurator;
    }

    private MobEffect getDebuffEffectValue() {
        ResourceLocation key = ResourceLocation.tryParse(debuffEffect);
        MobEffect effect = key == null ? null : BuiltInRegistries.MOB_EFFECT.get(key);
        return effect == null ? BuiltInRegistries.MOB_EFFECT.get(DEFAULT_DEBUFF_EFFECT) : effect;
    }

    @Getter
    @AllArgsConstructor
    public enum AdditionalEffects implements StringRepresentable {
        NONE("npcConfig.npcAttributes.rangedConfig.additionalEffects.none"),
        POTION("npcConfig.npcAttributes.rangedConfig.additionalEffects.potion"),
        FIRE("npcConfig.npcAttributes.rangedConfig.additionalEffects.fire");

        private final String name;

        public static AdditionalEffects bySerializedName(String name) {
            for (AdditionalEffects effect : values()) {
                if (effect.getSerializedName().equals(name) || effect.name().equalsIgnoreCase(name)) {
                    return effect;
                }
            }
            return NONE;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }
    }

    @Getter
    @AllArgsConstructor
    public enum TrailType implements StringRepresentable {
        NONE("npcConfig.npcAttributes.rangedConfig.trailType.none"),
        SMOKE("npcConfig.npcAttributes.rangedConfig.trailType.smoke"),
        FLAME("npcConfig.npcAttributes.rangedConfig.trailType.flame"),
        MAGIC("npcConfig.npcAttributes.rangedConfig.trailType.magic"),
        CRIT("npcConfig.npcAttributes.rangedConfig.trailType.crit");

        private final String name;

        public static TrailType bySerializedName(String name) {
            for (TrailType trailType : values()) {
                if (trailType.getSerializedName().equals(name) || trailType.name().equalsIgnoreCase(name)) {
                    return trailType;
                }
            }
            return NONE;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }
    }
}
