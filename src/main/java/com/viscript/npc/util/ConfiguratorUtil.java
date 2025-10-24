package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.utils.search.IResultHandler;
import com.viscript.npc.util.common.BeanUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 预设的配置组件
 */
public class ConfiguratorUtil {
    public static SearchComponentConfigurator<Item> createItemSearchComponentConfigurator(String name, Supplier<String> getter, Consumer<String> setter) {
        SearchComponentConfigurator<Item> itemSearchComponentConfigurator = new SearchComponentConfigurator<>(name,
                () -> {
                    String id = getter.get();
                    return id != null ? BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)) : Items.AIR;
                },
                item -> {
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                    setter.accept(key.toString());
                },
                BuiltInRegistries.ITEM.get(ResourceLocation.parse(
                        getter.get() != null ? getter.get() : Items.AIR.toString()
                )),
                false,
                (word, searchHandler) -> {
                    String lowerWord = word.toLowerCase();
                    for (var key : BuiltInRegistries.ITEM.keySet()) {
                        if (Thread.currentThread().isInterrupted()) return;
                        Item item = BuiltInRegistries.ITEM.get(key);
                        if (key.toString().toLowerCase().contains(lowerWord) || Component.translatable(item.getDescriptionId()).getString().toLowerCase().contains(lowerWord)) {
                            ((IResultHandler<Item>) searchHandler).acceptResult(BuiltInRegistries.ITEM.get(key));
                        }
                    }
                },
                value -> BuiltInRegistries.ITEM.getKey(value).toString(),
                value -> ""
        );
        itemSearchComponentConfigurator.searchComponent.setCandidateUIProvider(UIElementProvider.iconText(
                ItemStackTexture::new,
                item -> Component.translatable(item.getDescriptionId())
        ));
        return itemSearchComponentConfigurator;
    }

    public static SearchComponentConfigurator<String> createStrArrSearchComponentConfigurator(String name, Set<String> strArr, Supplier<String> getter, Consumer<String> setter) {
        return new SearchComponentConfigurator<>(name,
                getter,
                setter,
                BeanUtil.getValueOrDefault(getter.get(), ""),
                false,
                (word, searchHandler) -> {
                    String lowerWord = word.toLowerCase();
                    for (var key : strArr) {
                        if (Thread.currentThread().isInterrupted()) return;
                        if (key.toLowerCase().contains(lowerWord)) {
                            ((IResultHandler<String>) searchHandler).acceptResult(key);
                        }
                    }
                },
                (value) -> BeanUtil.getValueOrDefault(value, ""),
                value -> value
        );
    }
}
