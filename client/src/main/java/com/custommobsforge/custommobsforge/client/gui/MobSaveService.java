package com.custommobsforge.custommobsforge.client.gui;

import com.custommobsforge.custommobsforge.common.data.*;
import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.SaveConfigPacket;
import com.custommobsforge.custommobsforge.common.network.packet.RequestDataPacket;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.*;

public class MobSaveService {
    // Кэш для хранения данных вместо файлов
    private static final Map<String, MobData> CLIENT_CACHE = new HashMap<>();
    private static final Map<String, BehaviorTree> BEHAVIOR_CACHE = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Сохраняет конфигурацию моба
     */
    public boolean saveMob(MobConfig mobConfig) {
        try {
            // 1. Конвертируем в MobData для отправки на сервер
            MobData mobData = convertToMobData(mobConfig);

            // 2. Сохраняем в кэш
            CLIENT_CACHE.put(mobData.getId(), mobData);

            // 3. Отправляем пакет на сервер (ОБНОВЛЁННЫЙ)
            NetworkManager.INSTANCE.sendToServer(new SaveConfigPacket(mobData));

            System.out.println("Sent mob data to server: " + mobConfig.getName());
            return true;
        } catch (Exception e) {
            System.err.println("Error saving mob configuration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Сохраняет конфигурацию дерева поведения
     */
    public boolean saveBehaviorTree(BehaviorTreeConfig treeConfig) {
        try {
            // 1. Конвертируем в BehaviorTree для отправки на сервер
            BehaviorTree behaviorTree = convertToBehaviorTree(treeConfig);

            // 2. Сохраняем в кэш
            BEHAVIOR_CACHE.put(behaviorTree.getId(), behaviorTree);

            // 3. Отправляем пакет на сервер (ОБНОВЛЁННЫЙ)
            NetworkManager.INSTANCE.sendToServer(new SaveConfigPacket(behaviorTree));

            System.out.println("Sent behavior tree to server: " + treeConfig.getName());
            return true;
        } catch (Exception e) {
            System.err.println("Error saving behavior tree: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Конвертирует MobConfig в MobData для отправки на сервер
     */
    private MobData convertToMobData(MobConfig config) {
        MobData data = new MobData(config.getId().toString(), config.getName());
        data.setModelPath(config.getModelPath());
        data.setTexturePath(config.getTexturePath());
        data.setAnimationFilePath(config.getAnimationFilePath());

        // Конвертация атрибутов
        Map<String, Float> attributes = new HashMap<>();
        for (Map.Entry<String, Double> entry : config.getAttributes().entrySet()) {
            attributes.put(entry.getKey(), entry.getValue().floatValue());
        }
        data.setAttributes(attributes);

        // Конвертация анимаций
        Map<String, AnimationConfig> clientAnimations = config.getAnimationMappings();
        if (clientAnimations != null) {
            for (Map.Entry<String, AnimationConfig> entry : clientAnimations.entrySet()) {
                AnimationConfig clientConfig = entry.getValue();
                data.addAnimation(
                        entry.getKey(),
                        clientConfig.getAnimationName(),
                        clientConfig.isLoop(),
                        clientConfig.getSpeed()
                );
            }
        }

        // Если у моба есть дерево поведения, привязываем его ID
        if (config.getBehaviorTreeId() != null) {
            // Создаем пустое дерево поведения с таким же ID
            BehaviorTree tree = new BehaviorTree();
            tree.setId(config.getBehaviorTreeId().toString());
            tree.setName(config.getName() + "_behavior");
            data.setBehaviorTree(tree);
        }

        return data;
    }

    /**
     * Конвертирует MobData в MobConfig
     */
    private MobConfig convertFromMobData(MobData data) {
        try {
            MobConfig config = new MobConfig();
            config.setId(UUID.fromString(data.getId()));
            config.setName(data.getName());
            config.setModelPath(data.getModelPath());
            config.setTexturePath(data.getTexturePath());
            config.setAnimationFilePath(data.getAnimationFilePath());

            // Конвертация атрибутов
            Map<String, Double> attributes = new HashMap<>();
            for (Map.Entry<String, Float> entry : data.getAttributes().entrySet()) {
                attributes.put(entry.getKey(), (double) entry.getValue());
            }
            config.setAttributes(attributes);

            // Конвертация анимаций
            Map<String, AnimationConfig> animations = new HashMap<>();
            for (Map.Entry<String, AnimationMapping> entry : data.getAnimations().entrySet()) {
                AnimationMapping mapping = entry.getValue();
                AnimationConfig clientConfig = new AnimationConfig(
                        mapping.getAnimationName(),
                        mapping.isLoop(),
                        mapping.getSpeed()
                );
                clientConfig.setDescription(mapping.getDescription());
                animations.put(entry.getKey(), clientConfig);
            }
            config.setAnimationMappings(animations);

            // Установка ID дерева поведения
            if (data.getBehaviorTree() != null) {
                config.setBehaviorTreeId(UUID.fromString(data.getBehaviorTree().getId()));
            }

            return config;
        } catch (Exception e) {
            System.err.println("Error converting MobData to MobConfig: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Конвертирует BehaviorTreeConfig в BehaviorTree для отправки на сервер
     */
    private BehaviorTree convertToBehaviorTree(BehaviorTreeConfig config) {
        BehaviorTree tree = new BehaviorTree();
        tree.setId(config.getId().toString());
        tree.setName(config.getName());

        // Устанавливаем ID связанного моба, если есть
        if (config.getMobId() != null) {
            tree.setName(config.getName() + " (Mob: " + config.getMobName() + ")");
        }

        // Конвертация узлов
        List<BehaviorNode> nodes = new ArrayList<>();
        for (NodeData nodeData : config.getNodes()) {
            BehaviorNode node = new BehaviorNode(
                    nodeData.getType(),
                    nodeData.getDescription()
            );

            if (nodeData.getId() != null) {
                node.setId(nodeData.getId());
            }

            node.setParameter(nodeData.getParameter());
            node.setY(nodeData.getY());
            node.setExpanded(nodeData.isExpanded());

            // Копируем параметры анимации
            if (nodeData.getAnimationId() != null && !nodeData.getAnimationId().isEmpty()) {
                node.setAnimationId(nodeData.getAnimationId());
                node.setAnimationSpeed(nodeData.getAnimationSpeed());
                node.setLoopAnimation(nodeData.isLoopAnimation());
            }

            // Добавляем пользовательские параметры, если есть
            if (nodeData.getParameter() != null && !nodeData.getParameter().isEmpty()) {
                String[] params = nodeData.getParameter().split(";");
                for (String param : params) {
                    if (param.contains("=")) {
                        String[] keyValue = param.split("=", 2);
                        if (keyValue.length == 2) {
                            node.setCustomParameter(keyValue[0], keyValue[1]);
                        }
                    }
                }
            }

            nodes.add(node);
        }
        tree.setNodes(nodes);

        // Конвертация соединений
        List<BehaviorConnection> connections = new ArrayList<>();
        for (ConnectionData connData : config.getConnections()) {
            BehaviorConnection conn = new BehaviorConnection(
                    connData.getSourceNodeId(),
                    connData.getTargetNodeId()
            );

            connections.add(conn);
        }
        tree.setConnections(connections);

        return tree;
    }

    /**
     * Обновить кэш с новой MobData
     */
    public void updateCache(MobData mobData) {
        if (mobData != null) {
            CLIENT_CACHE.put(mobData.getId(), mobData);
        }
    }

    /**
     * Обновить кэш с новым BehaviorTree
     */
    public void updateBehaviorCache(BehaviorTree behaviorTree) {
        if (behaviorTree != null) {
            BEHAVIOR_CACHE.put(behaviorTree.getId(), behaviorTree);
        }
    }

    /**
     * Получить все мобы из кэша
     */
    public List<MobConfig> getAllMobs() {
        List<MobConfig> mobs = new ArrayList<>();

        // Запрос списка мобов с сервера (ОБНОВЛЁННЫЙ)
        NetworkManager.INSTANCE.sendToServer(new RequestDataPacket());

        // Возвращаем мобов из кэша
        for (MobData mobData : CLIENT_CACHE.values()) {
            MobConfig config = convertFromMobData(mobData);
            if (config != null) {
                mobs.add(config);
            }
        }

        return mobs;
    }

    /**
     * Загрузить моба из кэша по ID
     */
    public MobConfig loadMob(String mobId) {
        MobData mobData = CLIENT_CACHE.get(mobId);
        if (mobData != null) {
            return convertFromMobData(mobData);
        }

        // Если нет в кэше, запрашиваем с сервера (ОБНОВЛЁННЫЙ)
        NetworkManager.INSTANCE.sendToServer(new RequestDataPacket(mobId));
        return null;
    }

    /**
     * Очистить кэш
     */
    public void clearCache() {
        CLIENT_CACHE.clear();
        BEHAVIOR_CACHE.clear();
    }
}