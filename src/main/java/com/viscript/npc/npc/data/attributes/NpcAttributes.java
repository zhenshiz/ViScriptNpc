package com.viscript.npc.npc.data.attributes;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import com.viscript.npc.npc.data.INpcData;
import io.netty.buffer.ByteBuf;
import lombok.Data;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

@Data
public class NpcAttributes implements INpcData {
    public static final StreamCodec<ByteBuf, NpcAttributes> STREAM_CODEC;
    public static final Codec<NpcAttributes> CODEC;

    @Configurable(name = "npcConfig.npcAttributes.maxHealth")
    @ConfigNumber(range = {1, 1024}, wheel = 0.1)
    private double maxHealth = 20;
    @Configurable(name = "npcConfig.npcAttributes.movementSpeed")
    @ConfigNumber(range = {0, 1024}, wheel = 0.1)
    private double movementSpeed = 0.7;
    @Configurable(name = "npcConfig.npcAttributes.followRange")
    @ConfigNumber(range = {0, 2048}, wheel = 0.1)
    private double followRange = 32;
    @Configurable(name = "npcConfig.npcAttributes.meleeConfig", subConfigurable = true)
    private MeleeConfig meleeConfig = new MeleeConfig();
    @Configurable(name = "npcConfig.npcAttributes.resistanceConfig", subConfigurable = true)
    private ResistanceConfig resistanceConfig = new ResistanceConfig();
    @Configurable(name = "npcConfig.npcAttributes.defenseConfig", subConfigurable = true)
    private DefenseConfig defenseConfig = new DefenseConfig();
    //是否免疫火焰
    @Configurable(name = "npcConfig.npcAttributes.isImmuneToFire")
    private boolean isImmuneToFire = false;
    //是否会溺水
    @Configurable(name = "npcConfig.npcAttributes.isCanDrown")
    private boolean isCanDrown = false;
    //是否免疫药水
    @Configurable(name = "npcConfig.npcAttributes.isPotionImmune")
    private boolean isPotionImmune = false;
    //是否有摔落伤害
    @Configurable(name = "npcConfig.npcAttributes.isFallDamage")
    private boolean isFallDamage = false;
    //是否会在白天自燃
    @Configurable(name = "npcConfig.npcAttributes.isBurnInDay")
    private boolean isBurnInDay = false;
    //是否忽略蜘蛛网的影响
    @Configurable(name = "npcConfig.npcAttributes.isIgnoreCobweb")
    private boolean isIgnoreCobweb = false;
    //战时回血
    @Configurable(name = "npcConfig.npcAttributes.combatRegenRate", tips = "npcConfig.npcAttributes.regenRate.tips")
    @ConfigNumber(range = {0, Integer.MAX_VALUE}, wheel = 0.1)
    private float combatRegenRate = 0;
    //脱战回血
    @Configurable(name = "npcConfig.npcAttributes.outOfCombatRegenRate", tips = "npcConfig.npcAttributes.regenRate.tips")
    @ConfigNumber(range = {0, Integer.MAX_VALUE}, wheel = 0.1)
    private float outOfCombatRegenRate = 0;

    static {
        CODEC = PersistedParser.createCodec(NpcAttributes::new);
        STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }
}
