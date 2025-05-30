package com.custommobsforge.custommobsforge.client.gui;

import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class JsonUtils {
    /**
     * Сохраняет JSON объект в файл
     */
    public static void saveJsonToFile(JSONObject json, File file) throws IOException {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(json.toString(2));
        }
    }

    /**
     * Загружает JSON объект из файла
     */
    public static JSONObject loadJsonFromFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        }

        return new JSONObject(content.toString());
    }
}