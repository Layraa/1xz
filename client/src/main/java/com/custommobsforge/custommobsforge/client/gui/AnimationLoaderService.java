package com.custommobsforge.custommobsforge.client.gui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис для загрузки и управления анимациями
 */
public class AnimationLoaderService {
    // Кэш анимаций для каждой модели
    private Map<String, List<AnimationInfo>> animationCache = new HashMap<>();

    // Корневая директория ресурсов
    private String resourcesRoot = "assets/custommobsforge/";

    /**
     * Получить список анимаций для указанной модели
     */
    public List<AnimationInfo> getAnimationsForModel(String modelPath) {
        if (animationCache.containsKey(modelPath)) {
            return animationCache.get(modelPath);
        }

        try {
            // Извлекаем базовое имя модели
            String baseName = extractModelBaseName(modelPath);

            // Преобразуем путь модели в путь к файлу анимаций
            String animationPath = modelPath.replace("geo", "animations").replace(".geo.json", ".animation.json");

            // Список для хранения анимаций
            List<AnimationInfo> animations = new ArrayList<>();

            // Пытаемся загрузить файл анимаций
            File animFile = new File(animationPath);
            if (animFile.exists()) {
                // Парсим JSON файл анимаций с помощью улучшенного парсера
                List<AnimationParser.AnimationInfo> parsedAnimations = AnimationParser.parseAnimationFile(animFile);

                // Конвертируем AnimationParser.AnimationInfo в AnimationInfo
                for (AnimationParser.AnimationInfo parsedInfo : parsedAnimations) {
                    animations.add(new AnimationInfo(
                            parsedInfo.getId(),
                            parsedInfo.getDisplayName(),
                            parsedInfo.isLoop()
                    ));
                }
            } else {
                // Если файла нет, пробуем искать в стандартных местах
                String altPath = resourcesRoot + "animations/" + baseName + ".animation.json";
                File altFile = new File(altPath);

                if (altFile.exists()) {
                    List<AnimationParser.AnimationInfo> parsedAnimations = AnimationParser.parseAnimationFile(altFile);

                    // Конвертируем AnimationParser.AnimationInfo в AnimationInfo
                    for (AnimationParser.AnimationInfo parsedInfo : parsedAnimations) {
                        animations.add(new AnimationInfo(
                                parsedInfo.getId(),
                                parsedInfo.getDisplayName(),
                                parsedInfo.isLoop()
                        ));
                    }
                } else {
                    // Если файл так и не найден, добавляем стандартные анимации
                    addDefaultAnimations(animations, baseName);
                }
            }

            // Кэшируем результат
            animationCache.put(modelPath, animations);

            // Уведомляем GlobalAnimationManager об обновлении списка анимаций
            GlobalAnimationManager.updateAnimations(animations);

            return animations;

        } catch (Exception e) {
            System.err.println("Error getting animations for model: " + e.getMessage());
            e.printStackTrace();

            // В случае ошибки возвращаем пустой список
            List<AnimationInfo> defaultList = new ArrayList<>();
            addDefaultAnimations(defaultList, extractModelBaseName(modelPath));
            return defaultList;
        }
    }

    /**
     * Загрузить анимации из указанного файла напрямую
     */
    /**
     * Загрузить анимации из указанного файла напрямую
     */
    public List<AnimationInfo> loadAnimationsFromFile(String filePath) {
        List<AnimationInfo> animations = new ArrayList<>();

        try {
            // Используем улучшенный парсер
            List<AnimationParser.AnimationInfo> parsedAnimations = AnimationParser.findAndParseAnimationFile(filePath);

            // Конвертируем AnimationParser.AnimationInfo в AnimationInfo
            for (AnimationParser.AnimationInfo parsedInfo : parsedAnimations) {
                animations.add(new AnimationInfo(
                        parsedInfo.getId(),
                        parsedInfo.getDisplayName(),
                        parsedInfo.isLoop()
                ));
            }

            // Уведомляем GlobalAnimationManager
            GlobalAnimationManager.updateAnimations(animations);

        } catch (Exception e) {
            System.err.println("Error loading animations from file: " + e.getMessage());
            e.printStackTrace();

            // В случае ошибки добавляем стандартные анимации
            addDefaultAnimations(animations, extractModelBaseName(filePath));
        }

        return animations;
    }

    /**
     * Добавление стандартных анимаций для модели
     */
    private void addDefaultAnimations(List<AnimationInfo> animations, String baseName) {
        // Стандартные анимации для разных типов моделей
        if (baseName.equals("custom_mob")) {
            animations.add(new AnimationInfo("animation.custom_mob.idle", "Idle", true));
            animations.add(new AnimationInfo("animation.custom_mob.walk", "Walk", true));
            animations.add(new AnimationInfo("animation.custom_mob.attack", "Attack", false));
            animations.add(new AnimationInfo("animation.custom_mob.death", "Death", false));
        } else if (baseName.equals("god")) {
            animations.add(new AnimationInfo("animation.god.idle", "Idle", true));
            animations.add(new AnimationInfo("animation.god.walk", "Walk", true));
            animations.add(new AnimationInfo("animation.god.attack", "Attack", false));
            animations.add(new AnimationInfo("animation.god.special", "Special Attack", false));
        } else {
            // Стандартные анимации для неизвестных моделей
            animations.add(new AnimationInfo("animation." + baseName + ".idle", "Idle", true));
            animations.add(new AnimationInfo("animation." + baseName + ".walk", "Walk", true));
            animations.add(new AnimationInfo("animation." + baseName + ".attack", "Attack", false));
        }
    }

    /**
     * Загрузить анимацию
     */
    public AnimationInfo loadAnimation(String modelPath, String animationName) {
        List<AnimationInfo> animations = getAnimationsForModel(modelPath);

        for (AnimationInfo animation : animations) {
            if (animation.getId().equals(animationName)) {
                return animation;
            }
        }

        return null;
    }

    /**
     * Извлечь базовое имя модели из пути
     */
    private String extractModelBaseName(String modelPath) {
        // Получаем только имя файла из пути
        String fileName = modelPath;
        if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        }

        // Удаляем расширение и дополнительные части
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
     * Класс для хранения информации об анимации
     */
    public static class AnimationInfo {
        private String id;
        private String displayName;
        private boolean loop;
        private float defaultSpeed = 1.0f;

        public AnimationInfo(String id, String displayName, boolean loop) {
            this.id = id;
            this.displayName = displayName;
            this.loop = loop;
        }

        // Геттеры и сеттеры
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public boolean isLoop() { return loop; }
        public void setLoop(boolean loop) { this.loop = loop; }

        public float getDefaultSpeed() { return defaultSpeed; }
        public void setDefaultSpeed(float defaultSpeed) { this.defaultSpeed = defaultSpeed; }

        @Override
        public String toString() {
            return displayName;
        }
    }
}