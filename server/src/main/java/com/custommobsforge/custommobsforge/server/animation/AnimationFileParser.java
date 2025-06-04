package com.custommobsforge.custommobsforge.server.animation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.custommobsforge.custommobsforge.server.util.LogHelper;
import net.minecraft.server.MinecraftServer;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class AnimationFileParser {

    public static class BoneInfo {
        public final String boneName;
        public final Set<String> associatedKeywords;
        public final boolean isWeapon;
        public final boolean isLimb;

        public BoneInfo(String boneName) {
            this.boneName = boneName;
            this.associatedKeywords = new HashSet<>();
            this.isWeapon = detectWeapon(boneName);
            this.isLimb = detectLimb(boneName);

            // Автоматически добавляем ключевые слова на основе имени
            extractKeywords(boneName);
        }

        private boolean detectWeapon(String name) {
            String lower = name.toLowerCase();
            return lower.contains("sword") || lower.contains("blade") || lower.contains("weapon") ||
                    lower.contains("axe") || lower.contains("hammer") || lower.contains("staff") ||
                    lower.contains("bow") || lower.contains("gun");
        }

        private boolean detectLimb(String name) {
            String lower = name.toLowerCase();
            return lower.contains("arm") || lower.contains("leg") || lower.contains("hand") ||
                    lower.contains("foot") || lower.contains("finger") || lower.contains("toe");
        }

        private void extractKeywords(String name) {
            String lower = name.toLowerCase();

            // Разбиваем по разделителям
            String[] parts = lower.split("[_\\-\\s\\.]");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    associatedKeywords.add(part);
                }
            }

            // Добавляем полное имя
            associatedKeywords.add(lower);
        }
    }

    /**
     * Парсит файл анимации и извлекает информацию о костях
     */
    public static Map<String, BoneInfo> parseBoneInfo(MinecraftServer server, String animationPath) {
        Map<String, BoneInfo> boneInfoMap = new HashMap<>();

        try {
            String fileName = animationPath.substring(animationPath.lastIndexOf('/') + 1);
            Path serverAnimationsDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("custommobsforge")
                    .resolve("animations");

            Files.createDirectories(serverAnimationsDir);
            Path animationFile = serverAnimationsDir.resolve(fileName);

            LogHelper.info("[AnimationParser] Looking for animation file: {}", animationFile);

            if (Files.exists(animationFile)) {
                try (FileReader reader = new FileReader(animationFile.toFile())) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                    // Ищем информацию о костях в разных разделах
                    parseBoneInfoFromAnimations(root, boneInfoMap);
                    parseBoneInfoFromGeometry(root, boneInfoMap);

                    LogHelper.info("[AnimationParser] Parsed bone info for {} bones", boneInfoMap.size());

                } catch (Exception e) {
                    LogHelper.error("[AnimationParser] Error reading animation file: {}", e.getMessage());
                }
            } else {
                LogHelper.warn("[AnimationParser] Animation file not found: {}", animationFile);
            }

        } catch (Exception e) {
            LogHelper.error("[AnimationParser] Failed to parse bone info from {}: {}", animationPath, e.getMessage());
        }

        return boneInfoMap;
    }

    private static void parseBoneInfoFromAnimations(JsonObject root, Map<String, BoneInfo> boneInfoMap) {
        if (root.has("animations")) {
            JsonObject animations = root.getAsJsonObject("animations");

            for (Map.Entry<String, JsonElement> animEntry : animations.entrySet()) {
                if (animEntry.getValue().isJsonObject()) {
                    JsonObject animation = animEntry.getValue().getAsJsonObject();

                    if (animation.has("bones")) {
                        JsonObject bones = animation.getAsJsonObject("bones");

                        for (String boneName : bones.keySet()) {
                            if (!boneInfoMap.containsKey(boneName)) {
                                boneInfoMap.put(boneName, new BoneInfo(boneName));
                            }
                        }
                    }
                }
            }
        }
    }

    private static void parseBoneInfoFromGeometry(JsonObject root, Map<String, BoneInfo> boneInfoMap) {
        // Если в файле анимации есть информация о геометрии
        if (root.has("minecraft:geometry")) {
            JsonElement geometry = root.get("minecraft:geometry");

            // Обрабатываем геометрию (может быть массивом или объектом)
            if (geometry.isJsonArray()) {
                for (JsonElement geoElement : geometry.getAsJsonArray()) {
                    if (geoElement.isJsonObject()) {
                        parseBonesFromGeometryObject(geoElement.getAsJsonObject(), boneInfoMap);
                    }
                }
            } else if (geometry.isJsonObject()) {
                parseBonesFromGeometryObject(geometry.getAsJsonObject(), boneInfoMap);
            }
        }
    }

    private static void parseBonesFromGeometryObject(JsonObject geometry, Map<String, BoneInfo> boneInfoMap) {
        if (geometry.has("bones")) {
            JsonElement bonesElement = geometry.get("bones");

            if (bonesElement.isJsonArray()) {
                for (JsonElement boneElement : bonesElement.getAsJsonArray()) {
                    if (boneElement.isJsonObject()) {
                        JsonObject bone = boneElement.getAsJsonObject();

                        if (bone.has("name")) {
                            String boneName = bone.get("name").getAsString();
                            if (!boneInfoMap.containsKey(boneName)) {
                                boneInfoMap.put(boneName, new BoneInfo(boneName));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Создает умные группы костей на основе парсинга файла
     */
    public static Map<String, List<String>> createSmartBoneGroups(Map<String, BoneInfo> boneInfoMap) {
        Map<String, List<String>> groups = new HashMap<>();

        // Создаем группы автоматически
        List<String> weapons = new ArrayList<>();
        List<String> limbs = new ArrayList<>();
        List<String> rightSide = new ArrayList<>();
        List<String> leftSide = new ArrayList<>();

        for (BoneInfo boneInfo : boneInfoMap.values()) {
            String boneName = boneInfo.boneName;

            if (boneInfo.isWeapon) {
                weapons.add(boneName);
            }

            if (boneInfo.isLimb) {
                limbs.add(boneName);
            }

            if (boneInfo.associatedKeywords.contains("right") || boneInfo.associatedKeywords.contains("r")) {
                rightSide.add(boneName);
            }

            if (boneInfo.associatedKeywords.contains("left") || boneInfo.associatedKeywords.contains("l")) {
                leftSide.add(boneName);
            }
        }

        if (!weapons.isEmpty()) groups.put("weapons", weapons);
        if (!limbs.isEmpty()) groups.put("limbs", limbs);
        if (!rightSide.isEmpty()) groups.put("right", rightSide);
        if (!leftSide.isEmpty()) groups.put("left", leftSide);

        return groups;
    }
}