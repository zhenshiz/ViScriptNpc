package com.viscript.npc.npc.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.thexeler.AttentionMind;
import org.thexeler.api.asset.AssetCompiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AMIntentionAssetPublisher {
    private static final int MAX_JSON_BYTES = 1024 * 1024;
    private static final String PACK_DIRECTORY = "viscript_npc_editor";
    private static final String PACK_ID = "file/" + PACK_DIRECTORY;

    private AMIntentionAssetPublisher() {
    }

    public static void publish(ServerPlayer player, String rawId, String json) {
        if (!player.hasPermissions(2)) {
            throw new SecurityException("Operator permission level 2 is required to publish AM intentions");
        }
        ResourceLocation id = ResourceLocation.parse(rawId);
        if (json == null || json.isBlank()) throw new IllegalArgumentException("Asset JSON is empty");
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Asset JSON exceeds the 1 MiB upload limit");
        }

        JsonElement source = JsonParser.parseString(json);
        Map<ResourceLocation, JsonElement> sources = new LinkedHashMap<>();
        AttentionMind.intentionAssets().generation().assets().forEach((assetId, asset) -> {
            if (!assetId.equals(id)) sources.put(assetId, JsonParser.parseString(asset.canonicalJson()));
        });
        sources.put(id, source);
        var result = new AssetCompiler(AttentionMind.intentionTypes(), AttentionMind.expressionQueries())
                .compile(sources);
        if (!result.isSuccess()) {
            String detail = result.diagnostics().stream()
                    .map(value -> value.stableCode() + ": " + value.message())
                    .reduce((left, right) -> left + "; " + right).orElse("Unknown compilation error");
            throw new IllegalArgumentException("AM asset validation failed: " + detail);
        }

        MinecraftServer server = player.getServer();
        if (server == null) throw new IllegalStateException("Server is unavailable");
        try {
            Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_DIRECTORY).normalize();
            Path dataRoot = packRoot.resolve("data").resolve(id.getNamespace())
                    .resolve("attentionmind").resolve("intentions").normalize();
            Path target = dataRoot.resolve(id.getPath() + ".json").normalize();
            if (!target.startsWith(dataRoot) || !dataRoot.startsWith(packRoot)) {
                throw new SecurityException("Invalid AM asset path");
            }
            Files.createDirectories(target.getParent());
            writePackMetadata(packRoot);
            atomicWrite(target, json);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write AM asset: " + exception.getMessage(), exception);
        }

        server.getPackRepository().reload();
        ArrayList<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
        if (!selected.contains(PACK_ID)) selected.add(PACK_ID);
        server.reloadResources(selected).whenComplete((ignored, failure) -> server.execute(() -> {
            if (failure == null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Published AM intention " + id));
            } else {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Published file but reload failed: " + failure.getMessage()));
            }
        }));
    }

    private static void writePackMetadata(Path packRoot) throws IOException {
        Files.createDirectories(packRoot);
        Path metadata = packRoot.resolve("pack.mcmeta");
        if (!Files.exists(metadata)) {
            atomicWrite(metadata, "{\n  \"pack\": {\n    \"pack_format\": 48,\n"
                    + "    \"description\": \"ViScriptNpc editor AM assets\"\n  }\n}\n");
        }
    }

    private static void atomicWrite(Path target, String contents) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, contents, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
