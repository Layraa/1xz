package com.custommobsforge.custommobsforge.client.gui;

import com.custommobsforge.custommobsforge.client.gui.JsonUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.*;

public class BehaviorTreeModel {
    private final ObservableList<Node> nodes;
    private final ObservableList<Connection> connections;

    // Константы для размеров мира
    public static final int WORLD_WIDTH = 4000;
    public static final int WORLD_HEIGHT = 3000;

    public BehaviorTreeModel() {
        nodes = FXCollections.observableArrayList();
        connections = FXCollections.observableArrayList();
    }

    // Управление узлами
    public ObservableList<Node> getNodes() {
        return nodes;
    }

    public void addNode(Node node) {
        nodes.add(node);
    }

    public void removeNode(Node node) {
        // Удаляем все связи с этим узлом
        new ArrayList<>(connections).stream()
                .filter(conn -> conn.getSourceNode().equals(node) || conn.getTargetNode().equals(node))
                .forEach(connections::remove);

        nodes.remove(node);
    }

    public Optional<Node> findNodeById(UUID id) {
        return nodes.stream()
                .filter(node -> node.getId().equals(id))
                .findFirst();
    }

    // Управление связями
    public ObservableList<Connection> getConnections() {
        return connections;
    }

    public void addConnection(Connection connection) {
        // Проверяем, что такой связи ещё нет
        if (!connections.contains(connection)) {
            connections.add(connection);
        }
    }

    public void removeConnection(Connection connection) {
        connections.remove(connection);
    }

    public boolean connectionExists(Node source, Node target) {
        return connections.stream()
                .anyMatch(c -> c.getSourceNode().equals(source) && c.getTargetNode().equals(target));
    }

    public JSONObject exportToJson() {
        JSONObject jsonRoot = new JSONObject();

        // Экспорт узлов
        JSONArray nodesArray = new JSONArray();
        for (Node node : nodes) {
            JSONObject nodeObj = new JSONObject();
            nodeObj.put("id", node.getId().toString());
            nodeObj.put("type", node.getType());
            nodeObj.put("description", node.getDescription());
            nodeObj.put("parameter", node.getParameter());
            nodeObj.put("x", node.getX());
            nodeObj.put("y", node.getY());
            nodeObj.put("isExpanded", node.isExpanded());

            // Сохраняем параметры анимации, если они заданы
            if (!node.getAnimationId().isEmpty()) {
                nodeObj.put("animationId", node.getAnimationId());
                nodeObj.put("animationSpeed", node.getAnimationSpeed());
                nodeObj.put("loopAnimation", node.isLoopAnimation());
            }

            nodesArray.put(nodeObj);
        }
        jsonRoot.put("nodes", nodesArray);

        // Экспорт связей
        JSONArray connectionsArray = new JSONArray();
        for (Connection conn : connections) {
            JSONObject connObj = new JSONObject();
            connObj.put("source", conn.getSourceNode().getId().toString());
            connObj.put("target", conn.getTargetNode().getId().toString());
            connectionsArray.put(connObj);
        }
        jsonRoot.put("connections", connectionsArray);

        return jsonRoot;
    }

