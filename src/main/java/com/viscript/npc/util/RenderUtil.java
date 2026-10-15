package com.viscript.npc.util;

import com.google.common.hash.Hashing;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.viscript.npc.ViScriptNpc;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ResolvableProfile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * 提供客户端玩家头像绘制，以及正版玩家和 URL 皮肤的加载。
 */
public class RenderUtil {
    private static final Minecraft minecraft = Minecraft.getInstance();
    private static final Map<String, PlayerSkin> skins = new HashMap<>();

    @Info("""
            玩家头像
            
            GuiGraphics guiGraphics
            
            String input 玩家名称或者UUID
            
            int x 距离左侧边界的距离
            
            int y 距离上侧边界的距离
            
            int size 边长
            
            float scale 缩放比例
            """)
    public static void renderPlayerHead(GuiGraphics guiGraphics, String input, int x, int y, int size, float scale) {
        x = (int) (x / scale);
        y = (int) (y / scale);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, scale);
        PlayerFaceRenderer.draw(guiGraphics, getSkin(input), x, y, size);
        guiGraphics.pose().popPose();
    }

    private static void handleGameProfileAsync(String input) {
        ResolvableProfile component = createProfileComponent(input);
        component.resolve()
                .thenApplyAsync(result -> {
                    GameProfile profile = result.gameProfile();
                    try {
                        PlayerSkin playerSkin = minecraft.getSkinManager().getOrLoad(profile).get();
                        skins.put(input, playerSkin);
                    } catch (InterruptedException | ExecutionException ignored) {
                    }
                    return profile;
                })
                .exceptionally(ex -> null);
    }

    private static ResolvableProfile createProfileComponent(String input) {
        try {
            UUID uuid = UUID.fromString(input);
            return new ResolvableProfile(Optional.empty(), Optional.of(uuid), new PropertyMap());
        } catch (IllegalArgumentException e) {
            return new ResolvableProfile(Optional.of(input), Optional.empty(), new PropertyMap());
        }
    }

    public static PlayerSkin getSkin(String input) {
        if (skins.containsKey(input)) return skins.get(input);
        handleGameProfileAsync(input);
        if (skins.containsKey(input)) return skins.get(input);
        return DefaultPlayerSkin.get(minecraft.getUser().getProfileId());
    }

    /**
     * 获取 URL 皮肤的客户端纹理，并在首次访问时异步加载。
     *
     * <p>应在客户端渲染线程调用。仅接受 HTTP 或 HTTPS 地址；相同地址共用纹理和本地缓存。
     * 加载完成前、地址无效或图片加载失败时显示默认皮肤。
     * 图片支持标准 {@code 64×64} 和旧版 {@code 64×32} 玩家皮肤。
     *
     * @param input 皮肤 PNG 的直链，可以包含首尾空白
     * @return 已注册的皮肤纹理位置，地址无效时返回默认皮肤位置
     */
    public static ResourceLocation getUrlSkinTexture(String input) {
        ResourceLocation fallback = DefaultPlayerSkin.getDefaultTexture();
        String url = input.trim();
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null || !("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) {
                return fallback;
            }
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
        String hash = Hashing.sha256().hashString(url, StandardCharsets.UTF_8).toString();
        ResourceLocation texture = ViScriptNpc.id("skins/url/" + hash);
        var textures = minecraft.getTextureManager();
        if (textures.getTexture(texture, null) == null) {
            var cache = minecraft.gameDirectory.toPath().resolve("cache").resolve(ViScriptNpc.MOD_ID).resolve("skins").resolve(hash).toFile();
            // 原版无文件缓存的分支会在渲染线程解码前关闭 HTTP 流，因此使用文件缓存。
            textures.register(texture, new HttpTexture(cache, url, fallback, true, null));
        }
        return texture;
    }
}
