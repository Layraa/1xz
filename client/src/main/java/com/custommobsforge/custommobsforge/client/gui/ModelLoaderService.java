package com.custommobsforge.custommobsforge.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для загрузки и управления моделями
 */
public class ModelLoaderService {
    // Кэш загруженных моделей
    private Map<String, ModelInfo> modelCache = new HashMap<>();

    // Namespace мода
    private static final String MOD_ID = "custommobsforge";

    /**
     * Получить список всех доступных моделей
     */
    public List<String> getAvailableModels() {
        List<String> models = new ArrayList<>();
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();

        try {
            // Ищем все файлы в assets/custommobsforge/geo/ с расширением .geo.json
            resourceManager.listResources("geo", resource -> {
                if (!resource.getNamespace().equals(MOD_ID) || !resource.getPath().endsWith(".geo.json")) {
                    return false;
                }
                String path = "assets/" + MOD_ID + "/" + resource.getPath();
                models.add(path);
                System.out.println("Found model: " + path);
                return true;
            });
        } catch (Exception e) {
            System.err.println("Error listing models: " + e.getMessage());
            e.printStackTrace();
        }

        // Если не найдены модели, добавляем заглушки
        if (models.isEmpty()) {
            System.out.println("No models found, adding default examples");
            models.add("assets/" + MOD_ID + "/geo/custom_mob.geo.json");
            models.add("assets/" + MOD_ID + "/geo/god.geo.json");
            models.add("assets/" + MOD_ID + "/geo/overlord.geo.json");
        }

        return models;
    }

    /**
     * Получить список текстур для указанной модели
     */
    public List<String> getTexturesForModel(String modelPath) {
        List<String> textures = new ArrayList<>();
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();

        try {
            // Ищем все файлы в assets/custommobsforge/textures/entity/ с расширением .png
            resourceManager.listResources("textures/entity", resource -> {
                if (!resource.getNamespace().equals(MOD_ID) || !resource.getPath().endsWith(".png")) {
                    return false;
                }
                String path = "assets/" + MOD_ID + "/" + resource.getPath();
                textures.add(path);
                System.out.println("Found texture: " + path);
                return true;
            });

            // Если не найдены текстуры, добавляем заглушку на основе имени модели
            if (textures.isEmpty()) {
                String baseName = extractModelBaseName(modelPath);
                System.out.println("No textures found, adding default example for " + baseName);
                textures.add("assets/" + MOD_ID + "/textures/entity/" + baseName + ".png");
            }
        } catch (Exception e) {
            System.err.println("Error listing textures: " + e.getMessage());
            e.printStackTrace();
        }

        return textures;
    }

    /**
     * Извлечь базовое имя модели из пути
     */
    private String extractModelBaseName(String modelPath) {
        String fileName = modelPath;
        if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        }

        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.indexOf("."));
        }

        if (fileName.contains("_geo")) {
            fileName = fileName.replace("_geo", "");
        }

        if (fileName.contains(".geo")) {
            fileName = fileName.replace(".geo", "");
        }

        return fileName;
    }

    /**
     * Загрузить модель
     */
    public ModelInfo loadModel(String modelPath) {
        if (modelCache.containsKey(modelPath)) {
            return modelCache.get(modelPath);
        }

        try {
            ModelInfo modelInfo = new ModelInfo();
            modelInfo.setPath(modelPath);
            modelInfo.setName(extractModelBaseName(modelPath));
            modelInfo.setAvailableTextures(getTexturesForModel(modelPath));
            modelCache.put(modelPath, modelInfo);
            return modelInfo;
        } catch (Exception e) {
            System.err.println("Error loading model: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Класс для хранения информации о модели
     */
    public static class ModelInfo {
        private String path;
        private String name;
        private List<String> availableTextures = new ArrayList<>();

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<String> getAvailableTextures() { return availableTextures; }
        public void setAvailableTextures(List<String> availableTextures) { this.availableTextures = availableTextures; }
    }
}