    public void importFromJson(JSONObject jsonRoot) throws IllegalArgumentException {
        // Очищаем текущую модель
        nodes.clear();
        connections.clear();

        // Временная карта ID -> Node для восстановления связей
        Map<String, Node> nodeMap = new HashMap<>();

        // Импорт узлов
        JSONArray nodesArray = jsonRoot.getJSONArray("nodes");
        for (int i = 0; i < nodesArray.length(); i++) {
            JSONObject nodeObj = nodesArray.getJSONObject(i);

            String id = nodeObj.getString("id");
            String type = nodeObj.getString("type");
            String description = nodeObj.getString("description");
            String parameter = nodeObj.getString("parameter");
            double x = nodeObj.getDouble("x");
            double y = nodeObj.getDouble("y");
            boolean isExpanded = nodeObj.getBoolean("isExpanded");

            // Создаем узел и настраиваем его
            Node node = new Node(type, description, parameter);
            node.setX(x);
            node.setY(y);
            node.setExpanded(isExpanded);

            // Загружаем параметры анимации, если они есть
            if (nodeObj.has("animationId")) {
                node.setAnimationId(nodeObj.getString("animationId"));
                node.setAnimationSpeed(nodeObj.getDouble("animationSpeed"));
                node.setLoopAnimation(nodeObj.getBoolean("loopAnimation"));
            }

            nodes.add(node);
            nodeMap.put(id, node);
        }


        // Импорт связей
        JSONArray connectionsArray = jsonRoot.getJSONArray("connections");
        for (int i = 0; i < connectionsArray.length(); i++) {
            JSONObject connObj = connectionsArray.getJSONObject(i);

            String sourceId = connObj.getString("source");
            String targetId = connObj.getString("target");

            Node sourceNode = nodeMap.get(sourceId);
            Node targetNode = nodeMap.get(targetId);

            if (sourceNode != null && targetNode != null) {
                connections.add(new Connection(sourceNode, targetNode));
            }
        }
    }

    // Сохранение/загрузка файла
    public void saveToFile(File file) throws Exception {
        JsonUtils.saveJsonToFile(exportToJson(), file);
    }

    public void loadFromFile(File file) throws Exception {
        JSONObject json = JsonUtils.loadJsonFromFile(file);
        importFromJson(json);
    }

    // Создание корневого узла
    public Node createRootNode() {
        Node rootNode = new Node("PrioritySelectorNode", "ROOT (Priority Selector)", null);
        rootNode.setX(WORLD_WIDTH / 2.0);
        rootNode.setY(WORLD_HEIGHT / 2.0);
        rootNode.setExpanded(true);
        addNode(rootNode);
        return rootNode;
    }

    // Автоматическая компоновка
    public void applyAutoLayout() {
        // Находим корневые узлы (без входящих связей)
        List<Node> rootNodes = new ArrayList<>();
        for (Node node : nodes) {
            boolean isTargetNode = false;
            for (Connection conn : connections) {
                if (conn.getTargetNode().equals(node)) {
                    isTargetNode = true;
                    break;
                }
            }
            if (!isTargetNode) {
                rootNodes.add(node);
            }
        }

        // Если нет корневых узлов, берем первый
        if (rootNodes.isEmpty() && !nodes.isEmpty()) {
            rootNodes.add(nodes.get(0));
        }

        // Размещаем корневые узлы
        double startX = WORLD_WIDTH / 2.0;
        double startY = 500;
        double horizontalSpacing = 400;

        for (int i = 0; i < rootNodes.size(); i++) {
            Node rootNode = rootNodes.get(i);
            rootNode.setX(startX + (i - rootNodes.size() / 2.0) * horizontalSpacing);
            rootNode.setY(startY);

            // Рекурсивно размещаем дочерние узлы
            layoutChildNodes(rootNode, 1, 250);
        }
    }

    private void layoutChildNodes(Node parent, int level, double verticalSpacing) {
        // Собираем все дочерние узлы
        List<Node> children = new ArrayList<>();
        for (Connection conn : connections) {
            if (conn.getSourceNode().equals(parent)) {
                children.add(conn.getTargetNode());
            }
        }

        if (children.isEmpty()) return;

        // Компактно размещаем дочерние узлы
        double horizontalSpacing = 300;
        double childY = parent.getY() + verticalSpacing;

        // Центрирование группы дочерних узлов под родителем
        double startX = parent.getX() - ((children.size() - 1) * horizontalSpacing / 2.0);

        for (int i = 0; i < children.size(); i++) {
            Node child = children.get(i);
            child.setX(startX + i * horizontalSpacing);
            child.setY(childY);

            // Рекурсивно размещаем дочерние узлы следующего уровня
            layoutChildNodes(child, level + 1, verticalSpacing);
        }
    }
}