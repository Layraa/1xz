package com.custommobsforge.custommobsforge.common.config;

import com.custommobsforge.custommobsforge.common.data.MobData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MobConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_DIR = "mobs";
    private static final Map<String, MobData> mobCache = new HashMap<>();

    // Инициализация директории конфигурации
    public static void init(ServerLevel level) {
        try {
            Path configPath = level.getServer().getWorldPath(LevelResource.ROOT).resolve("custommobsforge").resolve(CONFIG_DIR);
            Files.createDirectories(configPath);

            // Создаем дефолтного моба если нет никаких конфигураций
            if (isEmpty(configPath)) {
                MobData defaultMob = createDefaultMob();
                saveMobConfig(defaultMob, level);
            }

            // Загружаем все конфигурации в кэш
            loadAllMobConfigs(level);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean isEmpty(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return !entries.findFirst().isPresent();
        }
    }

    private static MobData createDefaultMob() {
        MobData defaultMob = new MobData("default_mob", "Default Mob");
        defaultMob.setModelPath("assets/custommobsforge/geo/custom_mob.geo.json");
        defaultMob.setTexturePath("assets/custommobsforge/textures/entity/custom_mob.png");
        defaultMob.setAnimationFilePath("assets/custommobsforge/animations/custom_mob.animation.json");

        // Добавляем базовые анимации
        defaultMob.addAnimation("IDLE", "animation.custom_mob.idle", true, 1.0f);
        defaultMob.addAnimation("WALK", "animation.custom_mob.walk", true, 1.0f);
        defaultMob.addAnimation("ATTACK", "animation.custom_mob.attack", false, 1.0f);
        defaultMob.addAnimation("DEATH", "animation.custom_mob.death", false, 1.0f);

        // Устанавливаем атрибуты по умолчанию
        defaultMob.setAttribute("maxHealth", 20.0f);
        defaultMob.setAttribute("movementSpeed", 0.25f);
        defaultMob.setAttribute("attackDamage", 3.0f);
        defaultMob.setAttribute("armor", 0.0f);
        defaultMob.setAttribute("knockbackResistance", 0.0f);

        return defaultMob;
    }

    // Основные методы для работы с конфигурациями мобов

    public static void saveMobConfig(MobData mobData, ServerLevel level) {
        if (mobData == null || mobData.getId() == null) {
            return;
        }

        try {
            Path configPath = getMobConfigPath(mobData.getId(), level);
            Files.createDirectories(configPath.getParent());

            String json = GSON.toJson(mobData);
            Files.write(configPath, json.getBytes(StandardCharsets.UTF_8));

            // Обновляем кэш
            mobCache.put(mobData.getId(), mobData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static MobData loadMobConfig(String mobId, ServerLevel level) {
        // Сначала проверяем кэш
        if (mobCache.containsKey(mobId)) {
            return mobCache.get(mobId);
        }

        try {
            Path configPath = getMobConfigPath(mobId, level);
            if (!Files.exists(configPath)) {
                return null;
            }

            String json = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
            MobData mobData = GSON.fromJson(json, MobData.class);

            // Обновляем кэш
            mobCache.put(mobId, mobData);

            return mobData;
        } catch (IOException | JsonSyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<String> getAvailableMobIds(ServerLevel level) {
        try {
            Path configDir = level.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("custommobsforge").resolve(CONFIG_DIR);

            if (!Files.exists(configDir)) {
                return Collections.emptyList();
            }

            try (Stream<Path> paths = Files.list(configDir)) {
                return paths.filter(path -> path.toString().endsWith(".json"))
                        .map(path -> path.getFileName().toString().replace(".json", ""))
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public static List<MobData> getAvailableMobs(ServerLevel level) {
        List<String> mobIds = getAvailableMobIds(level);
        List<MobData> mobs = new ArrayList<>();

        for (String id : mobIds) {
            MobData mob = loadMobConfig(id, level);
            if (mob != null) {
                mobs.add(mob);
            }
        }

        return mobs;
    }

    public static void deleteMobConfig(String mobId, ServerLevel level) {
        try {
            Path configPath = getMobConfigPath(mobId, level);
            Files.deleteIfExists(configPath);

            // Обновляем кэш
            mobCache.remove(mobId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Path getMobConfigPath(String mobId, ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("custommobsforge").resolve(CONFIG_DIR)
                .resolve(mobId + ".json");
    }

    private static void loadAllMobConfigs(ServerLevel level) {
        List<String> mobIds = getAvailableMobIds(level);

        for (String id : mobIds) {
            loadMobConfig(id, level);
        }
    }

    // Методы для обновления кэша

    public static void clearCache() {
        mobCache.clear();
    }

    public static boolean isCached(String mobId) {
        return mobCache.containsKey(mobId);
    }
}