package com.custommobsforge.custommobsforge.client.cache;

import com.custommobsforge.custommobsforge.common.data.MobData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MobDataCache {
    private static final Map<String, MobData> CLIENT_CACHE = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CLIENT_CACHE_DIR = "custommobsforge/cache";
    private static Path cacheDir;

    public static void init() {
        try {
            cacheDir = Minecraft.getInstance().gameDirectory.toPath().resolve(CLIENT_CACHE_DIR);
            Files.createDirectories(cacheDir);

            // ДОБАВЛЕНО: Логирование инициализации кеша
            System.out.println("MobDataCache initialized. Cache directory: " + cacheDir.toString());

            // Загружаем кэшированные данные при запуске
            loadCachedData();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void storeMobData(MobData data) {
        if (data == null || data.getId() == null) {
            // ДОБАВЛЕНО: Логирование ошибки
            System.err.println("Cannot store null MobData or MobData with null ID");
            return;
        }

        // ДОБАВЛЕНО: Логирование
        System.out.println("Storing mob data in client cache: " + data.getName() + " (ID: " + data.getId() + ")");

        // Сохраняем в оперативную память
        CLIENT_CACHE.put(data.getId(), data);

        // Сохраняем на диск
        try {
            Path cachePath = cacheDir.resolve(data.getId() + ".json");
            String json = GSON.toJson(data);
            Files.write(cachePath, json.getBytes(StandardCharsets.UTF_8));

            // ДОБАВЛЕНО: Логирование
            System.out.println("Mob data saved to file: " + cachePath.toString() + " (Size: " + json.length() + " bytes)");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static MobData getMobData(String mobId) {
        // ДОБАВЛЕНО: Логирование
        MobData data = CLIENT_CACHE.get(mobId);
        if (data != null) {
            System.out.println("Found mob data in client cache: " + data.getName() + " (ID: " + mobId + ")");
        } else {
            System.out.println("Mob data not found in client cache for ID: " + mobId);
        }
        return data;
    }

    public static boolean hasMobData(String mobId) {
        return CLIENT_CACHE.containsKey(mobId);
    }

    private static void loadCachedData() {
        try {
            if (!Files.exists(cacheDir)) {
                return;
            }

            // ДОБАВЛЕНО: Логирование
            System.out.println("Loading cached mob data from: " + cacheDir.toString());

            int loadedCount = 0;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(cacheDir, "*.json")) {
                for (Path path : stream) {
                    try {
                        String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                        MobData data = GSON.fromJson(json, MobData.class);
                        if (data != null && data.getId() != null) {
                            CLIENT_CACHE.put(data.getId(), data);
                            loadedCount++;

                            // ДОБАВЛЕНО: Логирование
                            System.out.println("Loaded mob data from cache: " + data.getName() + " (ID: " + data.getId() + ")");
                        }
                    } catch (IOException | JsonSyntaxException e) {
                        e.printStackTrace();
                    }
                }
            }

            // ДОБАВЛЕНО: Итоговое логирование
            System.out.println("Loaded " + loadedCount + " mob data entries from cache");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void clearCache() {
        CLIENT_CACHE.clear();

        try {
            if (Files.exists(cacheDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(cacheDir, "*.json")) {
                    for (Path path : stream) {
                        Files.delete(path);
                    }
                }
            }

            // ДОБАВЛЕНО: Логирование
            System.out.println("Client mob data cache cleared");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}