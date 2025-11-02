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
import com.lowdragmc.lowdraglib2.syncdata.annotation.ReadOnlyManaged;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import com.viscript.npc.util.ConfiguratorUtil;
import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NpcInventory implements IConfigurable, IPersistedSerializable {
    public static final StreamCodec<ByteBuf, NpcInventory> STREAM_CODEC;
    public static final Codec<NpcInventory> CODEC;
    @Persisted
    private String helmet = Items.AIR.toString();
    @Persisted
    private String chestplate = Items.AIR.toString();
    @Persisted
    private String leggings = Items.AIR.toString();
    @Persisted
    private String boots = Items.AIR.toString();
    @Persisted
    private String mainHand = Items.AIR.toString();
    @Persisted
    private String offHand = Items.AIR.toString();
    @Configurable(name = "npcConfig.npcInventory.levelRange")
    @ConfigNumber(range = {0, Integer.MAX_VALUE}, type = Type.INTEGER)
    private Range levelRange = Range.of(0, 0);
    @Configurable(name = "npcConfig.npcInventory.lootTableType")
    @ConfigSelector(subConfiguratorBuilder = "LootTableTypeSubConfiguratorBuilder")
    private LootTableType lootTableType = LootTableType.CUSTOM;
    @Persisted
    private String lootTable = "";
    @Persisted
    @ReadOnlyManaged(serializeMethod = "writeLootTables", deserializeMethod = "readLootTables")
    private List<LootTableConfig> lootTables = new ArrayList<>();

    static {
        CODEC = PersistedParser.createCodec(NpcInventory::new);
        STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        father.addConfigurator(ConfiguratorUtil.createItemSearchComponentConfigurator("npcConfig.npcInventory.helmet", this::getHelmet, this::setHelmet));
        father.addConfigurator(ConfiguratorUtil.createItemSearchComponentConfigurator("npcConfig.npcInventory.chestplate", this::getChestplate, this::setChestplate, ItemTags.CHEST_ARMOR));
        father.addConfigurator(ConfiguratorUtil.createItemSearchComponentConfigurator("npcConfig.npcInventory.leggings", this::getLeggings, this::setLeggings, ItemTags.LEG_ARMOR));
        father.addConfigurator(ConfiguratorUtil.createItemSearchComponentConfigurator("npcConfig.npcInventory.boots", this::getBoots, this::setBoots, ItemTags.FOOT_ARMOR));
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
                ArrayConfiguratorGroup<LootTableConfig> lootTableConfigArrayConfiguratorGroup = new ArrayConfiguratorGroup<>("npcConfig.npcInventory.lootTables", false,
                        () -> new ArrayList<>(this.getLootTables()),
                        (getter, setter) -> {
                            LootTableConfig instance = getter.get();
                            return instance != null ? instance.createDirectConfigurator() : new Configurator();
                        }, true);
                lootTableConfigArrayConfiguratorGroup.setAddDefault(LootTableConfig::new);
                lootTableConfigArrayConfiguratorGroup.setOnUpdate(list -> {
                    List<LootTableConfig> origin = this.getLootTables();
                    origin.clear();
                    origin.addAll(list);
                });
                group.addConfigurator(lootTableConfigArrayConfiguratorGroup);
            }
        }
    }

    private IntTag writeLootTables(List<LootTableConfig> value) {
        return IntTag.valueOf(value.size());
    }

    private List<LootTableConfig> readLootTables(IntTag tag) {
        var groups = new ArrayList<LootTableConfig>();
        for (int i = 0; i < tag.getAsInt(); i++) {
            groups.add(new LootTableConfig());
        }
        return groups;
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
