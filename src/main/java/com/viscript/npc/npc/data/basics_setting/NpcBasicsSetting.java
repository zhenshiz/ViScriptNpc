package com.viscript.npc.npc.data.basics_setting;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigColor;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSelector;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.data.INpcData;
import com.viscript.npc.util.ConfiguratorUtil;
import com.viscript.npc.util.RenderUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * npc基础设置
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@LDLRegister(name = "npc_basics_setting", registry = INpcData.ID)
public class NpcBasicsSetting implements INpcData {
    @Configurable(name = "npcConfig.npcBasicsSetting.type",tips = "npcConfig.npcBasicsSetting.type.tips")
    private String npcId = "";
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
    private ResourceLocation textureLocation = ResourceLocation.withDefaultNamespace("textures/entity/player/slim/steve.png");
    @Persisted
    private String texturePlayerName = "";
    @Configurable(name = "npcConfig.npcBasicsSetting.capeTexture", tips = "npcConfig.npcBasicsSetting.capeTexture.tips")
    private ResourceLocation capeTexture = ViScriptNpc.id("textures/default_cape.png");
    @Configurable(name = "npcConfig.npcBasicsSetting.invulnerable")
    private boolean invulnerable = true;
    @Configurable(name = "npcConfig.npcBasicsSetting.isNoGravity")
    private boolean isNoGravity = false;

    public void skinTypeSubConfiguratorBuilder(SkinType skinType, ConfiguratorGroup group) {
        switch (skinType) {
            case RESOURCE_PACK ->
                    group.addConfigurator(ConfiguratorUtil.ofResourceLocation("npcConfig.npcBasicsSetting.textureLocation", this::getTextureLocation, this::setTextureLocation, textureLocation, true));
            case PLAYER_NAME ->
                    group.addConfigurator(new StringConfigurator("npcConfig.npcBasicsSetting.texturePlayerName", this::getTexturePlayerName, this::setTexturePlayerName, texturePlayerName, true));
        }
    }

    public ResourceLocation getSkinTexture() {
        return switch (skinType) {
            case RESOURCE_PACK -> textureLocation;
            case PLAYER_NAME -> RenderUtil.getSkin(texturePlayerName).texture();
        };
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
    }
}
