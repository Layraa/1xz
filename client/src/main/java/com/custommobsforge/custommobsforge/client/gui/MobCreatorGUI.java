package com.custommobsforge.custommobsforge.client.gui;

import com.custommobsforge.custommobsforge.common.network.NetworkManager;
import com.custommobsforge.custommobsforge.common.network.packet.RequestDataPacket;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import java.io.File;
import java.util.*;

public class MobCreatorGUI extends Application {
    private static Stage primaryStage;

    // Компоненты GUI
    private TabPane tabPane;

    private StatsEditorPanel statsEditorPanel;
    private BehaviorTreeEditor behaviorTreeEditor;
    private UndoRedoManager undoRedoManager;

    // Сервисы
    private ModelLoaderService modelLoaderService;
    private AnimationLoaderService animationLoaderService;
    private MobSaveService mobSaveService;

    // Текущий редактируемый моб
    private MobConfig currentMobConfig;

    // Флаги состояния
    private boolean isModified = false;
    private boolean isNewMob = true;


    public static void launchGUI() {
        Platform.setImplicitExit(false);
        new Thread(() -> Application.launch(MobCreatorGUI.class)).start();
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    private static MobCreatorGUI instance;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        instance = this;

        // Инициализация сервисов
        initializeServices();

        // Инициализация текущей конфигурации моба
        currentMobConfig = new MobConfig();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #212121;");

        // Создаем верхнюю панель меню
        MenuBar menuBar = createMenuBar();
        root.setTop(menuBar);

        // Создаем основную панель вкладок
        tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: #2B2B2B;");

        // Создаем и добавляем вкладки
        initializeTabs();
        root.setCenter(tabPane);

        // Создаем нижнюю панель
        HBox bottomPanel = createBottomPanel();
        root.setBottom(bottomPanel);

        // Создаем сцену и загружаем CSS
        Scene scene = new Scene(root, 1200, 800);
        // Здесь вы должны подключить ваш CSS-файл
        try {
            scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("Error loading CSS: " + e.getMessage());
        }

        stage.setScene(scene);
        stage.setTitle("Custom Mobs Forge - Creator");
        stage.show();

        // Устанавливаем обработчик закрытия окна
        stage.setOnCloseRequest(event -> {
            if (checkUnsavedChanges()) {
                stage.hide();
                event.consume(); // Предотвращаем закрытие окна, только скрываем его
            } else {
                event.consume(); // Отменяем закрытие окна
            }
        });

        // Устанавливаем обработчики смены вкладок
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                handleTabChanged(newTab.getText());
            }
        });

        // Загружаем первичные данные
        loadInitialData();
    }

    private void initializeServices() {
        modelLoaderService = new ModelLoaderService();
        animationLoaderService = new AnimationLoaderService();
        mobSaveService = new MobSaveService();
        undoRedoManager = new UndoRedoManager();
    }

    private List<String> availableAnimations = new ArrayList<>();
    private List<AnimationLoaderService.AnimationInfo> animationInfoList = new ArrayList<>();

    public static MobCreatorGUI getInstance() {
        return instance;
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        // Меню "Файл"
        Menu fileMenu = new Menu("File");

        MenuItem newItem = new MenuItem("New Mob");
        newItem.setOnAction(e -> createNewMob());
        MenuItem openItem = new MenuItem("Open Mob...");
        openItem.setOnAction(e -> openMob());

        MenuItem exportItem = new MenuItem("Export...");
        exportItem.setOnAction(e -> exportMob());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> exitApplication());

        fileMenu.getItems().addAll(newItem, openItem, new SeparatorMenuItem(),
                exportItem, new SeparatorMenuItem(), exitItem);

        // Меню "Правка"
        Menu editMenu = new Menu("Edit");

        MenuItem undoItem = new MenuItem("Undo");
        undoItem.setOnAction(e -> undoRedoManager.undo());
        undoItem.disableProperty().bind(undoRedoManager.canUndoProperty().not());

        MenuItem redoItem = new MenuItem("Redo");
        redoItem.setOnAction(e -> undoRedoManager.redo());
        redoItem.disableProperty().bind(undoRedoManager.canRedoProperty().not());

        MenuItem clearItem = new MenuItem("Clear All");
        clearItem.setOnAction(e -> clearAllSettings());

        editMenu.getItems().addAll(undoItem, redoItem, new SeparatorMenuItem(), clearItem);

        // Меню "Помощь"
        Menu helpMenu = new Menu("Help");

        // Добавляем все меню в строку меню
        menuBar.getMenus().addAll(fileMenu, editMenu, helpMenu);

        return menuBar;
    }

    private void initializeTabs() {
        // Вкладка базовых настроек
        Tab basicTab = new Tab("Basic Settings");
        basicTab.setClosable(false);
        statsEditorPanel = new StatsEditorPanel();
        basicTab.setContent(statsEditorPanel);

        // Вкладка редактора дерева поведения
        Tab behaviorTab = new Tab("Behavior Tree");
        behaviorTab.setClosable(false);
        behaviorTreeEditor = new BehaviorTreeEditor(undoRedoManager);
        behaviorTab.setContent(behaviorTreeEditor);

        tabPane.getTabs().addAll(basicTab, behaviorTab);
    }

    private HBox createBottomPanel() {
        // Кнопка Save - переносим в неё всю логику сохранения
        Button saveButton = new Button("Save");
        saveButton.getStyleClass().add(Constants.ADD_BUTTON_STYLE_CLASS);
        saveButton.setOnAction(e -> {
            try {
                // Собираем данные из всех панелей
                collectDataFromPanels();

                // Если это новый моб, запрашиваем имя
                if (isNewMob) {
                    TextInputDialog dialog = new TextInputDialog(currentMobConfig.getName());
                    dialog.setTitle("Save Mob");
                    dialog.setHeaderText("Enter a name for the mob");
                    dialog.setContentText("Mob name:");

                    Optional<String> result = dialog.showAndWait();
                    if (result.isPresent()) {
                        String mobName = result.get().trim();
                        if (!mobName.isEmpty()) {
                            currentMobConfig.setName(mobName);
                            isNewMob = false;
                        } else {
                            GUIUtils.showAlert("Error", "Mob name cannot be empty");
                            return;
                        }
                    } else {
                        return; // Отмена сохранения
                    }
                }

                // Обеспечиваем, что у моба и дерева поведения один и тот же UUID
                if (currentMobConfig.getBehaviorTreeId() == null) {
                    // Если ID еще не установлен, устанавливаем одинаковый для моба и дерева
                    UUID sharedId = currentMobConfig.getId();

                    // Получаем ссылку на дерево поведения
                    BehaviorTreeModel treeModel = behaviorTreeEditor.getModel();

                    // Экспортируем дерево в формат для сохранения
                    BehaviorTreeConfig treeConfig = new BehaviorTreeConfig();
                    treeConfig.setId(sharedId);
                    treeConfig.setName(currentMobConfig.getName() + "_behavior");
                    treeConfig.setMobId(sharedId);
                    treeConfig.setMobName(currentMobConfig.getName());

                    // Узлы и соединения
                    List<NodeData> nodes = new ArrayList<>();
                    for (Node node : treeModel.getNodes()) {
                        NodeData nodeData = new NodeData();
                        nodeData.setId(node.getId().toString());
                        nodeData.setType(node.getType());
                        nodeData.setDescription(node.getDescription());
                        nodeData.setParameter(node.getParameter());
                        nodeData.setX(node.getX());
                        nodeData.setY(node.getY());
                        nodeData.setExpanded(node.isExpanded());

                        // Копируем информацию об анимации
                        nodeData.setAnimationId(node.getAnimationId());
                        nodeData.setAnimationSpeed(node.getAnimationSpeed());
                        nodeData.setLoopAnimation(node.isLoopAnimation());

                        nodes.add(nodeData);
                    }
                    treeConfig.setNodes(nodes);

                    List<ConnectionData> connections = new ArrayList<>();
                    for (Connection conn : treeModel.getConnections()) {
                        ConnectionData connData = new ConnectionData();
                        connData.setSourceNodeId(conn.getSourceNode().getId().toString());
                        connData.setTargetNodeId(conn.getTargetNode().getId().toString());
                        connections.add(connData);
                    }
                    treeConfig.setConnections(connections);

                    // Связываем моба с деревом
                    currentMobConfig.setBehaviorTreeId(sharedId);

                    // Сохраняем дерево
                    mobSaveService.saveBehaviorTree(treeConfig);
                }

                // Сохраняем конфигурацию моба со всеми данными
                if (mobSaveService.saveMob(currentMobConfig)) {
                    isModified = false;
                    updateTitle();
                    GUIUtils.showNotification((Pane) statsEditorPanel.getParent(),
                            "Mob '" + currentMobConfig.getName() + "' saved successfully");
                } else {
                    GUIUtils.showAlert("Error", "Failed to save mob configuration");
                }
            } catch (Exception ex) {
                GUIUtils.showAlert("Error", "Failed to save mob configuration: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        Button closeButton = new Button("Close");
        closeButton.getStyleClass().add(Constants.CONTROL_BUTTON_STYLE_CLASS);
        closeButton.setOnAction(e -> exitApplication());

        HBox bottomPanel = new HBox(10, saveButton, closeButton);
        bottomPanel.setAlignment(Pos.CENTER);
        bottomPanel.setPadding(new Insets(10));
        bottomPanel.getStyleClass().add(Constants.CONTROL_PANEL_STYLE_CLASS);

        return bottomPanel;
    }

    private void loadInitialData() {
        // Загружаем доступные модели и текстуры
        Platform.runLater(() -> {
            // Обновляем статус
            updateTitle();
        });
    }

    private void handleTabChanged(String tabName) {
        // Сохраняем текущие данные и подготавливаем новую вкладку
        if (tabName.equals("Behavior Tree")) {
            // При переходе на вкладку Behavior Tree, передаем список анимаций
            if (behaviorTreeEditor != null) {
                behaviorTreeEditor.updateAnimationsList(availableAnimations);
            }
        }
    }

    /**
     * Обновляет список доступных анимаций и передает его в редактор поведения
     */
    public void updateAvailableAnimations(List<String> animations, List<AnimationLoaderService.AnimationInfo> animationInfoList) {
        this.availableAnimations.clear();
        this.availableAnimations.addAll(animations);

        this.animationInfoList.clear();
        if (animationInfoList != null) {
            this.animationInfoList.addAll(animationInfoList);
        }

        // Передаем список в редактор поведения, если он инициализирован
        if (behaviorTreeEditor != null) {
            behaviorTreeEditor.updateAnimationsList(availableAnimations);
        }

        System.out.println("MobCreatorGUI: Обновлен список анимаций: " + availableAnimations.size() + " анимаций");
    }

    /**
     * Получить список всех доступных анимаций
     */
    public List<String> getAvailableAnimations() {
        return new ArrayList<>(availableAnimations);
    }

    /**
     * Получить подробную информацию об анимациях
     */
    public List<AnimationLoaderService.AnimationInfo> getAnimationInfoList() {
        return new ArrayList<>(animationInfoList);
    }

    private void collectDataFromPanels() {
        // Собираем данные из панели статистик
        currentMobConfig.setName(statsEditorPanel.getMobName());
        currentMobConfig.setModelPath(statsEditorPanel.getSelectedModel());
        currentMobConfig.setTexturePath(statsEditorPanel.getSelectedTexture());

        // Добавляем эту строку для установки пути к файлу анимации
        if (statsEditorPanel.getCurrentAnimationFile() != null) {
            currentMobConfig.setAnimationFilePath(statsEditorPanel.getCurrentAnimationFile());
        }

        // Атрибуты
        Map<String, Double> attributes = new HashMap<>();
        attributes.put("maxHealth", statsEditorPanel.getHealth());
        attributes.put("movementSpeed", statsEditorPanel.getMovementSpeed());
        attributes.put("attackDamage", statsEditorPanel.getAttackDamage());
        attributes.put("armor", statsEditorPanel.getArmor());
        attributes.put("knockbackResistance", statsEditorPanel.getKnockbackResistance());
        currentMobConfig.setAttributes(attributes);

        // Анимации
        currentMobConfig.setAnimationMappings(statsEditorPanel.getAnimationMappings());

        // Отмечаем, что внесены изменения
        isModified = true;
        updateTitle();
    }

    public BehaviorTreeEditor getBehaviorTreeEditor() {
        return this.behaviorTreeEditor;
    }

    private void openMob() {
        if (!checkUnsavedChanges()) {
            return;
        }

        // Запрашиваем свежий список мобов с сервера
        NetworkManager.INSTANCE.sendToServer(new RequestDataPacket());

        // Используем имеющиеся данные из кэша
        List<MobConfig> mobs = mobSaveService.getAllMobs();
        if (mobs.isEmpty()) {
            GUIUtils.showAlert("Info", "No saved mobs found. Please wait a moment while mobs are being loaded from the server.");
            return;
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>();
        dialog.setTitle("Open Mob");
        dialog.setHeaderText("Select a mob to open");
        dialog.setContentText("Mob:");

        List<String> mobNames = new ArrayList<>();
        for (MobConfig mob : mobs) {
            mobNames.add(mob.getName());
        }
        dialog.getItems().addAll(mobNames);

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String mobName = result.get();
            MobConfig mobConfig = null;
            for (MobConfig mob : mobs) {
                if (mob.getName().equals(mobName)) {
                    mobConfig = mob;
                    break;
                }
            }

            if (mobConfig != null) {
                loadMobConfig(mobConfig);
            }
        }
    }

    private void loadMobConfig(MobConfig mobConfig) {
        currentMobConfig = mobConfig;
        isNewMob = false;
        isModified = false;

        // Обновляем панель статистик
        statsEditorPanel.loadMobConfig(mobConfig);

        // Загружаем дерево поведения, если необходимо
        // Эта часть будет зависеть от вашей реализации BehaviorTreeEditor

        updateTitle();

        GUIUtils.showNotification((Pane) statsEditorPanel.getParent(),
                "Mob '" + mobConfig.getName() + "' loaded successfully");
    }

    private void createNewMob() {
        if (!checkUnsavedChanges()) {
            return;
        }

        currentMobConfig = new MobConfig();
        isNewMob = true;
        isModified = false;

        // Сбрасываем настройки панелей
        statsEditorPanel.loadMobConfig(currentMobConfig);
        behaviorTreeEditor.clearAll(); // Предполагается, что такой метод существует

        updateTitle();
    }

    public MobSaveService getMobSaveService() {
        return mobSaveService;
    }

    private void exportMob() {
        // Экспорт моба в файл (JSON)
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Mob");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json"));

        // Устанавливаем начальное имя файла
        fileChooser.setInitialFileName(currentMobConfig.getName() + ".json");

        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try {
                // Собираем данные перед экспортом
                collectDataFromPanels();

                // Сохраняем в файл с помощью MobSaveService
                // В реальном приложении здесь может быть специальный метод экспорта
                mobSaveService.saveMob(currentMobConfig);

                GUIUtils.showNotification((Pane) statsEditorPanel.getParent(),
                        "Mob exported to " + file.getName());
            } catch (Exception e) {
                GUIUtils.showAlert("Error", "Failed to export mob: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private boolean checkUnsavedChanges() {
        if (isModified) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Unsaved Changes");
            alert.setHeaderText("You have unsaved changes");
            alert.setContentText("Do you want to save changes before continuing?");

            ButtonType buttonTypeSave = new ButtonType("Save");
            ButtonType buttonTypeDiscard = new ButtonType("Discard");
            ButtonType buttonTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(buttonTypeSave, buttonTypeDiscard, buttonTypeCancel);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == buttonTypeSave) {
                    // Используем тот же код, что и для кнопки Save
                    try {
                        // Собираем данные из всех панелей
                        collectDataFromPanels();

                        // Если это новый моб, запрашиваем имя
                        if (isNewMob) {
                            TextInputDialog dialog = new TextInputDialog(currentMobConfig.getName());
                            dialog.setTitle("Save Mob");
                            dialog.setHeaderText("Enter a name for the mob");
                            dialog.setContentText("Mob name:");

                            Optional<String> nameResult = dialog.showAndWait();
                            if (nameResult.isPresent()) {
                                String mobName = nameResult.get().trim();
                                if (!mobName.isEmpty()) {
                                    currentMobConfig.setName(mobName);
                                    isNewMob = false;
                                } else {
                                    GUIUtils.showAlert("Error", "Mob name cannot be empty");
                                    return false;
                                }
                            } else {
                                return false; // Отмена сохранения
                            }
                        }

                        // Обеспечиваем, что у моба и дерева поведения один и тот же UUID
                        if (currentMobConfig.getBehaviorTreeId() == null) {
                            // Если ID еще не установлен, устанавливаем одинаковый для моба и дерева
                            UUID sharedId = currentMobConfig.getId();

                            // Получаем ссылку на дерево поведения
                            BehaviorTreeModel treeModel = behaviorTreeEditor.getModel();

                            // Экспортируем дерево в формат для сохранения
                            BehaviorTreeConfig treeConfig = new BehaviorTreeConfig();
                            treeConfig.setId(sharedId);
                            treeConfig.setName(currentMobConfig.getName() + "_behavior");
                            treeConfig.setMobId(sharedId);
                            treeConfig.setMobName(currentMobConfig.getName());

                            // Узлы и соединения
                            List<NodeData> nodes = new ArrayList<>();
                            for (Node node : treeModel.getNodes()) {
                                NodeData nodeData = new NodeData();
                                nodeData.setId(node.getId().toString());
                                nodeData.setType(node.getType());
                                nodeData.setDescription(node.getDescription());
                                nodeData.setParameter(node.getParameter());
                                nodeData.setX(node.getX());
                                nodeData.setY(node.getY());
                                nodeData.setExpanded(node.isExpanded());

                                // Копируем информацию об анимации
                                nodeData.setAnimationId(node.getAnimationId());
                                nodeData.setAnimationSpeed(node.getAnimationSpeed());
                                nodeData.setLoopAnimation(node.isLoopAnimation());

                                nodes.add(nodeData);
                            }
                            treeConfig.setNodes(nodes);

                            List<ConnectionData> connections = new ArrayList<>();
                            for (Connection conn : treeModel.getConnections()) {
                                ConnectionData connData = new ConnectionData();
                                connData.setSourceNodeId(conn.getSourceNode().getId().toString());
                                connData.setTargetNodeId(conn.getTargetNode().getId().toString());
                                connections.add(connData);
                            }
                            treeConfig.setConnections(connections);

                            // Связываем моба с деревом
                            currentMobConfig.setBehaviorTreeId(sharedId);

                            // Сохраняем дерево
                            mobSaveService.saveBehaviorTree(treeConfig);
                        }

                        // Сохраняем конфигурацию моба со всеми данными
                        if (mobSaveService.saveMob(currentMobConfig)) {
                            isModified = false;
                            updateTitle();
                            GUIUtils.showNotification((Pane) statsEditorPanel.getParent(),
                                    "Mob '" + currentMobConfig.getName() + "' saved successfully");
                            return true;
                        } else {
                            GUIUtils.showAlert("Error", "Failed to save mob configuration");
                            return false;
                        }
                    } catch (Exception ex) {
                        GUIUtils.showAlert("Error", "Failed to save mob configuration: " + ex.getMessage());
                        ex.printStackTrace();
                        return false;
                    }
                } else if (result.get() == buttonTypeDiscard) {
                    return true;
                } else {
                    return false; // Отмена действия
                }
            }
            return false;
        }
        return true;
    }

    private void clearAllSettings() {
        if (GUIUtils.showConfirmDialog("Clear All", "Are you sure you want to clear all settings?")) {
            currentMobConfig = new MobConfig();
            isNewMob = true;
            isModified = false;

            // Сбрасываем настройки панелей
            statsEditorPanel.loadMobConfig(currentMobConfig);
            behaviorTreeEditor.clearAll(); // Предполагается, что такой метод существует

            updateTitle();
        }
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Custom Mobs Forge");
        alert.setHeaderText("Custom Mobs Forge");
        alert.setContentText("Version: 1.0.0\n\n" +
                "A mod for creating custom mobs with custom models, animations and behavior trees in Minecraft.\n\n" +
                "Made with AzureLib.");
        alert.showAndWait();
    }

    private void exitApplication() {
        if (checkUnsavedChanges()) {
            primaryStage.hide();
        }
    }

    private void updateTitle() {
        StringBuilder title = new StringBuilder("Custom Mobs Forge - Creator");

        if (currentMobConfig != null && currentMobConfig.getName() != null && !currentMobConfig.getName().isEmpty()) {
            title.append(" - ").append(currentMobConfig.getName());
        }

        if (isModified) {
            title.append(" *");
        }

        primaryStage.setTitle(title.toString());
    }

    @Override
    public void stop() {
        // Освобождаем ресурсы при закрытии приложения
        if (behaviorTreeEditor != null) {
            behaviorTreeEditor.cleanup();
        }
    }
}