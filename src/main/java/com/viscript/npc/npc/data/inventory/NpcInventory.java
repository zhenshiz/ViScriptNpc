package com.viscript.npc.npc.data.inventory;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber.Type;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSelector;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.math.Range;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import com.viscript.npc.util.ConfiguratorUtil;
import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class NpcInventory implements IConfigurable, IPersistedSerializable {
    public static final StreamCodec<ByteBuf, NpcInventory> STREAM_CODEC;
    public static final Codec<NpcInventory> CODEC;
    @Persisted
    private String helmet;
    @Persisted
    private String chestplate;
    @Persisted
    private String leggings;
    @Persisted
    private String boots;
    @Persisted
    private String mainHand;
    @Persisted
    private String offHand;
    @Configurable(name = "npcConfig.npcInventory.levelRange")
    @ConfigNumber(range = {0, Integer.MAX_VALUE}, type = Type.FLOAT)
    private Range levelConfig = Range.of(0, 0);
    @Configurable(name = "npcConfig.npcInventory.lootTableType")
    @ConfigSelector(subConfiguratorBuilder = "LootTableTypeSubConfiguratorBuilder")
    private LootTableType lootTableType = LootTableType.CUSTOM;
    @Persisted
    private String lootTable = "";
    @Persisted
    private List<LootTableConfig> lootTables = new ArrayList<>();

    static {
        CODEC = PersistedParser.createCodec(NpcInventory::new);
        STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        father.addConfigurator(ConfiguratorUtil.createItemSearchComponentConfigurator("npcConfig.npcInventory.helmet", this::getHelmet, this::setHelmet));
        father.addConfigurator(ConfiguratorUtil.createItemSearchComponentConfigurator("npcConfig.npcInventory.chestplate", this::getChestplate, this::setChestplate));
        father.addConfigurator(ConfiguratorUtil.createItemSearchComponentConfigurator("npcConfig.npcInventory.leggings", this::getLeggings, this::setLeggings));
        father.addConfigurator(ConfiguratorUtil.createItemSearchComponentConfigurator("npcConfig.npcInventory.boots", this::getBoots, this::setBoots));
        father.addConfigurator(ConfiguratorUtil.createItemSearchComponentConfigurator("npcConfig.npcInventory.mainHand", this::getMainHand, this::setMainHand));
        father.addConfigurator(ConfiguratorUtil.createItemSearchComponentConfigurator("npcConfig.npcInventory.offHand", this::getOffHand, this::setOffHand));
        IConfigurable.super.buildConfigurator(father);
    }

    private void LootTableTypeSubConfiguratorBuilder(LootTableType value, ConfiguratorGroup group) {
        switch (value) {
            case DATAPACK -> {
                group.addConfigurator(ConfiguratorUtil.createStrArrSearchComponentConfigurator("npcConfig.npcInventory.lootTable",
                        Platform.getMinecraftServer().reloadableRegistries().getKeys(Registries.LOOT_TABLE).stream().map(ResourceLocation::toString).collect(Collectors.toSet()),
                        this::getLootTable,
                        this::setLootTable)
                );
            }
            case CUSTOM -> {
                ArrayConfiguratorGroup<LootTableConfig> lootTableConfigArrayConfiguratorGroup = new ArrayConfiguratorGroup<>("npcConfig.npcInventory.lootTables", true, this::getLootTables,
                        (getter, setter) -> {
                            LootTableConfig instance = getter.get();
                            return instance != null ? instance.createDirectConfigurator() : new Configurator();
                        }, false);
                lootTableConfigArrayConfiguratorGroup.setAddDefault(LootTableConfig::new);
                group.addConfigurator(lootTableConfigArrayConfiguratorGroup);
            }
        }
    }

    @Getter
    @AllArgsConstructor
    public enum LootTableType implements StringRepresentable {
        DATAPACK(Component.translatable("npcConfig.npcInventory.lootTableType.datapack").getString()),
        CUSTOM(Component.translatable("npcConfig.npcInventory.lootTableType.custom").getString());

        private final String name;

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }
    }
}
