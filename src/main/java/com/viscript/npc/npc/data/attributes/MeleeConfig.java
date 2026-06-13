package com.viscript.npc.npc.data.attributes;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSelector;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.RegistrySearchComponent;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.utils.LocalizationUtils;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.viscript.npc.gui.edit.texture.MobEffectTexture;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.data.INpcData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Data
public class MeleeConfig implements INpcData {
    private static final ResourceLocation DEFAULT_DEBUFF_EFFECT = ResourceLocation.withDefaultNamespace("slowness");

    @Configurable(name = "npcConfig.npcAttributes.meleeConfig.attackDamage")
    @ConfigNumber(range = {0, 2048}, wheel = 0.1)
    private double attackDamage = 2;
    @Configurable(name = "npcConfig.npcAttributes.meleeConfig.attackSpeed")
    @ConfigNumber(range = {1, Integer.MAX_VALUE}, wheel = 1)
    private int attackSpeed = 20;
    @Configurable(name = "npcConfig.npcAttributes.meleeConfig.attackRange")
    @ConfigNumber(range = {1, Integer.MAX_VALUE}, wheel = 1)
    private double attackRange = 2;
    @Configurable(name = "npcConfig.npcAttributes.meleeConfig.knockback")
    @ConfigNumber(range = {0, 5}, wheel = 0.1)
    private double knockback = 0;
    @Configurable(name = "npcConfig.npcAttributes.meleeConfig.additionalEffects")
    @ConfigSelector(subConfiguratorBuilder = "additionalEffectsSubConfiguratorBuilder")
    private AdditionalEffects additionalEffects = AdditionalEffects.NONE;
    @Persisted
    private String debuffEffect = DEFAULT_DEBUFF_EFFECT.toString();
    @Persisted
    private Number seconds = 0;
    @Persisted
    private Number amplifier = 0;

    public void additionalEffectsSubConfiguratorBuilder(AdditionalEffects additionalEffects, ConfiguratorGroup group) {
        switch (additionalEffects) {
            case POTION -> {
                group.addConfigurator(this.createMobEffectSearchComponentConfigurator());
                group.addConfigurator(new NumberConfigurator("npcConfig.npcAttributes.meleeConfig.seconds", this::getSeconds, this::setSeconds, seconds, true)
                        .setRange(-1, Integer.MAX_VALUE)
                        .setWheel(1)
                        .setTips("npcConfig.npcAttributes.meleeConfig.seconds.tips"));
                group.addConfigurator(new NumberConfigurator("npcConfig.npcAttributes.meleeConfig.amplifier", this::getAmplifier, this::setAmplifier, amplifier, true)
                        .setRange(0, Integer.MAX_VALUE)
                        .setWheel(1));
            }
            case FIRE -> {
                group.addConfigurator(new NumberConfigurator("npcConfig.npcAttributes.meleeConfig.seconds", this::getSeconds, this::setSeconds, seconds, true)
                        .setRange(-1, Integer.MAX_VALUE)
                        .setWheel(1)
                        .setTips("npcConfig.npcAttributes.meleeConfig.seconds.tips"));
            }
        }
    }

    private SearchComponentConfigurator<MobEffect> createMobEffectSearchComponentConfigurator() {
        RegistrySearchComponent<MobEffect> configurator = new RegistrySearchComponent<>(
                "npcConfig.npcAttributes.meleeConfig.debuffEffect",
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
        configurator.setTips("npcConfig.npcAttributes.meleeConfig.debuffEffect.tips");
        return configurator;
    }

    private MobEffect getDebuffEffectValue() {
        ResourceLocation key = ResourceLocation.tryParse(debuffEffect);
        MobEffect effect = key == null ? null : BuiltInRegistries.MOB_EFFECT.get(key);
        return effect == null ? BuiltInRegistries.MOB_EFFECT.get(DEFAULT_DEBUFF_EFFECT) : effect;
    }

    public static void executeAdditionalEffects(CustomNpc npc, LivingEntity hurtEntity) {
        MeleeConfig meleeConfig = npc.getNpcAttributes().getMeleeConfig();
        switch (meleeConfig.getAdditionalEffects()) {
            case FIRE -> hurtEntity.igniteForSeconds((float) (int) meleeConfig.getSeconds());
            case POTION -> {
                BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(meleeConfig.getDebuffEffect())).ifPresent(mobEffect ->
                        hurtEntity.addEffect(new MobEffectInstance(mobEffect, (Integer) meleeConfig.getSeconds() * 20, (Integer) meleeConfig.getAmplifier(), false, false)));
            }
        }
    }

    @Getter
    @AllArgsConstructor
    public enum AdditionalEffects implements StringRepresentable {
        NONE("npcConfig.npcAttributes.meleeConfig.additionalEffects.none"),
        POTION("npcConfig.npcAttributes.meleeConfig.additionalEffects.potion"),
        FIRE("npcConfig.npcAttributes.meleeConfig.additionalEffects.fire");

        private final String name;

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }
    }
}
