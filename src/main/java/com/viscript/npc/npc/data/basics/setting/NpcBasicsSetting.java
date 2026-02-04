package com.viscript.npc.npc.data.basics.setting;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigColor;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSelector;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.data.INpcData;
import com.viscript.npc.util.RenderUtil;
import com.viscript.npc.util.common.StrUtil;
import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * npc基础设置
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NpcBasicsSetting implements INpcData {
    public static final StreamCodec<ByteBuf, NpcBasicsSetting> STREAM_CODEC;
    public static final Codec<NpcBasicsSetting> CODEC;
    @Configurable(name = "npcConfig.npcBasicsSetting.type",tips = "npcConfig.npcBasicsSetting.type.tips")
    private String type = "";
    @Configurable(name = "npcConfig.npcBasicsSetting.customName")
    private String customName = "ViScript NPC";
    @Configurable(name = "npcConfig.npcBasicsSetting.customSubName")
    private String customSubName = "";
    @Configurable(name = "npcConfig.npcBasicsSetting.nameVisible")
    private boolean nameVisible = true;
    @Configurable(name = "npcConfig.npcBasicsSetting.modeSize")
    @ConfigNumber(range = {0.0625, 16})
    private float modeSize = 1.0f;
    @Configurable(name = "npcConfig.npcBasicsSetting.skinColor")
    @ConfigColor
    private int skinColor = -1;
    @Configurable(name = "npcConfig.npcBasicsSetting.skinType")
    @ConfigSelector(subConfiguratorBuilder = "skinTypeSubConfiguratorBuilder")
    private SkinType skinType = SkinType.RESOURCE_PACK;
    @Persisted
    private String textureLocation = "textures/entity/player/slim/steve.png";
    @Persisted
    private String texturePlayerName = "";
    @Configurable(name = "npcConfig.npcBasicsSetting.capeTexture", tips = "npcConfig.npcBasicsSetting.capeTexture.tips")
    private String capeTexture = ViScriptNpc.id("textures/default_cape.png").toString();
    @Configurable(name = "npcConfig.npcBasicsSetting.invulnerable")
    private boolean invulnerable = true;
    @Configurable(name = "npcConfig.npcBasicsSetting.isNoAI")
    private boolean isNoAI = false;
    @Configurable(name = "npcConfig.npcBasicsSetting.isNoGravity")
    private boolean isNoGravity = false;

    public void skinTypeSubConfiguratorBuilder(SkinType skinType, ConfiguratorGroup group) {
        switch (skinType) {
            case RESOURCE_PACK ->
                    group.addConfigurator(new StringConfigurator("npcConfig.npcBasicsSetting.textureLocation", this::getTextureLocation, this::setTextureLocation, textureLocation, true)
                            .setResourceLocation(true));
            case PLAYER_NAME ->
                    group.addConfigurator(new StringConfigurator("npcConfig.npcBasicsSetting.texturePlayerName", this::getTexturePlayerName, this::setTexturePlayerName, texturePlayerName, true));
        }
    }

    static {
        CODEC = PersistedParser.createCodec(NpcBasicsSetting::new);
        STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }

    @Getter
    @AllArgsConstructor
    public enum SkinType implements StringRepresentable {
        RESOURCE_PACK("npcConfig.npcBasicsSetting.skinType.resourcePack"),
        PLAYER_NAME("npcConfig.npcBasicsSetting.skinType.playerName");

        private final String name;

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }

        public static ResourceLocation getSkinType(NpcBasicsSetting npcBasicsSetting) {
            return switch (npcBasicsSetting.skinType) {
                case RESOURCE_PACK ->
                        ResourceLocation.tryParse(StrUtil.cleanResource(npcBasicsSetting.textureLocation));
                case PLAYER_NAME -> RenderUtil.getSkin(npcBasicsSetting.texturePlayerName).texture();
            };
        }
    }
}
