package com.viscript.npc.npc.data.attributes;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSelector;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.search.IResultHandler;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Data
public class MeleeConfig implements INpcData {
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
    private String debuffEffect = MobEffects.SLOW_FALLING.getRegisteredName();
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
        SearchComponentConfigurator<MobEffect> mobEffectSearchComponentConfigurator = new SearchComponentConfigurator<>("npcConfig.npcAttributes.meleeConfig.debuffEffect",
                () -> BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.parse(debuffEffect)),
                effect -> {
                    ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
                    if (key != null) debuffEffect = key.toString();
                },
                Objects.requireNonNull(BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.parse(debuffEffect))),
                false,
                (word, searchHandler) -> {
                    String lowerWord = word.toLowerCase();
                    for (var key : BuiltInRegistries.MOB_EFFECT.keySet()) {
                        if (Thread.currentThread().isInterrupted()) return;
                        MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.get(key);
                        if (mobEffect != null && !mobEffect.isBeneficial() && (key.toString().toLowerCase().contains(lowerWord) || Component.translatable(mobEffect.getDescriptionId()).getString().toLowerCase().contains(lowerWord))) {
                            ((IResultHandler<MobEffect>) searchHandler).acceptResult(BuiltInRegistries.MOB_EFFECT.get(key));
                        }
                    }
                },
                (value) -> {
                    ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(value);
                    if (key != null) {
                        return key.toString();
                    }
                    return "";
                },
                value -> {
                    UIElementProvider<MobEffect> mobEffectUIElementProvider = UIElementProvider.iconText(
                            MobEffectTexture::new,
                            effect -> Component.translatable(effect.getDescriptionId())
                    );
                    return mobEffectUIElementProvider.createUI(value);
                }
        );
        mobEffectSearchComponentConfigurator.setTips("npcConfig.npcAttributes.meleeConfig.debuffEffect.tips");
        return mobEffectSearchComponentConfigurator;
    }

    public static void executeAdditionalEffects(CustomNpc npc, LivingEntity hurtEntity) {
        MeleeConfig meleeConfig = npc.getNpcAttributes().getMeleeConfig();
        switch (meleeConfig.getAdditionalEffects()) {
            case FIRE -> hurtEntity.igniteForSeconds((Float) meleeConfig.getSeconds());
            case POTION -> {
                BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(meleeConfig.getDebuffEffect())).ifPresent(mobEffect ->
                        hurtEntity.addEffect(new MobEffectInstance(mobEffect, (Integer) meleeConfig.getSeconds() * 20, (Integer) meleeConfig.getAmplifier(), false, false)));
            }
        }
    }

    @Getter
    @AllArgsConstructor
    public enum AdditionalEffects implements StringRepresentable {
        NONE(Component.translatable("npcConfig.npcAttributes.meleeConfig.additionalEffects.none").getString()),
        POTION(Component.translatable("npcConfig.npcAttributes.meleeConfig.additionalEffects.potion").getString()),
        FIRE(Component.translatable("npcConfig.npcAttributes.meleeConfig.additionalEffects.fire").getString());

        private final String name;

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }
    }
}
