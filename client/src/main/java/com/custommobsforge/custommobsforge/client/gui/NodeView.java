package com.custommobsforge.custommobsforge.client.gui;

import com.custommobsforge.custommobsforge.client.gui.Node;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import javafx.util.StringConverter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class NodeView extends VBox {
    private final Node node;
    private Circle inputPin;
    private Circle outputPin;
    private boolean isDragging = false;
    private Consumer<Node> onNodeSelected;
    private Consumer<NodeView> onNodeDragged;
    private Consumer<NodeView> onNodeReleased;
    private Runnable onNodeDeleted;
    private BiConsumer<NodeView, String> onConnectionStarted;
    private ContextMenu contextMenu;
    private double nodeScale = 1.0;

    // Содержимое узла
    private VBox contentContainer;

    // Карта для хранения динамических параметров
    private Map<String, Object> dynamicParams = new HashMap<>();

    // Соединения для портов
    private List<ConnectionView> inputConnections = new ArrayList<>();
    private List<ConnectionView> outputConnections = new ArrayList<>();

    // Веса для WeightedSelectorNode
    private Map<String, Double> childNodeWeights = new HashMap<>();

    // Анимации
    private List<String> availableAnimations = new ArrayList<>();

    // Для анимации перемещения
    private double layoutXBeforeDrag;
    private double layoutYBeforeDrag;

    // Инициализация компонентов узла
    public NodeView(Node node) {
        this.node = node;

        // Заполняем список анимаций (в будущем будет загружаться из файла)
        initializeAvailableAnimations();

        // Применяем CSS-стили
        getStyleClass().add(Constants.NODE_VIEW_STYLE_CLASS);

        // Включаем кэширование для ускорения отрисовки
        setCache(true);
        setCacheHint(CacheHint.SPEED);

        // Создаем хедер узла с пинами
        HBox header = createNodeHeader();

        // Создаем контейнер для содержимого узла
        contentContainer = new VBox(5);
        contentContainer.setPadding(new Insets(8));
        contentContainer.getStyleClass().add(Constants.NODE_CONTENT_STYLE_CLASS);

        // Создаем контент в зависимости от типа узла
        populateContent();

        // Управляем видимостью содержимого
        contentContainer.visibleProperty().bind(node.expandedProperty());
        contentContainer.managedProperty().bind(node.expandedProperty());

        // Добавляем компоненты
        getChildren().addAll(header, contentContainer);

        // Создаем контекстное меню
        createContextMenu();

        // Устанавливаем обработчики взаимодействия
        setupInteractions();

        // Парсим параметры узла
        parseNodeParameters();
    }

    // Инициализация доступных анимаций (заглушка, будет заменена динамической загрузкой)
    private void initializeAvailableAnimations() {
        availableAnimations.add("idle");
        availableAnimations.add("walk");
        availableAnimations.add("run");
        availableAnimations.add("attack");
        availableAnimations.add("hit");
        availableAnimations.add("death");
        availableAnimations.add("jump");
        availableAnimations.add("swim");
        availableAnimations.add("fly");
        availableAnimations.add("eat");
    }

    // Парсинг параметров узла из строки
    private void parseNodeParameters() {
        String params = node.getParameter();
        if (params == null || params.isEmpty()) {
            return;
        }

        try {
            // Формат: key1=value1;key2=value2;key3=value3
            String[] paramPairs = params.split(";");
            for (String pair : paramPairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    dynamicParams.put(key, value);
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при разборе параметров узла: " + e.getMessage());
        }
    }

    // Сохранение параметров узла в строку
    private void saveNodeParameters() {
        StringBuilder paramBuilder = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, Object> entry : dynamicParams.entrySet()) {
            if (!first) {
                paramBuilder.append(";");
            }
            paramBuilder.append(entry.getKey()).append("=").append(entry.getValue().toString());
            first = false;
        }

        node.setParameter(paramBuilder.toString());
    }

    // Добавляем в NodeView.java

    /**
     * Обновить список доступных анимаций
     */
    public void updateAnimationsList(List<String> animations) {
        // Очищаем текущий список
        availableAnimations.clear();

        // Добавляем новые анимации
        if (animations != null && !animations.isEmpty()) {
            availableAnimations.addAll(animations);
        } else {
            // Если список пуст, добавляем стандартные заглушки
            availableAnimations.add("idle");
            availableAnimations.add("walk");
            availableAnimations.add("attack");
        }

        // Обновляем содержимое, если это узел с анимацией
        if (node.getType().equals("PlayAnimationNode") ||
                node.getType().equals("AttackNode")) {
            populateContent();
        }
    }

    private HBox createNodeHeader() {
        HBox header = new HBox(5);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(5, 8, 5, 8));

        // Применяем CSS-стили для заголовка
        header.getStyleClass().add(Constants.NODE_HEADER_STYLE_CLASS);
        // Добавляем стиль для конкретного типа узла
        header.getStyleClass().add("node-header-" + node.getType());

        // Создаем пины
        inputPin = new Circle(6);
        inputPin.getStyleClass().add(Constants.PIN_STYLE_CLASS);
        inputPin.setUserData("in");

        outputPin = new Circle(6);
        outputPin.getStyleClass().add(Constants.PIN_STYLE_CLASS);
        outputPin.setUserData("out");

        // Создаем контейнеры для пинов
        StackPane inConnector = new StackPane();
        inConnector.setMinSize(20, 20);
        inConnector.setTranslateX(-18);

        // Увеличенная зона попадания (невидимая)
        Circle inHitArea = new Circle(14);
        inHitArea.setFill(javafx.scene.paint.Color.TRANSPARENT);
        inHitArea.setUserData("in");

        inConnector.getChildren().addAll(inHitArea, inputPin);

        StackPane outConnector = new StackPane();
        outConnector.setMinSize(20, 20);
        outConnector.setTranslateX(18);

        // Увеличенная зона попадания (невидимая)
        Circle outHitArea = new Circle(14);
        outHitArea.setFill(javafx.scene.paint.Color.TRANSPARENT);
        outHitArea.setUserData("out");

        outConnector.getChildren().addAll(outHitArea, outputPin);

        // Настраиваем обработчики для пинов
        setupPinInteractions(inConnector, inputPin, "in");
        setupPinInteractions(outConnector, outputPin, "out");

        // Заголовок узла
        Label nodeTitle = new Label();
        nodeTitle.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        nodeTitle.textProperty().bind(
                Bindings.createStringBinding(() -> {
                    if (node.isExpanded()) {
                        return node.getType();
                    } else {
                        String desc = node.getDescription();
                        if (desc.length() > 20) {
                            desc = desc.substring(0, 17) + "...";
                        }
                        return node.getType() + ": " + desc;
                    }
                }, node.expandedProperty(), node.typeProperty(), node.descriptionProperty())
        );

        // Добавляем иконку с описанием
        Button infoButton = createInfoButton();

        // Кнопка раскрытия/сворачивания
        Button expandButton = createExpandButton();

        // Компоновка элементов
        header.getChildren().addAll(
                inConnector,
                nodeTitle,
                new Pane() {{ HBox.setHgrow(this, Priority.ALWAYS); }},
                infoButton,
                expandButton,
                outConnector
        );

        return header;
    }

    // Метод для создания кнопки с информацией
    // Метод для создания кнопки с информацией
    private Button createInfoButton() {
        Button infoButton = new Button("?");
        infoButton.getStyleClass().add("info-button");
        infoButton.setMinSize(18, 18);
        infoButton.setMaxSize(18, 18);

        // Получаем подробное описание для этого типа узла
        String tooltipText = getNodeTypeDescription(node.getType());

        // Создаем всплывающую подсказку с бесконечным таймаутом
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(300);
        tooltip.getStyleClass().add("node-tooltip");

        // Устанавливаем бесконечное время показа tooltip
        tooltip.setShowDuration(Duration.INDEFINITE);

        Tooltip.install(infoButton, tooltip);

        return infoButton;
    }

    // Метод для создания кнопки разворачивания/сворачивания
    private Button createExpandButton() {
        Button expandButton = new Button();
        expandButton.getStyleClass().add("expand-button");
        expandButton.setMinSize(16, 16);
        expandButton.setMaxSize(16, 16);

        // Используем CSS классы для изменения внешнего вида в зависимости от состояния
        node.expandedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                expandButton.getStyleClass().remove("collapsed");
                expandButton.getStyleClass().add("expanded");
            } else {
                expandButton.getStyleClass().remove("expanded");
                expandButton.getStyleClass().add("collapsed");
            }
        });

        // Начальное состояние
        if (node.isExpanded()) {
            expandButton.getStyleClass().add("expanded");
            expandButton.setText("▼");
        } else {
            expandButton.getStyleClass().add("collapsed");
            expandButton.setText("►");
        }

        expandButton.setOnAction(e -> {
            toggleExpanded();
        });

        return expandButton;
    }

    // Метод для получения подробного описания типа узла
    private String getNodeTypeDescription(String nodeType) {
        switch (nodeType) {
            case "AttackNode":
                return "Узел атаки\n\n"
                        + "Настраивает параметры атаки моба: урон, дальность, угол атаки и анимацию.\n\n"
                        + "НОВОЕ: Поддерживает отслеживание костей для точных хитбоксов.\n"
                        + "Атака будет применена ко всем целям в заданном радиусе и угле. "
                        + "Можно настроить дополнительные эффекты и привязать особую анимацию.";

            case "PlayAnimationNode":
                return "Узел анимации\n\n"
                        + "Воспроизводит выбранную анимацию с заданными параметрами.\n\n"
                        + "Можно настроить скорость анимации и зацикливание. "
                        + "Анимация может воспроизводиться один раз или циклически.";

            case "TimerNode":
                return "Узел таймера\n\n"
                        + "Создает задержку перед активацией следующих узлов.\n\n"
                        + "Таймер может работать один раз или повторяться циклически. "
                        + "Полезен для создания периодических действий моба.";

            case "FollowNode":
                return "Узел следования\n\n"
                        + "Заставляет моба следовать за целью (обычно за игроком).\n\n"
                        + "Можно настроить скорость и дистанцию следования. "
                        + "Моб будет пытаться сохранять заданное расстояние до цели.";

            case "FleeNode":
                return "Узел отступления\n\n"
                        + "Заставляет моба убегать от цели (обычно от игрока).\n\n"
                        + "Работает, когда цель находится ближе заданного расстояния. "
                        + "Можно настроить скорость отступления.";

            case "SequenceNode":
                return "Узел последовательности\n\n"
                        + "Выполняет дочерние узлы по порядку до тех пор, пока один не завершится неудачей.\n\n"
                        + "Если все дочерние узлы выполнены успешно, последовательность считается успешной. "
                        + "Если хотя бы один дочерний узел завершается неудачей, вся последовательность считается неудачной.";

            case "SelectorNode":
                return "Узел выбора\n\n"
                        + "Выполняет дочерние узлы по порядку до первого успешного.\n\n"
                        + "Если хотя бы один дочерний узел выполнен успешно, узел селектора считается успешным. "
                        + "Если все дочерние узлы завершаются неудачей, селектор считается неудачным.";

            case "ParallelNode":
                return "Узел параллельного выполнения\n\n"
                        + "Выполняет все дочерние узлы одновременно.\n\n"
                        + "Узел считается успешным, если все дочерние узлы выполнены успешно. "
                        + "При включенной опции 'Прервать при ошибке' выполнение прекращается, если один из узлов завершился неудачей.";

            // НОВЫЕ УЗЛЫ
            case "PrioritySelectorNode":
                return "Приоритетный селектор\n\n"
                        + "Выполняет узлы с учетом приоритетов. События всегда имеют приоритет над обычным поведением.\n\n"
                        + "Используется как корневой узел для деревьев с событиями (OnSpawn, OnDamage). "
                        + "Событийные узлы автоматически получают высший приоритет.";

            case "TargetPlayerNode":
                return "Поиск игрока-цели\n\n"
                        + "Ищет ближайшего игрока и устанавливает его как цель для моба.\n\n"
                        + "Можно настроить радиус поиска, время запоминания цели и требование прямой видимости. "
                        + "Используется в начале боевой ветки для поиска противников.";

            case "HasTargetNode":
                return "Проверка наличия цели\n\n"
                        + "Проверяет, есть ли у моба живая цель в допустимом радиусе.\n\n"
                        + "Можно настроить максимальное расстояние, проверку видимости и автоочистку недействительных целей. "
                        + "Используется как условие для атак и следования.";

            case "HealthCheckNode":
                return "Проверка здоровья\n\n"
                        + "Проверяет процент здоровья моба или цели для фазовых переходов.\n\n"
                        + "Можно настроить тип сравнения (меньше/больше/равно) и цель проверки (себя/врага). "
                        + "Полезен для фазовых переходов боссов (например, вторая фаза при 50% HP).";

            case "OnSpawnNode":
                return "Узел события появления\n\n"
                        + "Активируется при появлении моба в мире.\n\n"
                        + "Может иметь необязательную задержку перед выполнением. "
                        + "Полезен для инициализации моба и запуска начальных действий.";

            case "OnDeathNode":
                return "Узел события смерти\n\n"
                        + "Активируется при смерти моба.\n\n"
                        + "Можно настроить дополнительные эффекты, например выпадение предметов. "
                        + "Полезен для создания эффектов при смерти и специальных наград игроку.";

            case "OnDamageNode":
                return "Узел события получения урона\n\n"
                        + "Активируется, когда моб получает урон.\n\n"
                        + "Можно настроить минимальный порог урона для активации. "
                        + "Может реагировать только на урон от игрока или на любой урон.";

            case "SpawnParticleNode":
                return "Узел создания частиц\n\n"
                        + "Создает эффект частиц вокруг моба.\n\n"
                        + "Можно настроить тип частиц, радиус распространения, количество и длительность эффекта. "
                        + "Полезен для визуальных эффектов и индикации состояния моба.";

            case "DisplayTitleNode":
                return "Узел отображения текста\n\n"
                        + "Отображает текст игроку (заголовок и подзаголовок).\n\n"
                        + "Можно настроить текст, цвет и длительность отображения. "
                        + "Полезен для информирования игрока о действиях моба или специальных событиях.";

            case "PlaySoundNode":
                return "Узел воспроизведения звука\n\n"
                        + "Воспроизводит звук в позиции моба.\n\n"
                        + "Можно настроить идентификатор звука, громкость, высоту звука и радиус слышимости. "
                        + "Полезен для звуковых эффектов и озвучивания действий моба.";

            case "ScriptNode":
                return "Узел скриптов\n\n"
                        + "Выполняет пользовательский скрипт для специфического поведения.\n\n"
                        + "Поддерживает JavaScript, Python и Lua. "
                        + "Предоставляет максимальную гибкость для программирования сложного поведения моба.";

            case "WeightedSelectorNode":
                return "Узел взвешенного выбора\n\n"
                        + "Случайно выбирает один из дочерних узлов на основе весов.\n\n"
                        + "Для каждого дочернего узла можно задать вес (вероятность выбора). "
                        + "Чем выше вес узла, тем больше вероятность его выбора при выполнении.";

            default:
                return "Узел " + nodeType + "\n\n"
                        + "Информация о данном типе узла отсутствует.";
        }
    }

    // Переключение расширенного/свернутого состояния с анимацией
    private void toggleExpanded() {
        // Сначала отключаем обновления соединений на время изменения состояния
        for (ConnectionView conn : inputConnections) {
            conn.setBeingEdited(true);
        }
        for (ConnectionView conn : outputConnections) {
            conn.setBeingEdited(true);
        }

        // Изменяем состояние
        boolean newState = !node.isExpanded();
        node.setExpanded(newState);

        // Немедленное обновление соединений
        updateAllConnectionsImmediately();

        // Разрешаем обновление соединений после изменения состояния
        for (ConnectionView conn : inputConnections) {
            conn.setBeingEdited(false);
        }
        for (ConnectionView conn : outputConnections) {
            conn.setBeingEdited(false);
        }

        // Анимация
        if (newState) {
            // Разворачивание - показываем контент с анимацией
            contentContainer.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(100), contentContainer);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.setOnFinished(e -> {
                // После завершения анимации обновляем соединения
                Platform.runLater(() -> {
                    // Блокируем обновления на время пересчета
                    for (ConnectionView conn : inputConnections) {
                        conn.setBeingEdited(true);
                    }
                    for (ConnectionView conn : outputConnections) {
                        conn.setBeingEdited(true);
                    }

                    // Выполняем обновление
                    updateAllConnectionsImmediately();

                    // Разрешаем обновления после пересчета
                    for (ConnectionView conn : inputConnections) {
                        conn.setBeingEdited(false);
                    }
                    for (ConnectionView conn : outputConnections) {
                        conn.setBeingEdited(false);
                    }
                });
            });
            ft.play();
        } else {
            // Сворачивание - скрываем контент с анимацией
            FadeTransition ft = new FadeTransition(Duration.millis(100), contentContainer);
            ft.setFromValue(1);
            ft.setToValue(0);
            ft.setOnFinished(e -> {
                // После завершения анимации обновляем соединения
                Platform.runLater(() -> {
                    // Блокируем обновления на время пересчета
                    for (ConnectionView conn : inputConnections) {
                        conn.setBeingEdited(true);
                    }
                    for (ConnectionView conn : outputConnections) {
                        conn.setBeingEdited(true);
                    }

                    // Выполняем обновление
                    updateAllConnectionsImmediately();

                    // Разрешаем обновления после пересчета
                    for (ConnectionView conn : inputConnections) {
                        conn.setBeingEdited(false);
                    }
                    for (ConnectionView conn : outputConnections) {
                        conn.setBeingEdited(false);
                    }
                });
            });
            ft.play();
        }
    }

    // Немедленное обновление всех соединений
    private void updateAllConnectionsImmediately() {
        // Обновляем все входящие соединения
        for (ConnectionView conn : inputConnections) {
            conn.updatePathImmediately();
        }

        // Обновляем все исходящие соединения
        for (ConnectionView conn : outputConnections) {
            conn.updatePathImmediately();
        }
    }

    private void setupPinInteractions(StackPane connector, Circle circle, String type) {
        // Визуальное выделение при наведении
        connector.setOnMouseEntered(e -> {
            if (type.equals("in")) {
                if (inputConnections.isEmpty()) {
                    circle.getStyleClass().add(Constants.PIN_HIGHLIGHTED_STYLE_CLASS);
                }
            } else {
                circle.getStyleClass().add(Constants.PIN_HIGHLIGHTED_STYLE_CLASS);
            }
        });

        connector.setOnMouseExited(e -> {
            circle.getStyleClass().remove(Constants.PIN_HIGHLIGHTED_STYLE_CLASS);
            circle.getStyleClass().remove("pin-error");

            // Восстанавливаем класс "connected" если порт подключен
            if (type.equals("in") && !inputConnections.isEmpty()) {
                if (!circle.getStyleClass().contains(Constants.PIN_CONNECTED_STYLE_CLASS)) {
                    circle.getStyleClass().add(Constants.PIN_CONNECTED_STYLE_CLASS);
                }
            } else if (type.equals("out") && !outputConnections.isEmpty()) {
                if (!circle.getStyleClass().contains(Constants.PIN_CONNECTED_STYLE_CLASS)) {
                    circle.getStyleClass().add(Constants.PIN_CONNECTED_STYLE_CLASS);
                }
            }
        });

        // Начало соединения - ТОЛЬКО для выходного пина
        connector.setOnMousePressed(e -> {
            if (e.isShiftDown()) {
                if ("out".equals(type)) {
                    System.out.println("Output pin pressed, starting connection from: " + circle);
                    if (onConnectionStarted != null) {
                        onConnectionStarted.accept(this, "out"); // Явно передаем "out"
                    }
                    e.consume();
                } else {
                    System.out.println("Input pin pressed, not starting connection");
                    circle.getStyleClass().add("pin-error");
                    showNotification("Connections must start from output pins");
                    e.consume();
                }
            }
        });

        // Обработчик удаления соединений по правому клику на пин
        circle.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                if (type.equals("in") && !inputConnections.isEmpty()) {
                    // Удаляем все входящие соединения
                    new ArrayList<>(inputConnections).forEach(conn -> {
                        if (conn.getOnConnectionDeleted() != null) {
                            conn.getOnConnectionDeleted().run();
                        }
                    });
                    e.consume();
                } else if (type.equals("out") && !outputConnections.isEmpty()) {
                    // Удаляем все исходящие соединения
                    new ArrayList<>(outputConnections).forEach(conn -> {
                        if (conn.getOnConnectionDeleted() != null) {
                            conn.getOnConnectionDeleted().run();
                        }
                    });
                    e.consume();
                }
            }
        });
    }

    // Заполнение содержимого в зависимости от типа узла
    private void populateContent() {
        contentContainer.getChildren().clear();

        switch (node.getType()) {
            case "AttackNode":
                addAttackNodeControls();
                break;
            case "PlayAnimationNode":
                addPlayAnimationNodeControls();
                break;
            case "TimerNode":
                addTimerNodeControls();
                break;
            case "FollowNode":
            case "FleeNode":
                addMovementNodeControls();
                break;
            case "OnSpawnNode":
            case "OnDeathNode":
            case "OnDamageNode":
                addEventNodeControls();
                break;
            case "SpawnParticleNode":
                addParticleNodeControls();
                break;
            case "DisplayTitleNode":
                addDisplayTitleNodeControls();
                break;
            case "PlaySoundNode":
                addSoundNodeControls();
                break;
            case "ScriptNode":
                addScriptNodeControls();
                break;
            case "SequenceNode":
            case "SelectorNode":
            case "ParallelNode":
            case "PrioritySelectorNode": // НОВЫЙ
                addCompositeNodeControls();
                break;
            case "WeightedSelectorNode":
                addWeightedSelectorNodeControls();
                break;
            // НОВЫЕ УЗЛЫ
            case "TargetPlayerNode":
                addTargetPlayerNodeControls();
                break;
            case "HasTargetNode":
                addHasTargetNodeControls();
                break;
            case "HealthCheckNode":
                addHealthCheckNodeControls();
                break;
            default:
                addDefaultNodeControls();
                break;
        }
    }

