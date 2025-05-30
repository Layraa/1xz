package com.custommobsforge.custommobsforge.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер файлов анимаций
 */
public class AnimationParser {

    // Кэш результатов парсинга
    private static final Map<String, List<AnimationInfo>> ANIMATION_CACHE = new HashMap<>();

    /**
     * Парсит файл анимаций и возвращает список найденных анимаций
     */
    public static List<AnimationInfo> parseAnimationFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            System.out.println("ОШИБКА: Файл анимации не найден или недоступен: " + (file != null ? file.getAbsolutePath() : "null"));
            return new ArrayList<>();
        }

        // Проверяем кэш
        String path = file.getAbsolutePath();
        if (ANIMATION_CACHE.containsKey(path)) {
            System.out.println("Возврат анимаций из кэша для: " + path);
            return new ArrayList<>(ANIMATION_CACHE.get(path));
        }

        List<AnimationInfo> results = new ArrayList<>();
        try {
            System.out.println("Парсинг файла анимаций: " + path);

            // Читаем содержимое файла
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            System.out.println("Файл успешно прочитан, размер: " + content.length() + " байт");

            // Проверяем, что файл выглядит как JSON
            if (!content.trim().startsWith("{")) {
                System.out.println("ПРЕДУПРЕЖДЕНИЕ: Файл не похож на JSON, начинается с: " +
                        content.substring(0, Math.min(20, content.length())));
            }

            // Сначала пробуем использовать JsonParser для парсинга
            try {
                System.out.println("Попытка парсинга с помощью JsonParser...");
                JsonParser parser = new JsonParser();
                JsonElement jsonElement = parser.parse(new java.io.FileReader(file));
                JsonObject rootObject = jsonElement.getAsJsonObject();

                System.out.println("JSON успешно разобран");
                System.out.println("Корневые ключи: " + rootObject.keySet());

                // Проверяем наличие блока анимаций
                if (rootObject.has("animations")) {
                    System.out.println("Найден блок 'animations'");
                    JsonObject animationsObject = rootObject.getAsJsonObject("animations");
                    System.out.println("Найден объект animations с " + animationsObject.keySet().size() + " ключами");

                    // Обходим все анимации
                    for (Map.Entry<String, JsonElement> entry : animationsObject.entrySet()) {
                        String animationId = entry.getKey();

                        // Игнорируем, если это не объект
                        if (!entry.getValue().isJsonObject()) {
                            System.out.println("Пропускаем не-объект: " + animationId);
                            continue;
                        }

                        JsonObject animData = entry.getValue().getAsJsonObject();

                        // Определяем зацикленность на основе данных анимации
                        boolean loop = true; // По умолчанию зацикливаем
                        if (animData.has("loop")) {
                            loop = animData.get("loop").getAsBoolean();
                        }

                        // Получаем имя для отображения (убираем префиксы)
                        String displayName = getDisplayName(animationId);

                        // Добавляем анимацию в список
                        results.add(new AnimationInfo(animationId, displayName, loop));
                        System.out.println("Добавлена анимация: " + animationId + " (loop: " + loop + ")");
                    }
                } else {
                    System.out.println("Блок 'animations' не найден в JSON");
                }
            } catch (Exception e) {
                System.err.println("Ошибка при парсинге JSON с JsonParser: " + e.getMessage());
                e.printStackTrace();

                // Если не удалось с JsonParser, используем регулярные выражения
                System.out.println("Попытка парсинга с помощью регулярных выражений...");
                parseUsingRegex(content, results);
            }
        } catch (Exception e) {
            System.err.println("Критическая ошибка при парсинге анимаций: " + e.getMessage());
            e.printStackTrace();
        }

        // Если список пуст, добавляем стандартные анимации
        if (results.isEmpty()) {
            System.out.println("Анимации не найдены, добавляем стандартные");
            String baseName = extractModelBaseName(file.getName());
            addDefaultAnimations(results, baseName);
        }

        // Кэшируем результат
        ANIMATION_CACHE.put(path, new ArrayList<>(results));

        System.out.println("Парсинг завершен, найдено " + results.size() + " анимаций");
        return results;
    }

    /**
     * Ищет файл анимаций в стандартных путях и парсит его
     */
    public static List<AnimationInfo> findAndParseAnimationFile(String fileName) {
        System.out.println("Поиск файла анимаций: " + fileName);

        // Нормализуем имя файла
        if (!fileName.endsWith(".animation.json") && !fileName.endsWith(".json")) {
            fileName = fileName + ".animation.json";
        }

        // Проверяем кэш
        if (ANIMATION_CACHE.containsKey(fileName)) {
            System.out.println("Возврат анимаций из кэша для: " + fileName);
            return new ArrayList<>(ANIMATION_CACHE.get(fileName));
        }

        // Формируем путь к ресурсу
        String resourcePath = fileName.startsWith("assets/") ? fileName : "assets/custommobsforge/animations/" + fileName;
        System.out.println("Проверяем ресурс: " + resourcePath);

        // Пробуем загрузить и парсить ресурс
        List<AnimationInfo> results = parseAnimationFromResource(resourcePath);

        // Если ничего не найдено, возвращаем стандартные анимации
        if (results.isEmpty()) {
            System.out.println("Файл анимации не найден, добавляем стандартные");
            String baseName = extractModelBaseName(fileName);
            addDefaultAnimations(results, baseName);
        }

        // Кэшируем результат
        ANIMATION_CACHE.put(fileName, new ArrayList<>(results));
        System.out.println("Парсинг завершен, найдено " + results.size() + " анимаций");
        return results;
    }

    /**
     * Парсинг с использованием регулярных выражений как запасной вариант
     */
    private static void parseUsingRegex(String content, List<AnimationInfo> results) {
        System.out.println("Используем парсинг с помощью регулярных выражений");

        // Ищем блок "animations"
        Pattern animBlockPattern = Pattern.compile("\"animations\"\\s*:\\s*\\{([^}]+)\\}");
        Matcher animBlockMatcher = animBlockPattern.matcher(content);

        if (animBlockMatcher.find()) {
            String animationsBlock = animBlockMatcher.group(1);

            // Ищем все анимации внутри блока
            Pattern animPattern = Pattern.compile("\"([^\"]+)\"\\s*:");
            Matcher animMatcher = animPattern.matcher(animationsBlock);

            while (animMatcher.find()) {
                String animName = animMatcher.group(1);

                // Игнорируем технические поля
                if (!animName.equals("bones") && !animName.equals("animation_length") &&
                        !animName.equals("sound_effects") && !animName.equals("particle_effects") &&
                        !animName.equals("format_version")) {

                    // Проверяем флаг зацикливания (примерно, точно определить через regex сложно)
                    boolean loop = !content.contains("\"" + animName + "\"\\s*:\\s*\\{[^}]*\"loop\"\\s*:\\s*false");

                    // Получаем имя для отображения
                    String displayName = getDisplayName(animName);

                    // Добавляем анимацию
                    results.add(new AnimationInfo(animName, displayName, loop));
                    System.out.println("Найдена анимация через regex: " + animName + " (loop: " + loop + ")");
                }
            }
        }
    }

    /**
     * Парсит анимацию из ресурса
     */
    public static List<AnimationInfo> parseAnimationFromResource(String resourcePath) {
        System.out.println("Попытка парсинга анимации из ресурса: " + resourcePath);

        try {
            ClassLoader classLoader = AnimationParser.class.getClassLoader();
            URL resourceUrl = classLoader.getResource(resourcePath);

            if (resourceUrl == null) {
                System.out.println("Ресурс не найден: " + resourcePath);
                return new ArrayList<>();
            }

            System.out.println("Ресурс найден: " + resourceUrl);

            // Читаем содержимое ресурса
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resourceUrl.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            System.out.println("Прочитано содержимое ресурса, размер: " + content.length() + " байт");

            // Парсим содержимое
            List<AnimationInfo> results = new ArrayList<>();

            try {
                // Парсинг через JsonParser
                JsonParser parser = new JsonParser();
                JsonElement jsonElement = parser.parse(content.toString());
                JsonObject rootObject = jsonElement.getAsJsonObject();

                if (rootObject.has("animations")) {
                    JsonObject animationsObject = rootObject.getAsJsonObject("animations");

                    for (Map.Entry<String, JsonElement> entry : animationsObject.entrySet()) {
                        String animationId = entry.getKey();
                        if (!entry.getValue().isJsonObject()) continue;

                        JsonObject animData = entry.getValue().getAsJsonObject();

                        boolean loop = true;
                        if (animData.has("loop")) {
                            loop = animData.get("loop").getAsBoolean();
                        }

                        String displayName = getDisplayName(animationId);
                        results.add(new AnimationInfo(animationId, displayName, loop));
                    }
                }
            } catch (Exception e) {
                System.err.println("Ошибка при парсинге JSON из ресурса: " + e.getMessage());

                // Используем регулярные выражения, если JSON-парсинг не удался
                parseUsingRegex(content.toString(), results);
            }

            // Если анимации не найдены, добавляем стандартные
            if (results.isEmpty()) {
                String baseName = extractModelBaseName(resourcePath);
                addDefaultAnimations(results, baseName);
            }

            return results;

        } catch (Exception e) {
            System.err.println("Ошибка при парсинге анимации из ресурса: " + e.getMessage());
            e.printStackTrace();

            // Возвращаем пустой список в случае ошибки
            return new ArrayList<>();
        }
    }

    /**
     * Добавляет стандартные анимации в список
     */
    private static void addDefaultAnimations(List<AnimationInfo> animations, String baseName) {
        animations.add(new AnimationInfo("animation." + baseName + ".idle", "Idle", true));
        animations.add(new AnimationInfo("animation." + baseName + ".walk", "Walk", true));
        animations.add(new AnimationInfo("animation." + baseName + ".attack", "Attack", false));
        animations.add(new AnimationInfo("animation." + baseName + ".death", "Death", false));
    }

    /**
     * Получает отображаемое имя для анимации
     */
    private static String getDisplayName(String animationId) {
        // Получаем только последнюю часть имени после последней точки
        if (animationId.contains(".")) {
            String name = animationId.substring(animationId.lastIndexOf('.') + 1);
            // Делаем первую букву заглавной и заменяем подчеркивания на пробелы
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
            name = name.replace('_', ' ');
            return name;
        }
        return animationId;
    }

    /**
     * Извлекает базовое имя модели из имени файла
     */
    private static String extractModelBaseName(String fileName) {
        // Удаляем расширение
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.indexOf("."));
        }

        // Удаляем суффиксы
        if (fileName.endsWith(".animation")) {
            fileName = fileName.substring(0, fileName.length() - 10);
        }

        // Удаляем путь, если он есть
        if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        }

        return fileName;
    }

    /**
     * Класс для хранения информации об анимации
     */
    public static class AnimationInfo {
        private final String id;
        private final String displayName;
        private final boolean loop;

        public AnimationInfo(String id, String displayName, boolean loop) {
            this.id = id;
            this.displayName = displayName;
            this.loop = loop;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isLoop() {
            return loop;
        }

        @Override
        public String toString() {
            return displayName + " (" + id + ", loop: " + loop + ")";
        }
    }
}