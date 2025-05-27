package com.custommobsforge.custommobsforge.server.event;

import com.custommobsforge.custommobsforge.common.event.SaveConfigEvent;
import com.custommobsforge.custommobsforge.common.data.MobData;
import com.custommobsforge.custommobsforge.common.data.BehaviorTree;
import com.custommobsforge.custommobsforge.common.config.MobConfigManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod.EventBusSubscriber
public class ServerSaveHandler {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SubscribeEvent
    public static void onSaveConfig(SaveConfigEvent event) {
        ServerLevel level = (ServerLevel) event.getPlayer().level();

        if (event.isMobData()) {
            // Сохранение данных моба
            MobData mobData = event.getMobData();
            System.out.println("Server received SaveConfigEvent for mob: " + mobData.getName() +
                    " (ID: " + mobData.getId() + ") from player: " + event.getPlayer().getName().getString());

            saveMobData(mobData, level);

        } else if (event.isBehaviorTree()) {
            // Сохранение дерева поведения
            BehaviorTree behaviorTree = event.getBehaviorTree();
            System.out.println("Server received SaveConfigEvent for behavior tree: " + behaviorTree.getName() +
                    " (ID: " + behaviorTree.getId() + ") from player: " + event.getPlayer().getName().getString());

            saveBehaviorTree(behaviorTree, level);
        }
    }

    // Метод для сохранения данных моба на сервере
    private static void saveMobData(MobData mobData, ServerLevel level) {
        try {
            System.out.println("Saving mob data to server for: " + mobData.getName() + " (ID: " + mobData.getId() + ")");

            // Используем MobConfigManager для сохранения
            MobConfigManager.saveMobConfig(mobData, level);

            String worldPath = level.getServer().getWorldPath(LevelResource.ROOT).toString();
            System.out.println("Mob data successfully saved to server at: " + worldPath + "/custommobsforge/mobs/" + mobData.getId() + ".json");
        } catch (Exception e) {
            System.err.println("Error saving mob data on server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Метод для сохранения дерева поведения на сервере
    private static void saveBehaviorTree(BehaviorTree tree, ServerLevel level) {
        try {
            System.out.println("Saving behavior tree to server for: " + tree.getName() + " (ID: " + tree.getId() + ")");

            saveBehaviorTreeToServer(tree, level);

            String worldPath = level.getServer().getWorldPath(LevelResource.ROOT).toString();
            System.out.println("Behavior tree successfully saved to server at: " + worldPath + "/custommobsforge/behaviors/" + tree.getId() + ".json");
        } catch (Exception e) {
            System.err.println("Error saving behavior tree on server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Вспомогательный метод для сохранения дерева поведения
    private static void saveBehaviorTreeToServer(BehaviorTree tree, ServerLevel level) {
        try {
            // Создаем директорию для хранения деревьев поведения
            Path behaviorDir = level.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("custommobsforge").resolve("behaviors");

            Files.createDirectories(behaviorDir);

            // Создаем файл с именем, основанным на ID дерева
            Path filePath = behaviorDir.resolve(tree.getId() + ".json");

            // Проверяем, есть ли все необходимые поля
            if (tree.getNodes() == null || tree.getNodes().isEmpty()) {
                System.out.println("Info: Saving behavior tree without nodes (GUI structure only)");
            }

            // Сериализуем дерево в JSON и сохраняем
            String json = GSON.toJson(tree);
            Files.write(filePath, json.getBytes(StandardCharsets.UTF_8));

            // Подробное логирование о сохранении файла
            System.out.println("Behavior tree saved to file: " + filePath.toString() +
                    " (Size: " + json.length() + " bytes)" +
                    " (Nodes: " + (tree.getNodes() != null ? tree.getNodes().size() : 0) + ")" +
                    " (Connections: " + (tree.getConnections() != null ? tree.getConnections().size() : 0) + ")");
        } catch (Exception e) {
            System.err.println("Error saving behavior tree file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}