// Добавить в класс NodeView.java

    /**
     * Метод для добавления контролов анимации в контейнер
     */
    // Добавить в класс NodeView.java
    /**
     * Метод для добавления контролов анимации в контейнер
     */
    private void addAnimationControls(VBox container) {
        // Показываем элементы управления анимацией только для нужных типов узлов
        if (!isAnimatableNode(node.getType())) {
            return;
        }

        // Заголовок раздела анимации
        Label animationHeader = new Label("Animation Settings:");
        animationHeader.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        animationHeader.setFont(Font.font("System", FontWeight.BOLD, 12));

        // Выбор анимации
        Label animationLabel = new Label("Animation:");
        animationLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        ComboBox<String> animationSelector = new ComboBox<>();
        animationSelector.getItems().addAll(availableAnimations);
        animationSelector.setValue(node.getAnimationId().isEmpty() ? null : node.getAnimationId());
        animationSelector.setPromptText("Select Animation");
        animationSelector.getStyleClass().add(Constants.INPUT_FIELD_STYLE_CLASS);
        animationSelector.setPrefWidth(150);

        animationSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                node.setAnimationId(newVal);
            }
        });

        // Зацикливание анимации
        Label loopLabel = new Label("Loop Animation:");
        loopLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        CheckBox loopCheckbox = new CheckBox();
        loopCheckbox.setSelected(node.isLoopAnimation());
        loopCheckbox.getStyleClass().add("custom-checkbox");

        loopCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            node.setLoopAnimation(newVal);
        });

        HBox loopBox = new HBox(5, loopCheckbox);
        loopBox.setAlignment(Pos.CENTER_LEFT);

        // Скорость анимации
        Label speedLabel = new Label("Playback Speed:");
        speedLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider speedSlider = createParameterSlider(0.1, 3, node.getAnimationSpeed(), 0.1);
        Label speedValueLabel = new Label(String.format("%.1fx", node.getAnimationSpeed()));
        speedValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        speedValueLabel.setMinWidth(40);

        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            node.setAnimationSpeed(value);
            speedValueLabel.setText(String.format("%.1fx", value));
        });

        HBox speedBox = new HBox(5, speedSlider, speedValueLabel);
        speedBox.setAlignment(Pos.CENTER_LEFT);

        // Добавляем все элементы в контейнер
        container.getChildren().addAll(
                animationHeader,
                animationLabel, animationSelector,
                loopLabel, loopBox,
                speedLabel, speedBox
        );
    }

    /**
     * Проверка, является ли тип узла анимируемым
     */
    private boolean isAnimatableNode(String nodeType) {
        return nodeType.equals("AttackNode") ||
                nodeType.equals("PlayAnimationNode") ||
                nodeType.equals("OnSpawnNode") ||
                nodeType.equals("OnDeathNode") ||
                nodeType.equals("OnDamageNode") ||
                nodeType.equals("SpawnParticleNode") ||
                nodeType.equals("DisplayTitleNode") ||
                nodeType.equals("PlaySoundNode");
    }


    private void addDefaultNodeControls() {
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        Label paramLabel = new Label("Parameter:");
        paramLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        paramLabel.setPadding(new Insets(5, 0, 0, 0));

        TextField paramField = new TextField(node.getParameter());
        paramField.setPromptText("Parameter");
        paramField.getStyleClass().add(Constants.PARAMETER_FIELD_STYLE_CLASS);
        paramField.textProperty().addListener((obs, old, newVal) -> node.setParameter(newVal));

        contentContainer.getChildren().addAll(descLabel, descValueLabel, paramLabel, paramField);
    }

    private void addTargetPlayerNodeControls() {
        double range = getDoubleParam("range", 16.0);
        double rememberTime = getDoubleParam("remember_time", 30000.0);
        boolean lineOfSight = getBooleanParam("line_of_sight", false);

        // Добавляем основное описание
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        // Радиус поиска
        Label rangeLabel = new Label("Search Range:");
        rangeLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider rangeSlider = createParameterSlider(1, 64, range, 1);
        Label rangeValueLabel = new Label(String.format("%.0f блоков", range));
        rangeValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        rangeValueLabel.setMinWidth(80);

        rangeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("range", value);
            rangeValueLabel.setText(String.format("%.0f блоков", value));
            saveNodeParameters();
        });

        HBox rangeBox = new HBox(5, rangeSlider, rangeValueLabel);
        rangeBox.setAlignment(Pos.CENTER_LEFT);

        // Время запоминания цели
        Label rememberLabel = new Label("Remember Time (ms):");
        rememberLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider rememberSlider = createParameterSlider(1000, 120000, rememberTime, 1000);
        Label rememberValueLabel = new Label(String.format("%.0f мс", rememberTime));
        rememberValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        rememberValueLabel.setMinWidth(80);

        rememberSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("remember_time", value);
            rememberValueLabel.setText(String.format("%.0f мс", value));
            saveNodeParameters();
        });

        HBox rememberBox = new HBox(5, rememberSlider, rememberValueLabel);
        rememberBox.setAlignment(Pos.CENTER_LEFT);

        // Требовать прямую видимость
        Label lineOfSightLabel = new Label("Require Line of Sight:");
        lineOfSightLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        CheckBox lineOfSightCheckbox = new CheckBox();
        lineOfSightCheckbox.setSelected(lineOfSight);
        lineOfSightCheckbox.getStyleClass().add("custom-checkbox");

        lineOfSightCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("line_of_sight", newVal);
            saveNodeParameters();
        });

        HBox lineOfSightBox = new HBox(5, lineOfSightCheckbox);
        lineOfSightBox.setAlignment(Pos.CENTER_LEFT);

        // Добавляем все элементы в контейнер
        contentContainer.getChildren().addAll(
                descLabel, descValueLabel,
                rangeLabel, rangeBox,
                rememberLabel, rememberBox,
                lineOfSightLabel, lineOfSightBox
        );
    }

    private void addHasTargetNodeControls() {
        double maxDistance = getDoubleParam("max_distance", 20.0);
        boolean lineOfSight = getBooleanParam("line_of_sight", false);
        boolean clearIfInvalid = getBooleanParam("clear_if_invalid", true);

        // Добавляем основное описание
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        // Максимальное расстояние до цели
        Label maxDistanceLabel = new Label("Max Distance:");
        maxDistanceLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider maxDistanceSlider = createParameterSlider(1, 64, maxDistance, 1);
        Label maxDistanceValueLabel = new Label(String.format("%.0f блоков", maxDistance));
        maxDistanceValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        maxDistanceValueLabel.setMinWidth(80);

        maxDistanceSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("max_distance", value);
            maxDistanceValueLabel.setText(String.format("%.0f блоков", value));
            saveNodeParameters();
        });

        HBox maxDistanceBox = new HBox(5, maxDistanceSlider, maxDistanceValueLabel);
        maxDistanceBox.setAlignment(Pos.CENTER_LEFT);

        // Проверять прямую видимость
        Label lineOfSightLabel = new Label("Check Line of Sight:");
        lineOfSightLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        CheckBox lineOfSightCheckbox = new CheckBox();
        lineOfSightCheckbox.setSelected(lineOfSight);
        lineOfSightCheckbox.getStyleClass().add("custom-checkbox");

        lineOfSightCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("line_of_sight", newVal);
            saveNodeParameters();
        });

        HBox lineOfSightBox = new HBox(5, lineOfSightCheckbox);
        lineOfSightBox.setAlignment(Pos.CENTER_LEFT);

        // Очищать цель если она невалидна
        Label clearIfInvalidLabel = new Label("Clear Invalid Target:");
        clearIfInvalidLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        CheckBox clearIfInvalidCheckbox = new CheckBox();
        clearIfInvalidCheckbox.setSelected(clearIfInvalid);
        clearIfInvalidCheckbox.getStyleClass().add("custom-checkbox");

        clearIfInvalidCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("clear_if_invalid", newVal);
            saveNodeParameters();
        });

        HBox clearIfInvalidBox = new HBox(5, clearIfInvalidCheckbox);
        clearIfInvalidBox.setAlignment(Pos.CENTER_LEFT);

        // Добавляем все элементы в контейнер
        contentContainer.getChildren().addAll(
                descLabel, descValueLabel,
                maxDistanceLabel, maxDistanceBox,
                lineOfSightLabel, lineOfSightBox,
                clearIfInvalidLabel, clearIfInvalidBox
        );
    }

    private void addHealthCheckNodeControls() {
        double healthPercent = getDoubleParam("health_percent", 50.0);
        String comparison = getStringParam("comparison", "less_than");
        String target = getStringParam("target", "self");

        // Добавляем основное описание
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        // Процент здоровья
        Label healthPercentLabel = new Label("Health Percent:");
        healthPercentLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider healthPercentSlider = createParameterSlider(0, 100, healthPercent, 5);
        Label healthPercentValueLabel = new Label(String.format("%.0f%%", healthPercent));
        healthPercentValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        healthPercentValueLabel.setMinWidth(50);

        healthPercentSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("health_percent", value);
            healthPercentValueLabel.setText(String.format("%.0f%%", value));
            saveNodeParameters();
        });

        HBox healthPercentBox = new HBox(5, healthPercentSlider, healthPercentValueLabel);
        healthPercentBox.setAlignment(Pos.CENTER_LEFT);

        // Тип сравнения
        Label comparisonLabel = new Label("Comparison Type:");
        comparisonLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        ComboBox<String> comparisonSelector = new ComboBox<>();
        comparisonSelector.getItems().addAll("less_than", "greater_than", "equals");
        comparisonSelector.setValue(comparison);
        comparisonSelector.setPromptText("Select Comparison");
        comparisonSelector.getStyleClass().add(Constants.INPUT_FIELD_STYLE_CLASS);
        comparisonSelector.setPrefWidth(150);

        // Русские названия для отображения
        comparisonSelector.setConverter(new javafx.util.StringConverter<String>() {
            @Override
            public String toString(String object) {
                switch (object) {
                    case "less_than": return "Меньше чем";
                    case "greater_than": return "Больше чем";
                    case "equals": return "Равно";
                    default: return object;
                }
            }

            @Override
            public String fromString(String string) {
                switch (string) {
                    case "Меньше чем": return "less_than";
                    case "Больше чем": return "greater_than";
                    case "Равно": return "equals";
                    default: return string;
                }
            }
        });

        comparisonSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                dynamicParams.put("comparison", newVal);
                saveNodeParameters();
            }
        });

        // Цель проверки
        Label targetLabel = new Label("Check Target:");
        targetLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        ComboBox<String> targetSelector = new ComboBox<>();
        targetSelector.getItems().addAll("self", "target");
        targetSelector.setValue(target);
        targetSelector.setPromptText("Select Target");
        targetSelector.getStyleClass().add(Constants.INPUT_FIELD_STYLE_CLASS);
        targetSelector.setPrefWidth(150);

        // Русские названия для отображения
        targetSelector.setConverter(new javafx.util.StringConverter<String>() {
            @Override
            public String toString(String object) {
                switch (object) {
                    case "self": return "Себя";
                    case "target": return "Цель";
                    default: return object;
                }
            }

            @Override
            public String fromString(String string) {
                switch (string) {
                    case "Себя": return "self";
                    case "Цель": return "target";
                    default: return string;
                }
            }
        });

        targetSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                dynamicParams.put("target", newVal);
                saveNodeParameters();
            }
        });

        // Добавляем все элементы в контейнер
        contentContainer.getChildren().addAll(
                descLabel, descValueLabel,
                healthPercentLabel, healthPercentBox,
                comparisonLabel, comparisonSelector,
                targetLabel, targetSelector
        );
    }

    private void addAttackNodeControls() {
        // Получаем значения параметров из динамического хранилища или устанавливаем значения по умолчанию
        double damage = getDoubleParam("damage", 10.0);
        double range = getDoubleParam("range", 2.0);
        double angle = getDoubleParam("angle", 60.0);
        double cooldown = getDoubleParam("cooldown", 1000.0);

        // Новые параметры Bone Tracking
        boolean useBoneTracking = getBooleanParam("use_bone_tracking", false);
        String boneName = getStringParam("bone_name", "rightArm");
        double boneRadius = getDoubleParam("bone_radius", 0.5);
        double minVelocity = getDoubleParam("min_velocity", 0.5);

        // Добавляем основное описание
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        // Урон
        Label damageLabel = new Label("Damage:");
        damageLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider damageSlider = createParameterSlider(0, 50, damage, 1);
        Label damageValueLabel = new Label(String.valueOf((int)damage));
        damageValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        damageValueLabel.setMinWidth(30);

        damageSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int value = newVal.intValue();
            dynamicParams.put("damage", value);
            damageValueLabel.setText(String.valueOf(value));
            saveNodeParameters();
        });

        HBox damageBox = new HBox(5, damageSlider, damageValueLabel);
        damageBox.setAlignment(Pos.CENTER_LEFT);

        // Дальность атаки (для традиционного режима)
        Label rangeLabel = new Label("Attack Range:");
        rangeLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider rangeSlider = createParameterSlider(0, 10, range, 0.5);
        Label rangeValueLabel = new Label(String.format("%.1f", range));
        rangeValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        rangeValueLabel.setMinWidth(30);

        rangeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("range", value);
            rangeValueLabel.setText(String.format("%.1f", value));
            saveNodeParameters();
        });

        HBox rangeBox = new HBox(5, rangeSlider, rangeValueLabel);
        rangeBox.setAlignment(Pos.CENTER_LEFT);

        // Угол атаки
        Label angleLabel = new Label("Attack Angle:");
        angleLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider angleSlider = createParameterSlider(0, 360, angle, 5);
        Label angleValueLabel = new Label(String.format("%.0f°", angle));
        angleValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        angleValueLabel.setMinWidth(40);

        angleSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("angle", value);
            angleValueLabel.setText(String.format("%.0f°", value));
            saveNodeParameters();
        });

        HBox angleBox = new HBox(5, angleSlider, angleValueLabel);
        angleBox.setAlignment(Pos.CENTER_LEFT);

        // Кулдаун атаки
        Label cooldownLabel = new Label("Cooldown (ms):");
        cooldownLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider cooldownSlider = createParameterSlider(100, 5000, cooldown, 100);
        Label cooldownValueLabel = new Label(String.format("%.0f мс", cooldown));
        cooldownValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        cooldownValueLabel.setMinWidth(60);

        cooldownSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("cooldown", value);
            cooldownValueLabel.setText(String.format("%.0f мс", value));
            saveNodeParameters();
        });

        HBox cooldownBox = new HBox(5, cooldownSlider, cooldownValueLabel);
        cooldownBox.setAlignment(Pos.CENTER_LEFT);

        // СЕКЦИЯ BONE TRACKING
        Label boneTrackingHeader = new Label("Bone Tracking Settings:");
        boneTrackingHeader.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        boneTrackingHeader.setFont(javafx.scene.text.Font.font("System", javafx.scene.text.FontWeight.BOLD, 12));

        // Включить отслеживание костей
        Label useBoneTrackingLabel = new Label("Use Bone Tracking:");
        useBoneTrackingLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        CheckBox useBoneTrackingCheckbox = new CheckBox();
        useBoneTrackingCheckbox.setSelected(useBoneTracking);
        useBoneTrackingCheckbox.getStyleClass().add("custom-checkbox");

        HBox useBoneTrackingBox = new HBox(5, useBoneTrackingCheckbox);
        useBoneTrackingBox.setAlignment(Pos.CENTER_LEFT);

        // Имена костей
        Label boneNameLabel = new Label("Bone Names:");
        boneNameLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        TextField boneNameField = new TextField(boneName);
        boneNameField.setPromptText("rightArm,leftArm,sword*");
        boneNameField.getStyleClass().add(Constants.PARAMETER_FIELD_STYLE_CLASS);

        Label boneNameHelpLabel = new Label("Примеры: rightArm, sword*, group:weapons");
        boneNameHelpLabel.getStyleClass().add("help-label");
        boneNameHelpLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #AAAAAA;");

        // Радиус кости
        Label boneRadiusLabel = new Label("Bone Radius:");
        boneRadiusLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider boneRadiusSlider = createParameterSlider(0.1, 2.0, boneRadius, 0.1);
        Label boneRadiusValueLabel = new Label(String.format("%.1f", boneRadius));
        boneRadiusValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        boneRadiusValueLabel.setMinWidth(30);

        HBox boneRadiusBox = new HBox(5, boneRadiusSlider, boneRadiusValueLabel);
        boneRadiusBox.setAlignment(Pos.CENTER_LEFT);

        // Минимальная скорость
        Label minVelocityLabel = new Label("Min Velocity:");
        minVelocityLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider minVelocitySlider = createParameterSlider(0.0, 5.0, minVelocity, 0.1);
        Label minVelocityValueLabel = new Label(String.format("%.1f", minVelocity));
        minVelocityValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        minVelocityValueLabel.setMinWidth(30);

        HBox minVelocityBox = new HBox(5, minVelocitySlider, minVelocityValueLabel);
        minVelocityBox.setAlignment(Pos.CENTER_LEFT);

        // Контейнер для настроек bone tracking
        VBox boneTrackingContainer = new VBox(5);
        boneTrackingContainer.getChildren().addAll(
                boneNameLabel, boneNameField, boneNameHelpLabel,
                boneRadiusLabel, boneRadiusBox,
                minVelocityLabel, minVelocityBox
        );

        // Обработчики событий
        useBoneTrackingCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("use_bone_tracking", newVal);
            boneTrackingContainer.setDisable(!newVal);
            rangeSlider.setDisable(newVal); // Отключаем традиционную дальность при bone tracking
            angleSlider.setDisable(newVal); // Отключаем угол при bone tracking
            saveNodeParameters();
        });

        boneNameField.textProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("bone_name", newVal);
            saveNodeParameters();
        });

        boneRadiusSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("bone_radius", value);
            boneRadiusValueLabel.setText(String.format("%.1f", value));
            saveNodeParameters();
        });

        minVelocitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("min_velocity", value);
            minVelocityValueLabel.setText(String.format("%.1f", value));
            saveNodeParameters();
        });

        // Начальное состояние
        boneTrackingContainer.setDisable(!useBoneTracking);
        rangeSlider.setDisable(useBoneTracking);
        angleSlider.setDisable(useBoneTracking);

        // Добавляем все элементы в контейнер
        contentContainer.getChildren().addAll(
                descLabel, descValueLabel,
                damageLabel, damageBox,
                rangeLabel, rangeBox,
                angleLabel, angleBox,
                cooldownLabel, cooldownBox,
                new Separator(),
                boneTrackingHeader,
                useBoneTrackingLabel, useBoneTrackingBox,
                boneTrackingContainer
        );

        // Добавляем раздел настроек анимации
        addAnimationControls(contentContainer);
    }

    private void addPlayAnimationNodeControls() {
        String animation = getStringParam("animation", "idle");
        double speed = getDoubleParam("speed", 1.0);
        boolean loop = getBooleanParam("loop", true);

        // Добавляем основное описание
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        // Выбор анимации
        Label animationLabel = new Label("Animation:");
        animationLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        ComboBox<String> animationSelector = new ComboBox<>();
        animationSelector.getItems().addAll(availableAnimations);
        animationSelector.setValue(animation);
        animationSelector.setPromptText("Select Animation");
        animationSelector.getStyleClass().add(Constants.INPUT_FIELD_STYLE_CLASS);
        animationSelector.setPrefWidth(150);

        animationSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                dynamicParams.put("animation", newVal);
                saveNodeParameters();
            }
        });

        // Скорость анимации
        Label speedLabel = new Label("Playback Speed:");
        speedLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider speedSlider = createParameterSlider(0.1, 3, speed, 0.1);
        Label speedValueLabel = new Label(String.format("%.1fx", speed));
        speedValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        speedValueLabel.setMinWidth(40);

        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("speed", value);
            speedValueLabel.setText(String.format("%.1fx", value));
            saveNodeParameters();
        });

        HBox speedBox = new HBox(5, speedSlider, speedValueLabel);
        speedBox.setAlignment(Pos.CENTER_LEFT);

        // Зацикливание анимации
        Label loopLabel = new Label("Loop Animation:");
        loopLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        CheckBox loopCheckbox = new CheckBox();
        loopCheckbox.setSelected(loop);
        loopCheckbox.getStyleClass().add("custom-checkbox");

        loopCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("loop", newVal);
            saveNodeParameters();
        });

        HBox loopBox = new HBox(5, loopCheckbox);
        loopBox.setAlignment(Pos.CENTER_LEFT);

        // Добавляем все элементы в контейнер
        contentContainer.getChildren().addAll(
                descLabel, descValueLabel,
                animationLabel, animationSelector,
                speedLabel, speedBox,
                loopLabel, loopBox
        );
    }

    private void addTimerNodeControls() {
        double duration = getDoubleParam("duration", 1.0);
        boolean autoRepeat = getBooleanParam("repeat", false);

        // Добавляем основное описание
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        // Длительность таймера
        Label durationLabel = new Label("Duration (seconds):");
        durationLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider durationSlider = createParameterSlider(0, 30, duration, 0.1);
        Label durationValueLabel = new Label(String.format("%.1f s", duration));
        durationValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        durationValueLabel.setMinWidth(40);

        durationSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("duration", value);
            durationValueLabel.setText(String.format("%.1f s", value));
            saveNodeParameters();
        });

        HBox durationBox = new HBox(5, durationSlider, durationValueLabel);
        durationBox.setAlignment(Pos.CENTER_LEFT);

        // Автоповтор таймера
        Label repeatLabel = new Label("Auto Repeat:");
        repeatLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        CheckBox repeatCheckbox = new CheckBox();
        repeatCheckbox.setSelected(autoRepeat);
        repeatCheckbox.getStyleClass().add("custom-checkbox");

        repeatCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("repeat", newVal);
            saveNodeParameters();
        });

        HBox repeatBox = new HBox(5, repeatCheckbox);
        repeatBox.setAlignment(Pos.CENTER_LEFT);

        // Добавляем все элементы в контейнер
        contentContainer.getChildren().addAll(
                descLabel, descValueLabel,
                durationLabel, durationBox,
                repeatLabel, repeatBox
        );
    }

    private void addMovementNodeControls() {
        double distance = getDoubleParam("distance", 5.0);
        double speed = getDoubleParam("speed", 1.0);
        boolean targetPlayer = getBooleanParam("targetPlayer", true);

        // Добавляем основное описание
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        // Настройка расстояния
        Label distanceLabel = new Label("Distance:");
        distanceLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider distanceSlider = createParameterSlider(0, 20, distance, 0.5);
        Label distanceValueLabel = new Label(String.format("%.1f", distance));
        distanceValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        distanceValueLabel.setMinWidth(30);

        distanceSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("distance", value);
            distanceValueLabel.setText(String.format("%.1f", value));
            saveNodeParameters();
        });

        HBox distanceBox = new HBox(5, distanceSlider, distanceValueLabel);
        distanceBox.setAlignment(Pos.CENTER_LEFT);

        // Настройка скорости
        Label speedLabel = new Label("Movement Speed:");
        speedLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider speedSlider = createParameterSlider(0.1, 5, speed, 0.1);
        Label speedValueLabel = new Label(String.format("%.1f", speed));
        speedValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        speedValueLabel.setMinWidth(30);

        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("speed", value);
            speedValueLabel.setText(String.format("%.1f", value));
            saveNodeParameters();
        });

        HBox speedBox = new HBox(5, speedSlider, speedValueLabel);
        speedBox.setAlignment(Pos.CENTER_LEFT);

        // Настройка цели
        Label targetLabel = new Label("Target Player:");
        targetLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        CheckBox targetCheckbox = new CheckBox();
        targetCheckbox.setSelected(targetPlayer);
        targetCheckbox.getStyleClass().add("custom-checkbox");

        targetCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("targetPlayer", newVal);
            saveNodeParameters();
        });

        HBox targetBox = new HBox(5, targetCheckbox);
        targetBox.setAlignment(Pos.CENTER_LEFT);

        // Добавляем все элементы в контейнер
        if (node.getType().equals("FollowNode")) {
            contentContainer.getChildren().addAll(
                    descLabel, descValueLabel,
                    targetLabel, targetBox,
                    distanceLabel, distanceBox,
                    speedLabel, speedBox
            );
        } else { // FleeNode
            contentContainer.getChildren().addAll(
                    descLabel, descValueLabel,
                    targetLabel, targetBox,
                    distanceLabel, distanceBox,
                    speedLabel, speedBox
            );
        }
    }

    private void addEventNodeControls() {
        // Общее для всех событийных нод
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        String nodeType = node.getType();

        switch (nodeType) {
            case "OnSpawnNode":
                addOnSpawnSpecificControls(descLabel, descValueLabel);
                break;
            case "OnDeathNode":
                addOnDeathSpecificControls(descLabel, descValueLabel);
                break;
            case "OnDamageNode":
                addOnDamageSpecificControls(descLabel, descValueLabel);
                break;
            default:
                contentContainer.getChildren().addAll(descLabel, descValueLabel);
                break;
        }
    }

    private void addOnSpawnSpecificControls(Label descLabel, Label descValueLabel) {
        boolean delayEnabled = getBooleanParam("delayEnabled", false);
        double delay = getDoubleParam("delay", 0.0);

        // Опция отложенного выполнения
        Label delayEnabledLabel = new Label("Delayed Activation:");
        delayEnabledLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        CheckBox delayEnabledCheckbox = new CheckBox();
        delayEnabledCheckbox.setSelected(delayEnabled);
        delayEnabledCheckbox.getStyleClass().add("custom-checkbox");

        HBox delayEnabledBox = new HBox(5, delayEnabledCheckbox);
        delayEnabledBox.setAlignment(Pos.CENTER_LEFT);

        // Настройка задержки
        Label delayLabel = new Label("Delay (seconds):");
        delayLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider delaySlider = createParameterSlider(0, 10, delay, 0.1);
        Label delayValueLabel = new Label(String.format("%.1f s", delay));
        delayValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        delayValueLabel.setMinWidth(40);

        // Обработчик изменений для чекбокса
        delayEnabledCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("delayEnabled", newVal);
            delaySlider.setDisable(!newVal);
            saveNodeParameters();
        });

        // Обработчик изменений для слайдера
        delaySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("delay", value);
            delayValueLabel.setText(String.format("%.1f s", value));
            saveNodeParameters();
        });

        HBox delayBox = new HBox(5, delaySlider, delayValueLabel);
        delayBox.setAlignment(Pos.CENTER_LEFT);

        // Начальное состояние слайдера задержки зависит от чекбокса
        delaySlider.setDisable(!delayEnabled);

        contentContainer.getChildren().addAll(
                descLabel, descValueLabel,
                delayEnabledLabel, delayEnabledBox,
                delayLabel, delayBox
        );
    }

    private void addOnDeathSpecificControls(Label descLabel, Label descValueLabel) {
        boolean dropItemsEnabled = getBooleanParam("dropItemsEnabled", false);
        boolean showDeathMessage = getBooleanParam("showDeathMessage", false);

        // Опция выпадения предметов
        Label dropItemsLabel = new Label("Drop Items:");
        dropItemsLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        CheckBox dropItemsCheckbox = new CheckBox();
        dropItemsCheckbox.setSelected(dropItemsEnabled);
        dropItemsCheckbox.getStyleClass().add("custom-checkbox");

        HBox dropItemsBox = new HBox(5, dropItemsCheckbox);
        dropItemsBox.setAlignment(Pos.CENTER_LEFT);

        // Опция отображения сообщения о смерти
        Label deathMessageLabel = new Label("Show Death Message:");
        deathMessageLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        CheckBox deathMessageCheckbox = new CheckBox();
        deathMessageCheckbox.setSelected(showDeathMessage);
        deathMessageCheckbox.getStyleClass().add("custom-checkbox");

        HBox deathMessageBox = new HBox(5, deathMessageCheckbox);
        deathMessageBox.setAlignment(Pos.CENTER_LEFT);

        // Обработчики изменений
        dropItemsCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("dropItemsEnabled", newVal);
            saveNodeParameters();
        });

        deathMessageCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("showDeathMessage", newVal);
            saveNodeParameters();
        });

        contentContainer.getChildren().addAll(
                descLabel, descValueLabel,
                dropItemsLabel, dropItemsBox,
                deathMessageLabel, deathMessageBox
        );
    }

    private void addOnDamageSpecificControls(Label descLabel, Label descValueLabel) {
        double minDamage = getDoubleParam("minDamage", 0.0);
        boolean reactToPlayerOnly = getBooleanParam("reactToPlayerOnly", true);

        // Минимальный порог урона для срабатывания
        Label minDamageLabel = new Label("Min Damage Threshold:");
        minDamageLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider minDamageSlider = createParameterSlider(0, 20, minDamage, 0.5);
        Label minDamageValueLabel = new Label(String.format("%.1f", minDamage));
        minDamageValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        minDamageValueLabel.setMinWidth(30);

        minDamageSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("minDamage", value);
            minDamageValueLabel.setText(String.format("%.1f", value));
            saveNodeParameters();
        });

        HBox minDamageBox = new HBox(5, minDamageSlider, minDamageValueLabel);
        minDamageBox.setAlignment(Pos.CENTER_LEFT);

        // Реагировать только на урон от игрока
        Label reactToPlayerLabel = new Label("React Only to Player Damage:");
        reactToPlayerLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        CheckBox reactToPlayerCheckbox = new CheckBox();
        reactToPlayerCheckbox.setSelected(reactToPlayerOnly);
        reactToPlayerCheckbox.getStyleClass().add("custom-checkbox");

        HBox reactToPlayerBox = new HBox(5, reactToPlayerCheckbox);
        reactToPlayerBox.setAlignment(Pos.CENTER_LEFT);

        reactToPlayerCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("reactToPlayerOnly", newVal);
            saveNodeParameters();
        });

        contentContainer.getChildren().addAll(
                descLabel, descValueLabel,
                minDamageLabel, minDamageBox,
                reactToPlayerLabel, reactToPlayerBox
        );
    }

    private void addParticleNodeControls() {
        String particleType = getStringParam("particleType", "smoke");
        double radius = getDoubleParam("radius", 1.0);
        int count = getIntParam("count", 10);
        double duration = getDoubleParam("duration", 1.0);

        // Основное описание
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        // Тип частиц
        Label particleTypeLabel = new Label("Particle Type:");
        particleTypeLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        ComboBox<String> particleTypeSelector = new ComboBox<>();
        particleTypeSelector.getItems().addAll(
                "smoke", "flame", "heart", "water", "lava", "snow",
                "explosion", "portal", "redstone", "slime", "magic"
        );
        particleTypeSelector.setValue(particleType);
        particleTypeSelector.setPromptText("Select Particle Type");
        particleTypeSelector.getStyleClass().add(Constants.INPUT_FIELD_STYLE_CLASS);
        particleTypeSelector.setPrefWidth(150);

        particleTypeSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                dynamicParams.put("particleType", newVal);
                saveNodeParameters();
            }
        });

        // Радиус распространения
        Label radiusLabel = new Label("Radius:");
        radiusLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider radiusSlider = createParameterSlider(0.1, 10, radius, 0.1);
        Label radiusValueLabel = new Label(String.format("%.1f", radius));
        radiusValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        radiusValueLabel.setMinWidth(30);

        radiusSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("radius", value);
            radiusValueLabel.setText(String.format("%.1f", value));
            saveNodeParameters();
        });

        HBox radiusBox = new HBox(5, radiusSlider, radiusValueLabel);
        radiusBox.setAlignment(Pos.CENTER_LEFT);

        // Количество частиц
        Label countLabel = new Label("Particle Count:");
        countLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider countSlider = createParameterSlider(1, 100, count, 1);
        Label countValueLabel = new Label(String.valueOf(count));
        countValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        countValueLabel.setMinWidth(30);

        countSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int value = newVal.intValue();
            dynamicParams.put("count", value);
            countValueLabel.setText(String.valueOf(value));
            saveNodeParameters();
        });

        HBox countBox = new HBox(5, countSlider, countValueLabel);
        countBox.setAlignment(Pos.CENTER_LEFT);

        // Длительность эффекта
        Label durationLabel = new Label("Duration (seconds):");
        durationLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider durationSlider = createParameterSlider(0.1, 10, duration, 0.1);
        Label durationValueLabel = new Label(String.format("%.1f s", duration));
        durationValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        durationValueLabel.setMinWidth(40);

        durationSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("duration", value);
            durationValueLabel.setText(String.format("%.1f s", value));
            saveNodeParameters();
        });

        HBox durationBox = new HBox(5, durationSlider, durationValueLabel);
        durationBox.setAlignment(Pos.CENTER_LEFT);

        // Добавляем все элементы в контейнер
        contentContainer.getChildren().addAll(
                descLabel, descValueLabel,
                particleTypeLabel, particleTypeSelector,
                radiusLabel, radiusBox,
                countLabel, countBox,
                durationLabel, durationBox
        );
    }

    private void addDisplayTitleNodeControls() {
        String titleText = getStringParam("titleText", "");
        String subtitleText = getStringParam("subtitleText", "");
        double displayTime = getDoubleParam("displayTime", 3.0);
        String color = getStringParam("color", "white");

        // Основное описание
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        // Текст заголовка
        Label titleTextLabel = new Label("Title Text:");
        titleTextLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        TextField titleTextField = new TextField(titleText);
        titleTextField.setPromptText("Main Title");
        titleTextField.getStyleClass().add(Constants.PARAMETER_FIELD_STYLE_CLASS);

        titleTextField.textProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("titleText", newVal);
            saveNodeParameters();
        });

        // Текст подзаголовка
        Label subtitleTextLabel = new Label("Subtitle Text:");
        subtitleTextLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        TextField subtitleTextField = new TextField(subtitleText);
        subtitleTextField.setPromptText("Subtitle");
        subtitleTextField.getStyleClass().add(Constants.PARAMETER_FIELD_STYLE_CLASS);

        subtitleTextField.textProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("subtitleText", newVal);
            saveNodeParameters();
        });

        // Время отображения
        Label displayTimeLabel = new Label("Display Time (seconds):");
        displayTimeLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider displayTimeSlider = createParameterSlider(0.5, 10, displayTime, 0.5);
        Label displayTimeValueLabel = new Label(String.format("%.1f s", displayTime));
        displayTimeValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        displayTimeValueLabel.setMinWidth(40);

        displayTimeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("displayTime", value);
            displayTimeValueLabel.setText(String.format("%.1f s", value));
            saveNodeParameters();
        });

        HBox displayTimeBox = new HBox(5, displayTimeSlider, displayTimeValueLabel);
        displayTimeBox.setAlignment(Pos.CENTER_LEFT);

        // Цвет текста
        Label colorLabel = new Label("Text Color:");
        colorLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        ComboBox<String> colorSelector = new ComboBox<>();
        colorSelector.getItems().addAll(
                "white", "black", "red", "green", "blue", "yellow",
                "purple", "orange", "gray", "aqua", "gold"
        );
        colorSelector.setValue(color);
        colorSelector.setPromptText("Select Color");
        colorSelector.getStyleClass().add(Constants.INPUT_FIELD_STYLE_CLASS);
        colorSelector.setPrefWidth(150);

        colorSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                dynamicParams.put("color", newVal);
                saveNodeParameters();
            }
        });

        // Добавляем все элементы в контейнер
        contentContainer.getChildren().addAll(
                descLabel, descValueLabel,
                titleTextLabel, titleTextField,
                subtitleTextLabel, subtitleTextField,
                displayTimeLabel, displayTimeBox,
                colorLabel, colorSelector
        );
    }

    private void addSoundNodeControls() {
        String soundId = getStringParam("soundId", "entity.generic.hurt");
        double volume = getDoubleParam("volume", 1.0);
        double pitch = getDoubleParam("pitch", 1.0);
        double radius = getDoubleParam("radius", 16.0);

        // Основное описание
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        // ID звука
        Label soundIdLabel = new Label("Sound ID:");
        soundIdLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        ComboBox<String> soundIdSelector = new ComboBox<>();
        soundIdSelector.getItems().addAll(
                "entity.generic.hurt", "entity.player.hurt", "entity.player.death",
                "block.note_block.bass", "block.note_block.bell", "block.note_block.harp",
                "entity.experience_orb.pickup", "entity.item.pickup", "block.chest.open",
                "block.wooden_door.open", "block.wooden_door.close", "entity.arrow.hit",
                "entity.generic.explode", "entity.lightning_bolt.thunder", "ambient.weather.rain"
        );
        soundIdSelector.setValue(soundId);
        soundIdSelector.setPromptText("Select Sound");
        soundIdSelector.setEditable(true); // Позволяет ввести кастомный ID
        soundIdSelector.getStyleClass().add(Constants.INPUT_FIELD_STYLE_CLASS);
        soundIdSelector.setPrefWidth(200);

        soundIdSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                dynamicParams.put("soundId", newVal);
                saveNodeParameters();
            }
        });

        // Громкость
        Label volumeLabel = new Label("Volume:");
        volumeLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider volumeSlider = createParameterSlider(0, 2, volume, 0.1);
        Label volumeValueLabel = new Label(String.format("%.1f", volume));
        volumeValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        volumeValueLabel.setMinWidth(30);

        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("volume", value);
            volumeValueLabel.setText(String.format("%.1f", value));
            saveNodeParameters();
        });

        HBox volumeBox = new HBox(5, volumeSlider, volumeValueLabel);
        volumeBox.setAlignment(Pos.CENTER_LEFT);

        // Высота звука (pitch)
        Label pitchLabel = new Label("Pitch:");
        pitchLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider pitchSlider = createParameterSlider(0.5, 2, pitch, 0.1);
        Label pitchValueLabel = new Label(String.format("%.1f", pitch));
        pitchValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        pitchValueLabel.setMinWidth(30);

        pitchSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("pitch", value);
            pitchValueLabel.setText(String.format("%.1f", value));
            saveNodeParameters();
        });

        HBox pitchBox = new HBox(5, pitchSlider, pitchValueLabel);
        pitchBox.setAlignment(Pos.CENTER_LEFT);

        // Радиус слышимости
        Label radiusLabel = new Label("Hearing Radius:");
        radiusLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Slider radiusSlider = createParameterSlider(1, 64, radius, 1);
        Label radiusValueLabel = new Label(String.format("%.0f", radius));
        radiusValueLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);
        radiusValueLabel.setMinWidth(30);

        radiusSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double value = newVal.doubleValue();
            dynamicParams.put("radius", value);
            radiusValueLabel.setText(String.format("%.0f", value));
            saveNodeParameters();
        });

        HBox radiusBox = new HBox(5, radiusSlider, radiusValueLabel);
        radiusBox.setAlignment(Pos.CENTER_LEFT);

        // Добавляем все элементы в контейнер
        contentContainer.getChildren().addAll(
                descLabel, descValueLabel,
                soundIdLabel, soundIdSelector,
                volumeLabel, volumeBox,
                pitchLabel, pitchBox,
                radiusLabel, radiusBox
        );
    }

    private void addScriptNodeControls() {
        String scriptType = getStringParam("scriptType", "javascript");
        String scriptContent = getStringParam("scriptContent", "// Add your script here");

        // Основное описание
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        // Тип скрипта
        Label scriptTypeLabel = new Label("Script Type:");
        scriptTypeLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        ComboBox<String> scriptTypeSelector = new ComboBox<>();
        scriptTypeSelector.getItems().addAll("javascript", "python", "lua");
        scriptTypeSelector.setValue(scriptType);
        scriptTypeSelector.setPromptText("Select Script Type");
        scriptTypeSelector.getStyleClass().add(Constants.INPUT_FIELD_STYLE_CLASS);
        scriptTypeSelector.setPrefWidth(150);

        scriptTypeSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                dynamicParams.put("scriptType", newVal);
                saveNodeParameters();
            }
        });

        // Содержимое скрипта
        Label scriptContentLabel = new Label("Script:");
        scriptContentLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        TextArea scriptContentArea = new TextArea(scriptContent);
        scriptContentArea.setPromptText("Enter script code here");
        scriptContentArea.getStyleClass().add(Constants.PARAMETER_FIELD_STYLE_CLASS);
        scriptContentArea.setWrapText(true);
        scriptContentArea.setPrefRowCount(8);

        scriptContentArea.textProperty().addListener((obs, oldVal, newVal) -> {
            dynamicParams.put("scriptContent", newVal);
            saveNodeParameters();
        });

        // Добавляем все элементы в контейнер
        contentContainer.getChildren().addAll(
                descLabel, descValueLabel,
                scriptTypeLabel, scriptTypeSelector,
                scriptContentLabel, scriptContentArea
        );
    }

    private void addCompositeNodeControls() {
        // Основное описание
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        // Добавляем только нужные элементы в контейнер
        contentContainer.getChildren().addAll(
                descLabel, descValueLabel
        );

        // Дополнительные параметры для ParallelNode
        if (node.getType().equals("ParallelNode")) {
            boolean abortOnFailure = getBooleanParam("abortOnFailure", true);

            Label abortLabel = new Label("Abort on Failure:");
            abortLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

            CheckBox abortCheckbox = new CheckBox();
            abortCheckbox.setSelected(abortOnFailure);
            abortCheckbox.getStyleClass().add("custom-checkbox");

            abortCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                dynamicParams.put("abortOnFailure", newVal);
                saveNodeParameters();
            });

            HBox abortBox = new HBox(5, abortCheckbox);
            abortBox.setAlignment(Pos.CENTER_LEFT);

            contentContainer.getChildren().addAll(abortLabel, abortBox);
        }
    }

    private void addWeightedSelectorNodeControls() {
        // Основное описание
        Label descLabel = new Label("Description:");
        descLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        Label descValueLabel = new Label();
        descValueLabel.setWrapText(true);
        descValueLabel.getStyleClass().add(Constants.NODE_TITLE_STYLE_CLASS);
        descValueLabel.textProperty().bind(node.descriptionProperty());

        // Информация о весах
        Label weightsLabel = new Label("Child Node Weights:");
        weightsLabel.getStyleClass().add(Constants.PARAMETER_LABEL_STYLE_CLASS);

        // Панель для отображения весов (будет заполняться динамически)
        VBox weightsContainer = new VBox(5);
        weightsContainer.setPadding(new Insets(5));
        weightsContainer.getStyleClass().add("weights-container");

        // Кнопка обновления весов
        Button refreshButton = new Button("Refresh Connected Nodes");
        refreshButton.getStyleClass().add(Constants.CONTROL_BUTTON_STYLE_CLASS);
        refreshButton.setOnAction(e -> updateChildNodeWeights(weightsContainer));

        // Добавляем все элементы в контейнер (без описания поведения)
        contentContainer.getChildren().addAll(
                descLabel, descValueLabel,
                // behaviorLabel, behaviorDescription, - эти строки удалены
                weightsLabel, weightsContainer,
                refreshButton
        );

        // Немедленно обновляем список весов
        updateChildNodeWeights(weightsContainer);
    }

    // Метод для обновления контейнера весов дочерних узлов
    private void updateChildNodeWeights(VBox container) {
        container.getChildren().clear();

        // Если нет подключенных узлов, показываем сообщение
        if (outputConnections.isEmpty()) {
            Label noNodesLabel = new Label("No connected nodes. Connect nodes to assign weights.");
            noNodesLabel.setWrapText(true);
            noNodesLabel.getStyleClass().add("info-label");
            container.getChildren().add(noNodesLabel);
            return;
        }

        // Инициализация весов из параметров
        initializeChildNodeWeights();

        // Создаем UI элементы для каждого подключенного узла
        for (ConnectionView connection : outputConnections) {
            Node childNode = connection.getTargetView().getNode();
            String childId = childNode.getId().toString();
            String childDescription = childNode.getDescription();
            double weight = getChildNodeWeight(childId);

            // Создаем панель для узла
            HBox nodeWeightBox = new HBox(5);
            nodeWeightBox.setAlignment(Pos.CENTER_LEFT);

            // Метка с описанием узла
            Label nodeLabel = new Label(childDescription);
            nodeLabel.setMinWidth(100);
            nodeLabel.setPrefWidth(150);
            nodeLabel.setMaxWidth(150);
            nodeLabel.setWrapText(true);

            // Слайдер веса
            Slider weightSlider = createParameterSlider(0, 100, weight, 1);
            weightSlider.setPrefWidth(120);

            // Метка значения веса
            Label weightValueLabel = new Label(String.format("%.0f%%", weight));
            weightValueLabel.setMinWidth(40);

            // Обновляем вес при изменении слайдера
            weightSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                double value = newVal.doubleValue();
                setChildNodeWeight(childId, value);
                weightValueLabel.setText(String.format("%.0f%%", value));
            });

            // Добавляем все в строку
            nodeWeightBox.getChildren().addAll(nodeLabel, weightSlider, weightValueLabel);

            // Добавляем строку в контейнер
            container.getChildren().add(nodeWeightBox);
        }

        // Добавляем информацию о суммарном весе
        double totalWeight = calculateTotalWeight();
        Label totalWeightLabel = new Label(String.format("Total weight: %.0f", totalWeight));
        totalWeightLabel.setStyle("-fx-font-weight: bold;");
        container.getChildren().add(totalWeightLabel);
    }

    private void initializeChildNodeWeights() {
        // Загружаем веса из параметров узла
        String weightParam = getStringParam("nodeWeights", "");

        if (!weightParam.isEmpty()) {
            try {
                String[] weightPairs = weightParam.split(";");
                for (String pair : weightPairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        String nodeId = keyValue[0].trim();
                        double weight = Double.parseDouble(keyValue[1].trim());
                        childNodeWeights.put(nodeId, weight);
                    }
                }
            } catch (Exception e) {
                System.err.println("Ошибка при разборе весов узлов: " + e.getMessage());
            }
        }

        // Добавляем отсутствующие узлы с весом по умолчанию
        for (ConnectionView connection : outputConnections) {
            Node childNode = connection.getTargetView().getNode();
            String childId = childNode.getId().toString();
            if (!childNodeWeights.containsKey(childId)) {
                childNodeWeights.put(childId, 50.0); // По умолчанию 50%
            }
        }

        saveChildNodeWeights();
    }

    private double getChildNodeWeight(String nodeId) {
        return childNodeWeights.getOrDefault(nodeId, 50.0);
    }

    private void setChildNodeWeight(String nodeId, double weight) {
        childNodeWeights.put(nodeId, weight);
        saveChildNodeWeights();
    }

    private double calculateTotalWeight() {
        // Проверяем, что веса присутствуют для всех подключенных узлов
        double total = 0;
        for (ConnectionView connection : outputConnections) {
            Node childNode = connection.getTargetView().getNode();
            String childId = childNode.getId().toString();
            total += childNodeWeights.getOrDefault(childId, 50.0);
        }
        return total;
    }

    private void saveChildNodeWeights() {
        // Сохраняем веса в параметр узла
        StringBuilder weightBuilder = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, Double> entry : childNodeWeights.entrySet()) {
            // Сохраняем только для реально подключенных узлов
            boolean isConnected = false;
            for (ConnectionView connection : outputConnections) {
                Node childNode = connection.getTargetView().getNode();
                if (childNode.getId().toString().equals(entry.getKey())) {
                    isConnected = true;
                    break;
                }
            }

            if (isConnected) {
                if (!first) {
                    weightBuilder.append(";");
                }
                weightBuilder.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
        }

        dynamicParams.put("nodeWeights", weightBuilder.toString());
        saveNodeParameters();
    }

    // Вспомогательные методы для работы с динамическими параметрами
    private String getStringParam(String key, String defaultValue) {
        Object value = dynamicParams.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private double getDoubleParam(String key, double defaultValue) {
        try {
            Object value = dynamicParams.get(key);
            if (value instanceof Double) {
                return (Double) value;
            } else if (value instanceof String) {
                return Double.parseDouble((String) value);
            }
        } catch (Exception e) {
            // В случае ошибки возвращаем значение по умолчанию
        }
        return defaultValue;
    }

    private int getIntParam(String key, int defaultValue) {
        try {
            Object value = dynamicParams.get(key);
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof String) {
                return Integer.parseInt((String) value);
            }
        } catch (Exception e) {
            // В случае ошибки возвращаем значение по умолчанию
        }
        return defaultValue;
    }

    private boolean getBooleanParam(String key, boolean defaultValue) {
        try {
            Object value = dynamicParams.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            } else if (value instanceof String) {
                return Boolean.parseBoolean((String) value);
            }
        } catch (Exception e) {
            // В случае ошибки возвращаем значение по умолчанию
        }
        return defaultValue;
    }

    private Slider createParameterSlider(double min, double max, double value, double increment) {
        Slider slider = new Slider(min, max, value);
        slider.getStyleClass().add("custom-slider");
        slider.setShowTickMarks(false);
        slider.setShowTickLabels(false);
        slider.setPrefWidth(120);
        slider.setBlockIncrement(increment);
        return slider;
    }

    private void setupInteractions() {
        final double[] lastX = new double[1];
        final double[] lastY = new double[1];

        setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                // Проверяем, не клик ли это по пину
                if (event.getTarget() instanceof Circle) {
                    return;
                }

                // Выделяем узел
                isDragging = true;
                this.toFront();

                // Сохраняем начальные координаты
                layoutXBeforeDrag = getLayoutX();
                layoutYBeforeDrag = getLayoutY();

                // Эффект выделения через CSS
                setSelected(true);

                // Запоминаем координаты для вычисления смещения
                lastX[0] = event.getSceneX();
                lastY[0] = event.getSceneY();

                // Уведомляем о выделении узла
                if (onNodeSelected != null) {
                    onNodeSelected.accept(node);
                }

                event.consume();
            }
        });

        setOnMouseDragged(event -> {
            if (isDragging && event.getButton() == MouseButton.PRIMARY) {
                // Вычисляем смещение в пикселях
                double deltaX = event.getSceneX() - lastX[0];
                double deltaY = event.getSceneY() - lastY[0];

                // Запоминаем текущие координаты
                lastX[0] = event.getSceneX();
                lastY[0] = event.getSceneY();

                // Обновляем координаты узла в модели (для правильного сохранения)
                node.setX(node.getX() + deltaX / nodeScale);
                node.setY(node.getY() + deltaY / nodeScale);

                // Перемещаем узел без использования translateX/Y для плавности
                setLayoutX(getLayoutX() + deltaX);
                setLayoutY(getLayoutY() + deltaY);

                // Уведомляем о перемещении узла для обновления соединений
                if (onNodeDragged != null) {
                    onNodeDragged.accept(this);
                }

                event.consume();
            }
        });

        setOnMouseReleased(event -> {
            if (isDragging && event.getButton() == MouseButton.PRIMARY) {
                isDragging = false;

                // Уведомляем об окончании перемещения узла
                if (onNodeReleased != null) {
                    onNodeReleased.accept(this);
                }

                event.consume();
            }
        });

        // Контекстное меню по правому клику
        setOnContextMenuRequested(event -> {
            if (contextMenu != null) {
                contextMenu.show(this, event.getScreenX(), event.getScreenY());
            }
            event.consume();
        });
    }

    private void createContextMenu() {
        contextMenu = new ContextMenu();

        MenuItem deleteItem = new MenuItem("Delete Node");
        deleteItem.setOnAction(e -> {
            if (onNodeDeleted != null) {
                onNodeDeleted.run();
            }
        });

        MenuItem expandItem = new MenuItem("Toggle Expand");
        expandItem.setOnAction(e -> {
            toggleExpanded();
        });

        MenuItem copyItem = new MenuItem("Copy Node");
        copyItem.setOnAction(e -> {
            // Копирование узла (будет реализовано в контроллере)
        });

        contextMenu.getItems().addAll(expandItem, copyItem, new SeparatorMenuItem(), deleteItem);
    }

    public void setSelected(boolean selected) {
        if (selected) {
            if (!getStyleClass().contains("node-view-selected")) {
                getStyleClass().add("node-view-selected");
            }
        } else {
            getStyleClass().remove("node-view-selected");
        }
    }

    // Плавное применение масштаба
    public void applyScale(double scale) {
        if (this.nodeScale != scale) {
            setScaleX(scale);
            setScaleY(scale);
            this.nodeScale = scale;
        }
    }

    // Плавное изменение позиции
    public void updatePosition(double x, double y) {
        setLayoutX(x);
        setLayoutY(y);
    }

    public void setOnNodeSelected(Consumer<Node> handler) {
        onNodeSelected = handler;
    }

    public void setOnNodeDragged(Consumer<NodeView> handler) {
        onNodeDragged = handler;
    }

    public void setOnNodeReleased(Consumer<NodeView> handler) {
        onNodeReleased = handler;
    }

    public void setOnNodeDeleted(Runnable handler) {
        onNodeDeleted = handler;
    }

    public void setOnConnectionStarted(BiConsumer<NodeView, String> handler) {
        onConnectionStarted = handler;
    }

    public Circle getInputPin() {
        return inputPin;
    }

    public Circle getOutputPin() {
        return outputPin;
    }

    public Node getNode() {
        return node;
    }

    public void highlightInputPin(boolean highlight) {
        if (highlight) {
            inputPin.getStyleClass().add(Constants.PIN_HIGHLIGHTED_STYLE_CLASS);
        } else {
            inputPin.getStyleClass().remove(Constants.PIN_HIGHLIGHTED_STYLE_CLASS);
            updateInputPinAppearance();
        }
    }

    public void highlightOutputPin(boolean highlight) {
        if (highlight) {
            outputPin.getStyleClass().add(Constants.PIN_HIGHLIGHTED_STYLE_CLASS);
        } else {
            outputPin.getStyleClass().remove(Constants.PIN_HIGHLIGHTED_STYLE_CLASS);
            updateOutputPinAppearance();
        }
    }

    public Point2D getInputPinPosition() {
        return inputPin.localToParent(0, 0);
    }

    public Point2D getOutputPinPosition() {
        return outputPin.localToParent(0, 0);
    }

    // Получение точной позиции центра пина
    public Point2D getInputPinCenterPosition() {
        // Получаем центр пина в координатах родителя
        return new Point2D(
                inputPin.getLayoutX() + inputPin.getTranslateX() + inputPin.getRadius(),
                inputPin.getLayoutY() + inputPin.getTranslateY() + inputPin.getRadius()
        );
    }

    // Получение точной позиции центра пина
    public Point2D getOutputPinCenterPosition() {
        // Получаем центр пина в координатах родителя
        return new Point2D(
                outputPin.getLayoutX() + outputPin.getTranslateX() + outputPin.getRadius(),
                outputPin.getLayoutY() + outputPin.getTranslateY() + outputPin.getRadius()
        );
    }

    public void addInputConnection(ConnectionView connection) {
        if (!inputConnections.contains(connection)) {
            inputConnections.add(connection);
            updateInputPinAppearance();
        }
    }

    public void addOutputConnection(ConnectionView connection) {
        if (!outputConnections.contains(connection)) {
            outputConnections.add(connection);
            updateOutputPinAppearance();
        }
    }

    public void removeInputConnection(ConnectionView connection) {
        inputConnections.remove(connection);
        updateInputPinAppearance();
    }

    public void removeOutputConnection(ConnectionView connection) {
        outputConnections.remove(connection);
        updateOutputPinAppearance();
    }

    private void updateInputPinAppearance() {
        if (!inputConnections.isEmpty()) {
            if (!inputPin.getStyleClass().contains(Constants.PIN_CONNECTED_STYLE_CLASS)) {
                inputPin.getStyleClass().add(Constants.PIN_CONNECTED_STYLE_CLASS);
            }
        } else {
            inputPin.getStyleClass().remove(Constants.PIN_CONNECTED_STYLE_CLASS);
        }
    }

    private void updateOutputPinAppearance() {
        if (!outputConnections.isEmpty()) {
            if (!outputPin.getStyleClass().contains(Constants.PIN_CONNECTED_STYLE_CLASS)) {
                outputPin.getStyleClass().add(Constants.PIN_CONNECTED_STYLE_CLASS);
            }
        } else {
            outputPin.getStyleClass().remove(Constants.PIN_CONNECTED_STYLE_CLASS);
        }
    }

    public boolean hasInputConnection() {
        return !inputConnections.isEmpty();
    }

    public boolean hasOutputConnections() {
        return !outputConnections.isEmpty();
    }

    // Вспомогательный метод для показа уведомлений
    private void showNotification(String message) {
        if (getScene() != null && getScene().getWindow() != null) {
            GUIUtils.showNotification((Pane) getParent(), message);
        }
    }

    // Внутренний функциональный интерфейс для обработки событий
    @FunctionalInterface
    public interface BiConsumer<T, U> {
        void accept(T t, U u);
    }
}