package com.viscript.npc.npc.data.inventory;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber.Type;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigSelector;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.math.Range;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.annotation.ReadOnlyManaged;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.mojang.serialization.Codec;
import com.viscript.npc.npc.data.INpcData;
import com.viscript.npc.util.ConfiguratorUtil;
import io.netty.buffer.ByteBuf;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NpcInventory implements INpcData {
    public static final StreamCodec<ByteBuf, NpcInventory> STREAM_CODEC;
    public static final Codec<NpcInventory> CODEC;
    @Configurable(name = "npcConfig.npcInventory.helmet")
    private ItemStack helmet = ItemStack.EMPTY;
    @Configurable(name = "npcConfig.npcInventory.chestplate")
    private ItemStack chestplate = ItemStack.EMPTY;
    @Configurable(name = "npcConfig.npcInventory.leggings")
    private ItemStack leggings = ItemStack.EMPTY;
    @Configurable(name = "npcConfig.npcInventory.boots")
    private ItemStack boots = ItemStack.EMPTY;
    @Configurable(name = "npcConfig.npcInventory.mainHand")
    private ItemStack mainHand = ItemStack.EMPTY;
    @Configurable(name = "npcConfig.npcInventory.offHand")
    private ItemStack offHand = ItemStack.EMPTY;
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

    private void LootTableTypeSubConfiguratorBuilder(LootTableType value, ConfiguratorGroup group) {
        switch (value) {
            case DATAPACK -> {
                group.addConfigurator(ConfiguratorUtil.createStrArrSearchComponentConfigurator("npcConfig.npcInventory.lootTable", new HashSet<>(),
                        //todo 同步服务端 loot table？
                        //Platform.getMinecraftServer().reloadableRegistries().getKeys(Registries.LOOT_TABLE).stream().map(ResourceLocation::toString).collect(Collectors.toSet()),
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
        DATAPACK("datapack"),
        CUSTOM("custom");

        private final String name;

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }
    }
}
