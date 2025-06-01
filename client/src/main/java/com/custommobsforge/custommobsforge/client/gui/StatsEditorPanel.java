package com.custommobsforge.custommobsforge.client.gui;

import com.custommobsforge.custommobsforge.client.gui.AnimationConfig;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class StatsEditorPanel extends VBox {
    // Основные поля моба
    private TextField mobNameField;
    private ComboBox<String> modelSelector;
    private ComboBox<String> textureSelector;

    // Статистики моба
    private Slider healthSlider;
    private Label healthValue;
    private Slider damageSlider;
    private Label damageValue;
    private Slider speedSlider;
    private Label speedValue;
    private Slider knockbackSlider;
    private Label knockbackValue;
    private Slider armorSlider;
    private Label armorValue;

    // Для выбора биомов спавна
    private ListView<String> biomeListView;
    private ObservableList<String> selectedBiomes = FXCollections.observableArrayList();

    // Настройки спавна
    private CheckBox canSpawnDayCheckbox;
    private CheckBox canSpawnNightCheckbox;
    private Slider spawnWeightSlider;
    private Label spawnWeightValue;

    // Анимации
    private ComboBox<String> actionAnimationSelector;
    private ObservableList<String> availableAnimations = FXCollections.observableArrayList();

    // Маппинги анимаций для разных действий
    private Map<String, AnimationConfig> animationMappings = new HashMap<>();
    private ListView<String> actionListView;
    private String selectedAction;

    // Сервисы
    private ModelLoaderService modelLoader;
    private AnimationLoaderService animationLoader;

    // Текущие выбранные элементы
    private String currentModel;
    private String currentTexture;
    private String currentAnimationFile;

    public StatsEditorPanel() {
        setPadding(new Insets(20));
        setSpacing(15);
        getStyleClass().add("mob-settings");

        // Инициализация сервисов
        modelLoader = new ModelLoaderService();
        animationLoader = new AnimationLoaderService();

        // Заголовок
        Label headerLabel = createHeaderLabel("Mob Creation");

        // Создаем основные разделы
        VBox basicInfoPane = createBasicInfoPane();
        HBox mainContent = new HBox(20);

        // Левая панель - только статистики, без настроек спавна
        VBox statsPane = createStatsPane();
        VBox leftPane = new VBox(15, statsPane);

        // Правая панель - настройки анимаций
        VBox animationsPane = createAnimationsPane();
        VBox rightPane = new VBox(15, animationsPane);

        // Добавляем панели в основной контейнер
        mainContent.getChildren().addAll(leftPane, rightPane);

        // Добавляем все элементы в основной контейнер
        getChildren().addAll(headerLabel, new Separator(), basicInfoPane,
                new Separator(), mainContent);

        // Загружаем доступные модели, текстуры и анимации
        loadAvailableAssets();
    }

    private Label createHeaderLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("header-label");
        label.setFont(Font.font("System", FontWeight.BOLD, 18));
        return label;
    }

    public List<String> getAvailableAnimationsList() {
        return new ArrayList<>(availableAnimations);
    }

    private VBox createBasicInfoPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        HBox nameBox = new HBox(10);
        nameBox.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label("Mob Name:");
        nameLabel.setMinWidth(100);

        mobNameField = new TextField();
        mobNameField.setPromptText("Enter mob name");
        mobNameField.setPrefWidth(200);
        mobNameField.getStyleClass().add(Constants.INPUT_FIELD_STYLE_CLASS);
        HBox.setHgrow(mobNameField, Priority.ALWAYS);

        nameBox.getChildren().addAll(nameLabel, mobNameField);

        // Селектор модели
        HBox modelBox = new HBox(10);
        modelBox.setAlignment(Pos.CENTER_LEFT);

        Label modelLabel = new Label("Model:");
        modelLabel.setMinWidth(100);

        modelSelector = new ComboBox<>();
        modelSelector.setPromptText("Select Model");
        modelSelector.setPrefWidth(300);
        modelSelector.getStyleClass().add(Constants.INPUT_FIELD_STYLE_CLASS);
        modelSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(currentModel)) {
                currentModel = newVal;
                updateTexturesForModel(newVal);
            }
        });

        modelBox.getChildren().addAll(modelLabel, modelSelector);

        // Селектор текстуры
        HBox textureBox = new HBox(10);
        textureBox.setAlignment(Pos.CENTER_LEFT);

        Label textureLabel = new Label("Texture:");
        textureLabel.setMinWidth(100);

        textureSelector = new ComboBox<>();
        textureSelector.setPromptText("Select Texture");
        textureSelector.setPrefWidth(300);
        textureSelector.getStyleClass().add(Constants.INPUT_FIELD_STYLE_CLASS);
        textureSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(currentTexture)) {
                currentTexture = newVal;
            }
        });

        textureBox.getChildren().addAll(textureLabel, textureSelector);

        // Селектор файла анимации
        HBox animationFileBox = new HBox(10);
        animationFileBox.setAlignment(Pos.CENTER_LEFT);

        Label animationFileLabel = new Label("Animation File:");
        animationFileLabel.setMinWidth(100);

        TextField animationFileField = new TextField();
        animationFileField.setPromptText("Enter animation file name");
        animationFileField.setPrefWidth(300);
        animationFileField.getStyleClass().add(Constants.INPUT_FIELD_STYLE_CLASS);

        List<String> animationFiles = loadAvailableAnimationFiles();
        ComboBox<String> animationFileSelector = new ComboBox<>();
        animationFileSelector.setPromptText("Select from available");
        animationFileSelector.getItems().addAll(animationFiles);
        animationFileSelector.setPrefWidth(180);
        animationFileSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                animationFileField.setText(newVal);
                if (!newVal.startsWith("assets/")) {
                    currentAnimationFile = "assets/custommobsforge/animations/" + newVal;
                } else {
                    currentAnimationFile = newVal;
                }
            }
        });

        animationFileField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                if (!newVal.startsWith("assets/")) {
                    currentAnimationFile = "assets/custommobsforge/animations/" + newVal;
                } else {
                    currentAnimationFile = newVal;
                }
            }
        });

        Button parseAnimationButton = new Button("Parse Animations");
        parseAnimationButton.getStyleClass().add(Constants.ADD_BUTTON_STYLE_CLASS);
        parseAnimationButton.setOnAction(e -> {
            System.out.println("Parsing animation file: " + currentAnimationFile);
            if (currentAnimationFile != null && !currentAnimationFile.trim().isEmpty()) {
                parseAnimationFile(currentAnimationFile);
            } else {
                GUIUtils.showNotification(this, "Please enter animation file name");
            }
        });

        animationFileBox.getChildren().addAll(
                animationFileLabel,
                animationFileField,
                animationFileSelector,
                parseAnimationButton
        );

        pane.getChildren().addAll(nameBox, modelBox, textureBox, animationFileBox);

        return pane;
    }

    /**
     * Загружает список доступных файлов анимаций из ресурсов
     */
    private List<String> loadAvailableAnimationFiles() {
        List<String> files = new ArrayList<>();

        // Добавляем отладочную информацию
        System.out.println("Looking for animation files...");

        try {
            ClassLoader classLoader = getClass().getClassLoader();
            String basePath = "assets/custommobsforge/animations/";

            // Получаем URL ресурса
            URL resourceUrl = classLoader.getResource(basePath);
            if (resourceUrl != null) {
                System.out.println("Found directory: " + basePath);

                // Читаем все файлы в директории
                try {
                    java.nio.file.Path path = Paths.get(resourceUrl.toURI());
                    if (Files.isDirectory(path)) {
                        Files.walk(path)
                                .filter(Files::isRegularFile)
                                .filter(p -> p.toString().endsWith(".animation.json"))
                                .forEach(p -> {
                                    String fileName = p.getFileName().toString();
                                    String filePath = basePath + fileName;
                                    System.out.println("Adding file: " + filePath);
                                    files.add(filePath);
                                });
                    } else {
                        System.out.println("Path is not a directory: " + path);
                    }
                } catch (Exception e) {
                    System.err.println("Error walking directory: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("Directory not found: " + basePath);
            }
        } catch (Exception e) {
            System.err.println("Error accessing resources: " + e.getMessage());
            e.printStackTrace();
        }

        // Если не нашли файлы, добавляем заглушки для демонстрации
        if (files.isEmpty()) {
            System.out.println("No animation files found, adding placeholder entries");
            files.add("assets/custommobsforge/animations/custom_mob.animation.json");
            files.add("assets/custommobsforge/animations/god.animation.json");
            files.add("assets/custommobsforge/animations/overlord.animation.json");
        }

        System.out.println("Total animation files found: " + files.size());
        return files;
    }

    /**
     * Парсит файл анимаций и извлекает имена всех анимаций
     */
    private void parseAnimationFile(String animationFilePath) {
        try {
            System.out.println("=== НАЧАЛО ПАРСИНГА ФАЙЛА АНИМАЦИЙ ===");
            System.out.println("Парсинг файла: " + animationFilePath);
            currentAnimationFile = animationFilePath;

            // Очищаем текущие списки анимаций
            availableAnimations.clear();

            if (actionAnimationSelector != null) {
                actionAnimationSelector.getItems().clear();
            }

            // Основной парсинг анимаций
            List<AnimationParser.AnimationInfo> animInfoList = AnimationParser.findAndParseAnimationFile(animationFilePath);

            System.out.println("Результат парсинга: найдено " + animInfoList.size() + " анимаций");

            // Логируем найденные анимации
            for (AnimationParser.AnimationInfo info : animInfoList) {
                System.out.println("Анимация: " + info.getId() + ", отображаемое имя: " +
                        info.getDisplayName() + ", зацикленная: " + info.isLoop());

                // Добавляем ID анимации в доступные анимации
                availableAnimations.add(info.getId());
            }

            // Обновляем выпадающие списки
            if (actionAnimationSelector != null) {
                actionAnimationSelector.getItems().addAll(availableAnimations);
            }

            // Передаем список анимаций в GUI
            MobCreatorGUI gui = findMobCreatorGUI();
            if (gui != null) {
                System.out.println("Передаем список анимаций в MobCreatorGUI");

                // Конвертируем в AnimationLoaderService.AnimationInfo
                List<AnimationLoaderService.AnimationInfo> convertedList = new ArrayList<>();
                for (AnimationParser.AnimationInfo info : animInfoList) {
                    convertedList.add(new AnimationLoaderService.AnimationInfo(
                            info.getId(), info.getDisplayName(), info.isLoop()
                    ));
                }
                gui.updateAvailableAnimations(availableAnimations, convertedList);
            } else {
                System.err.println("Предупреждение: Не удалось найти MobCreatorGUI для обновления списка анимаций");
            }

            System.out.println("=== ПАРСИНГ ФАЙЛА АНИМАЦИЙ ЗАВЕРШЕН УСПЕШНО ===");
            GUIUtils.showNotification(this, "Найдено " + availableAnimations.size() + " анимаций");

        } catch (Exception e) {
            System.err.println("=== КРИТИЧЕСКАЯ ОШИБКА ПРИ ПАРСИНГЕ ФАЙЛА АНИМАЦИЙ ===");
            System.err.println("Сообщение ошибки: " + e.getMessage());
            e.printStackTrace();

            // В случае ошибки добавляем стандартные заглушки
            availableAnimations.add("idle");
            availableAnimations.add("walk");
            availableAnimations.add("attack");

            List<AnimationLoaderService.AnimationInfo> defaultInfoList = new ArrayList<>();
            defaultInfoList.add(new AnimationLoaderService.AnimationInfo("idle", "Idle", true));
            defaultInfoList.add(new AnimationLoaderService.AnimationInfo("walk", "Walk", true));
            defaultInfoList.add(new AnimationLoaderService.AnimationInfo("attack", "Attack", false));

            // Передаем список в GUI даже при ошибке
            MobCreatorGUI gui = findMobCreatorGUI();
            if (gui != null) {
                gui.updateAvailableAnimations(availableAnimations, defaultInfoList);
            }

            GUIUtils.showAlert("Ошибка", "Не удалось разобрать файл анимаций: " + e.getMessage());
        }
    }

    private MobCreatorGUI findMobCreatorGUI() {
        if (MobCreatorGUI.getInstance() != null) {
            return MobCreatorGUI.getInstance();
        }

        javafx.scene.Parent parent = getParent();
        while (parent != null) {
            if (parent.getScene() != null && parent.getScene().getRoot() != null &&
                    parent.getScene().getWindow() != null) {
                if (parent.getScene().getRoot() instanceof BorderPane) {
                    return MobCreatorGUI.getInstance();
                }
            }
            parent = parent.getParent();
        }
        return null;
    }

    private VBox createStatsPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));
        pane.getStyleClass().add("stats-pane");

        Label statsHeader = new Label("Mob Statistics");
        statsHeader.setFont(Font.font("System", FontWeight.BOLD, 14));

        // Здоровье
        HBox healthBox = createStatSlider("Health", 1, 100, 20, value ->
                String.format("%.0f ❤", value));
        healthSlider = (Slider) healthBox.getChildren().get(1);
        healthValue = (Label) healthBox.getChildren().get(2);

        // Урон
        HBox damageBox = createStatSlider("Attack Damage", 0, 30, 3, value ->
                String.format("%.1f 🗡", value));
        damageSlider = (Slider) damageBox.getChildren().get(1);
        damageValue = (Label) damageBox.getChildren().get(2);

        // Скорость
        HBox speedBox = createStatSlider("Movement Speed", 0.1, 0.5, 0.25, value ->
                String.format("%.2f ⚡", value));
        speedSlider = (Slider) speedBox.getChildren().get(1);
        speedValue = (Label) speedBox.getChildren().get(2);

        // Отбрасывание
        HBox knockbackBox = createStatSlider("Knockback Resistance", 0, 1, 0, value ->
                String.format("%.1f", value));
        knockbackSlider = (Slider) knockbackBox.getChildren().get(1);
        knockbackValue = (Label) knockbackBox.getChildren().get(2);

        // Броня
        HBox armorBox = createStatSlider("Armor", 0, 20, 0, value ->
                String.format("%.0f 🛡", value));
        armorSlider = (Slider) armorBox.getChildren().get(1);
        armorValue = (Label) armorBox.getChildren().get(2);

        pane.getChildren().addAll(statsHeader, healthBox, damageBox, speedBox, knockbackBox, armorBox);

        return pane;
    }

    private HBox createStatSlider(String name, double min, double max, double defaultValue,
                                  java.util.function.Function<Double, String> formatter) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(name + ":");
        nameLabel.setMinWidth(120);

        Slider slider = new Slider(min, max, defaultValue);
        slider.setPrefWidth(150);
        HBox.setHgrow(slider, Priority.ALWAYS);

        Label valueLabel = new Label(formatter.apply(defaultValue));
        valueLabel.setMinWidth(60);

        slider.valueProperty().addListener((obs, oldVal, newVal) ->
                valueLabel.setText(formatter.apply(newVal.doubleValue())));

        box.getChildren().addAll(nameLabel, slider, valueLabel);

        return box;
    }

    private VBox createAnimationsPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));
        pane.getStyleClass().add("animations-pane");

        Label animationsHeader = new Label("Animation Mappings");
        animationsHeader.setFont(Font.font("System", FontWeight.BOLD, 14));

        // Реализуем более простой и понятный интерфейс для выбора анимаций
        HBox mappingsContainer = new HBox(15);

        // Список действий
        VBox actionsBox = new VBox(5);
        Label actionsLabel = new Label("Action Types:");

        // Создаем список типов действий
        ObservableList<String> actionTypes = FXCollections.observableArrayList(
                "IDLE", "WALK", "RUN", "ATTACK_MELEE", "ATTACK_RANGED",
                "DEATH", "HURT", "JUMP", "SPAWN", "SPECIAL_1", "SPECIAL_2"
        );

        actionListView = new ListView<>(actionTypes);
        actionListView.setPrefHeight(200);
        actionListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        selectedAction = newVal;
                        loadAnimationConfig(newVal);
                    }
                }
        );

        Button addActionButton = new Button("Add Custom Action");
        addActionButton.setMaxWidth(Double.MAX_VALUE);
        addActionButton.setOnAction(e -> showAddActionDialog());

        actionsBox.getChildren().addAll(actionsLabel, actionListView, addActionButton);

        // Настройки анимации
        VBox configBox = new VBox(10);
        configBox.setPrefWidth(300);

        Label configLabel = new Label("Animation Settings:");

        // Выбор анимации для действия
        Label animationLabel = new Label("Select Animation:");
        actionAnimationSelector = new ComboBox<>();
        actionAnimationSelector.setMaxWidth(Double.MAX_VALUE);
        actionAnimationSelector.setPromptText("Select Animation");

        // Зацикливание
        CheckBox loopCheckbox = new CheckBox("Loop Animation");
        loopCheckbox.setSelected(true);

        // Скорость
        Label speedLabel = new Label("Animation Speed:");
        Slider speedSlider = new Slider(0.1, 3.0, 1.0);
        speedSlider.setShowTickMarks(true);
        speedSlider.setShowTickLabels(true);
        speedSlider.setMajorTickUnit(0.5);

        Label speedValue = new Label("1.0x");
        speedValue.setMinWidth(40);

        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            speedValue.setText(String.format("%.1fx", value));
        });

        HBox speedBox = new HBox(10, speedSlider, speedValue);
        speedBox.setAlignment(Pos.CENTER_LEFT);

        // Описание анимации
        Label descriptionLabel = new Label("Animation Description:");
        TextArea descriptionArea = new TextArea();
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);

        // Кнопки
        HBox buttonsBox = new HBox(10);
        buttonsBox.setAlignment(Pos.CENTER_RIGHT);

        Button applyButton = new Button("Apply");
        applyButton.getStyleClass().add(Constants.ADD_BUTTON_STYLE_CLASS);
        applyButton.setOnAction(e -> {
            if (selectedAction != null && actionAnimationSelector.getValue() != null) {
                AnimationConfig config = new AnimationConfig(
                        actionAnimationSelector.getValue(),
                        loopCheckbox.isSelected(),
                        (float) speedSlider.getValue()
                );
                config.setDescription(descriptionArea.getText());

                animationMappings.put(selectedAction, config);

                GUIUtils.showNotification(pane,
                        "Animation '" + config.getAnimationName() + "' applied to action '" + selectedAction + "'");
            } else {
                GUIUtils.showNotification(pane, "Please select an action and animation");
            }
        });

        Button removeButton = new Button("Remove");
        removeButton.setOnAction(e -> {
            if (selectedAction != null) {
                animationMappings.remove(selectedAction);
                GUIUtils.showNotification(pane,
                        "Animation mapping for '" + selectedAction + "' removed");

                actionAnimationSelector.setValue(null);
                loopCheckbox.setSelected(true);
                speedSlider.setValue(1.0);
                descriptionArea.setText("");
            } else {
                GUIUtils.showNotification(pane, "Please select an action to remove");
            }
        });

        buttonsBox.getChildren().addAll(removeButton, applyButton);

        configBox.getChildren().addAll(
                configLabel,
                animationLabel, actionAnimationSelector,
                loopCheckbox,
                speedLabel, speedBox,
                descriptionLabel, descriptionArea,
                buttonsBox
        );

        mappingsContainer.getChildren().addAll(actionsBox, configBox);

        pane.getChildren().addAll(animationsHeader, mappingsContainer);

        return pane;
    }

    private void loadAvailableAssets() {
        Platform.runLater(() -> {
            List<String> models = modelLoader.getAvailableModels();
            modelSelector.getItems().addAll(models);

            if (!models.isEmpty()) {
                modelSelector.setValue(models.get(0));
            }
        });
    }

    private void updateTexturesForModel(String modelPath) {
        textureSelector.getItems().clear();
        List<String> textures = modelLoader.getTexturesForModel(modelPath);
        textureSelector.getItems().addAll(textures);

        if (!textures.isEmpty()) {
            textureSelector.setValue(textures.get(0));
        }
    }

    private void loadAnimationConfig(String action) {
        AnimationConfig config = animationMappings.get(action);

        if (config != null) {
            Platform.runLater(() -> {
                actionAnimationSelector.setValue(config.getAnimationName());
            });
        } else {
            Platform.runLater(() -> {
                if (!availableAnimations.isEmpty()) {
                    String actionLower = action.toLowerCase();
                    Optional<String> matchingAnim = availableAnimations.stream()
                            .filter(anim -> anim.toLowerCase().contains(actionLower))
                            .findFirst();

                    if (matchingAnim.isPresent()) {
                        actionAnimationSelector.setValue(matchingAnim.get());
                    } else {
                        actionAnimationSelector.setValue(availableAnimations.get(0));
                    }
                } else {
                    actionAnimationSelector.setValue(null);
                }
            });
        }
    }

    private void showAddActionDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Custom Action");
        dialog.setHeaderText("Enter a name for the custom action");
        dialog.setContentText("Action name:");

        dialog.showAndWait().ifPresent(actionName -> {
            if (!actionListView.getItems().contains(actionName)) {
                actionListView.getItems().add(actionName);
                actionListView.getSelectionModel().select(actionName);
            } else {
                GUIUtils.showAlert("Error", "Action with this name already exists");
            }
        });
    }

    private BehaviorTreeConfig exportTreeFromEditor(BehaviorTreeEditor editor) {
        BehaviorTreeConfig config = new BehaviorTreeConfig();

        try {
            BehaviorTreeModel model = editor.getModel();

            List<NodeData> nodes = new ArrayList<>();
            for (Node node : model.getNodes()) {
                NodeData nodeData = new NodeData();
                nodeData.setId(node.getId().toString());
                nodeData.setType(node.getType());
                nodeData.setDescription(node.getDescription());
                nodeData.setParameter(node.getParameter());
                nodeData.setX(node.getX());
                nodeData.setY(node.getY());
                nodeData.setExpanded(node.isExpanded());
                nodeData.setAnimationId(node.getAnimationId());
                nodeData.setAnimationSpeed(node.getAnimationSpeed());
                nodeData.setLoopAnimation(node.isLoopAnimation());
                nodes.add(nodeData);
            }
            config.setNodes(nodes);

            List<ConnectionData> connections = new ArrayList<>();
            for (Connection conn : model.getConnections()) {
                ConnectionData connData = new ConnectionData();
                connData.setSourceNodeId(conn.getSourceNode().getId().toString());
                connData.setTargetNodeId(conn.getTargetNode().getId().toString());
                connections.add(connData);
            }
            config.setConnections(connections);

        } catch (Exception e) {
            System.err.println("Error exporting behavior tree: " + e.getMessage());
            e.printStackTrace();
        }

        return config;
    }

    private void previewMobInGame() {
        GUIUtils.showAlert("Info", "Preview functionality is not implemented yet");
    }

    public Map<String, AnimationConfig> getAnimationMappings() {
        return new HashMap<>(animationMappings);
    }

    public void setAnimationMappings(Map<String, AnimationConfig> mappings) {
        if (mappings != null) {
            this.animationMappings = new HashMap<>(mappings);
            if (selectedAction != null) {
                loadAnimationConfig(selectedAction);
            }
        }
    }

    public String getMobName() {
        return mobNameField.getText();
    }

    public String getSelectedModel() {
        return currentModel;
    }

    public String getSelectedTexture() {
        return currentTexture;
    }

    public double getHealth() {
        return healthSlider.getValue();
    }

    public double getAttackDamage() {
        return damageSlider.getValue();
    }

    public double getMovementSpeed() {
        return speedSlider.getValue();
    }

    public double getArmor() {
        return armorSlider.getValue();
    }

    public double getKnockbackResistance() {
        return knockbackSlider.getValue();
    }

    public boolean getCanSpawnDay() {
        return true;
    }

    public boolean getCanSpawnNight() {
        return true;
    }

    public List<String> getSelectedBiomes() {
        return new ArrayList<>();
    }

    public String getCurrentAnimationFile() {
        return currentAnimationFile;
    }

    public void loadMobConfig(MobConfig config) {
        if (config == null) {
            return;
        }

        mobNameField.setText(config.getName());

        if (config.getModelPath() != null && !modelSelector.getItems().contains(config.getModelPath())) {
            modelSelector.getItems().add(config.getModelPath());
        }

        if (config.getModelPath() != null) {
            modelSelector.setValue(config.getModelPath());
        }

        if (config.getTexturePath() != null && !textureSelector.getItems().contains(config.getTexturePath())) {
            textureSelector.getItems().add(config.getTexturePath());
        }

        if (config.getTexturePath() != null) {
            textureSelector.setValue(config.getTexturePath());
        }

        Map<String, Double> attributes = config.getAttributes();
        if (attributes != null) {
            if (attributes.containsKey("maxHealth")) {
                healthSlider.setValue(attributes.get("maxHealth"));
            }
            if (attributes.containsKey("movementSpeed")) {
                speedSlider.setValue(attributes.get("movementSpeed"));
            }
            if (attributes.containsKey("attackDamage")) {
                damageSlider.setValue(attributes.get("attackDamage"));
            }
            if (attributes.containsKey("armor")) {
                armorSlider.setValue(attributes.get("armor"));
            }
            if (attributes.containsKey("knockbackResistance")) {
                knockbackSlider.setValue(attributes.get("knockbackResistance"));
            }
        }

        setAnimationMappings(config.getAnimationMappings());

        if (config.getAnimationFilePath() != null && !config.getAnimationFilePath().isEmpty()) {
            currentAnimationFile = config.getAnimationFilePath();
            parseAnimationFile(currentAnimationFile);
        }
    }
}