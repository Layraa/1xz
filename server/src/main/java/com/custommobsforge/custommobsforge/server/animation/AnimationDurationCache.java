package com.custommobsforge.custommobsforge.server.animation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.custommobsforge.custommobsforge.server.util.LogHelper;
import net.minecraft.server.MinecraftServer;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AnimationDurationCache {
    private static final Map<String, Map<String, AnimationInfo>> CACHE = new ConcurrentHashMap<>();

    public static class AnimationInfo {
        public final double durationSeconds;
        public final long durationMillis;
        public final boolean loop;

        public AnimationInfo(double durationSeconds, boolean loop) {
            this.durationSeconds = durationSeconds;
            this.durationMillis = (long)(durationSeconds * 1000);
            this.loop = loop;
        }
    }

    /**
     * Загружает информацию об анимациях из файла
     */
    public static void loadAnimationFile(MinecraftServer server, String animationPath) {
        LogHelper.info("[AnimationCache] Attempting to load animation file: {}", animationPath);

        try {
            // Извлекаем имя файла из пути
            String fileName = animationPath.substring(animationPath.lastIndexOf('/') + 1);

            // Пробуем загрузить из папки сервера
            Path serverAnimationsDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("custommobsforge")
                    .resolve("animations");

            // Создаем папку если её нет
            Files.createDirectories(serverAnimationsDir);

            Path animationFile = serverAnimationsDir.resolve(fileName);

            LogHelper.info("[AnimationCache] Looking for file at: {}", animationFile);

            if (Files.exists(animationFile)) {
                LogHelper.info("[AnimationCache] Found animation file, loading...");

                try (FileReader reader = new FileReader(animationFile.toFile())) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                    if (root.has("animations")) {
                        JsonObject animations = root.getAsJsonObject("animations");
                        Map<String, AnimationInfo> animationMap = new ConcurrentHashMap<>();

                        animations.entrySet().forEach(entry -> {
                            String animName = entry.getKey();
                            JsonObject animData = entry.getValue().getAsJsonObject();

                            // Получаем длительность
                            double duration = animData.has("animation_length")
                                    ? animData.get("animation_length").getAsDouble()
                                    : 2.0;

                            // Получаем loop
                            boolean loop = animData.has("loop")
                                    ? animData.get("loop").getAsBoolean()
                                    : false;

                            animationMap.put(animName, new AnimationInfo(duration, loop));

                            LogHelper.info("[AnimationCache] Loaded animation '{}' - duration: {}s, loop: {}",
                                    animName, duration, loop);
                        });

                        CACHE.put(animationPath, animationMap);
                        LogHelper.info("[AnimationCache] Successfully cached {} animations from {}",
                                animationMap.size(), fileName);
                    } else {
                        LogHelper.warn("[AnimationCache] No 'animations' section found in file");
                    }
                } catch (Exception e) {
                    LogHelper.error("[AnimationCache] Error reading animation file: {}", e.getMessage());
                }
            } else {
                LogHelper.warn("[AnimationCache] Animation file not found at: {}", animationFile);
                LogHelper.info("[AnimationCache] Please copy your animation files to: {}", serverAnimationsDir);
            }

        } catch (Exception e) {
            LogHelper.error("[AnimationCache] Failed to load animation file {}: {}",
                    animationPath, e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Получает информацию об анимации
     */
    public static AnimationInfo getAnimationInfo(String animationPath, String animationName) {
        Map<String, AnimationInfo> fileAnimations = CACHE.get(animationPath);
        if (fileAnimations != null) {
            AnimationInfo info = fileAnimations.get(animationName);
            if (info != null) {
                LogHelper.debug("[AnimationCache] Found cached info for '{}': {}s, loop: {}",
                        animationName, info.durationSeconds, info.loop);
            }
            return info;
        }
        LogHelper.debug("[AnimationCache] No cached info for animation '{}' in file '{}'",
                animationName, animationPath);
        return null;
    }

    /**
     * Проверяет, загружен ли файл
     */
    public static boolean isFileLoaded(String animationPath) {
        return CACHE.containsKey(animationPath);
    }

    /**
     * Очищает кэш
     */
    public static void clearCache() {
        CACHE.clear();
    }
}