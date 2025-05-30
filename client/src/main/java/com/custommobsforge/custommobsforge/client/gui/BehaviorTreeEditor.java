package com.custommobsforge.custommobsforge.client.gui;

import com.custommobsforge.custommobsforge.client.gui.BehaviorTreeModel;
import com.custommobsforge.custommobsforge.client.gui.Connection;
import com.custommobsforge.custommobsforge.client.gui.Node;
import com.custommobsforge.custommobsforge.client.gui.UndoRedoManager;
import com.custommobsforge.custommobsforge.client.gui.GUIUtils;
import com.custommobsforge.custommobsforge.client.gui.Constants;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.*;

public class BehaviorTreeEditor extends VBox {
    // Модель данных
    private BehaviorTreeModel model;

    // UI компоненты
    private StackPane viewport;
    private Pane nodePane;
    private Pane connectionPane;  // Отдельная панель для соединений
    private HBox topControlPanel;  // Отдельная ссылка на верхнюю панель управления
    private HBox bottomControlPanel; // Ссылка на нижнюю панель управления
    private Canvas gridCanvas;
    private ComboBox<String> nodeTypeSelector;
    private TextField nodeDescriptionField;
    private TextField nodeParameterField;

    // Управление представлениями
    private Map<Node, NodeView> nodeViews = new HashMap<>();
    private Map<Connection, ConnectionView> connectionViews = new HashMap<>();
    private TemporaryConnectionView tempConnectionView;

    // Состояние редактора
    private Node selectedNode;
    private NodeView sourceNodeView;
    private String connectionSourceType;
    private NodeView targetNodeView;

    // Параметры вьюпорта
    private DoubleProperty viewX = new SimpleDoubleProperty(BehaviorTreeModel.WORLD_WIDTH / 2.0);
    private DoubleProperty viewY = new SimpleDoubleProperty(BehaviorTreeModel.WORLD_HEIGHT / 2.0);
    private DoubleProperty zoom = new SimpleDoubleProperty(1.5);
    private boolean scaleNodesWithZoom = true;

    // Состояние перетаскивания
    private boolean isDraggingViewport = false;
    private double dragStartX, dragStartY;

    // Управление историей действий
    private UndoRedoManager undoRedoManager;

    public BehaviorTreeEditor(UndoRedoManager undoRedoManager) {
        this.undoRedoManager = undoRedoManager;
        this.model = new BehaviorTreeModel();

        setStyle("-fx-background-color: #212121;");
        setSpacing(0);
        setPadding(new Insets(0));

        // Создаем верхнюю панель управления
        VBox controlPanel = createControlPanel();

        // Сохраняем ссылку на верхнюю панель
        topControlPanel = (HBox) ((VBox) controlPanel).getChildren().get(0);
        // Сохраняем ссылку на нижнюю панель
        bottomControlPanel = (HBox) ((VBox) controlPanel).getChildren().get(1);

        // Создаем вьюпорт
        viewport = createViewport();
        VBox.setVgrow(viewport, Priority.ALWAYS);

        getChildren().addAll(controlPanel, viewport);

        // Инициализируем рабочую область
        Platform.runLater(this::initializeWorkspace);
    }

    private VBox createControlPanel() {
        // Создаем панель для добавления узлов
        HBox nodeInputBox = createNodeInputBox();
        nodeInputBox.setStyle("-fx-padding: 5 10 5 10; -fx-background-color: #2B2B2B;");

        // Создаем панель с кнопками управления
        HBox controlBox = createControlBox();
        controlBox.setStyle("-fx-padding: 5 10 5 10; -fx-background-color: #2B2B2B;");

        // Объединяем в одну панель
        VBox panel = new VBox(nodeInputBox, controlBox);
        panel.setStyle("-fx-background-color: #2B2B2B;");

        return panel;
    }

    private HBox createNodeInputBox() {
        // Селектор типа узла - ОБНОВЛЕННЫЙ СПИСОК
        nodeTypeSelector = new ComboBox<>();
        nodeTypeSelector.getItems().addAll(
                // Композитные узлы
                "SequenceNode", "SelectorNode", "ParallelNode", "PrioritySelectorNode",
                // Узлы действий
                "AttackNode", "PlayAnimationNode", "FollowNode", "FleeNode",
                // Узлы проверок
                "TargetPlayerNode", "HasTargetNode", "HealthCheckNode",
                // События
                "OnSpawnNode", "OnDeathNode", "OnDamageNode",
                // Эффекты
                "SpawnParticleNode", "DisplayTitleNode", "PlaySoundNode",
                // Утилиты
                "TimerNode", "ScriptNode", "WeightedSelectorNode"
        );
        nodeTypeSelector.setPromptText("Select Node Type");
        nodeTypeSelector.setStyle("-fx-background-color: #3A3A3A; -fx-text-fill: #EBEBEB; -fx-background-radius: 3;");
        nodeTypeSelector.setPrefWidth(150);

        // Поле описания
        nodeDescriptionField = new TextField();
        nodeDescriptionField.setPromptText("Node Description");
        nodeDescriptionField.setStyle("-fx-background-color: #3A3A3A; -fx-text-fill: #EBEBEB; -fx-prompt-text-fill: #808080; -fx-background-radius: 3;");

        // Поле параметра
        nodeParameterField = new TextField();
        nodeParameterField.setPromptText("Parameter");
        nodeParameterField.setStyle("-fx-background-color: #3A3A3A; -fx-text-fill: #EBEBEB; -fx-prompt-text-fill: #808080; -fx-background-radius: 3;");

        // Кнопка добавления
        Button addNodeButton = new Button("Add Node");
        addNodeButton.setStyle("-fx-background-color: #4381C1; -fx-text-fill: #EBEBEB; -fx-background-radius: 3;");
        addNodeButton.setOnAction(e -> addNewNode());

        // Компоновка элементов
        HBox inputBox = new HBox(10);
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.getChildren().addAll(
                new Label("Node Type:") {{ setStyle("-fx-text-fill: #EBEBEB;"); }},
                nodeTypeSelector,
                new Label("Description:") {{ setStyle("-fx-text-fill: #EBEBEB;"); }},
                nodeDescriptionField,
                new Label("Parameter:") {{ setStyle("-fx-text-fill: #EBEBEB;"); }},
                nodeParameterField,
                addNodeButton
        );

        return inputBox;
    }

    private HBox createControlBox() {
        Button autoLayoutButton = createControlButton("Auto Layout", e -> applyAutoLayout());
        Button exportButton = createControlButton("Export", e -> exportBehaviorTree());
        Button importButton = createControlButton("Import", e -> importBehaviorTree());
        Button centerButton = createControlButton("Center View", e -> centerWorkspace());
        Button zoomInButton = createControlButton("Zoom In", e -> changeZoom(1.1));
        Button zoomOutButton = createControlButton("Zoom Out", e -> changeZoom(0.9));

        HBox controlBox = new HBox(10);
        controlBox.setAlignment(Pos.CENTER_LEFT);
        controlBox.getChildren().addAll(
                autoLayoutButton, exportButton, importButton, centerButton,
                new Separator(javafx.geometry.Orientation.VERTICAL) {{ setPadding(new Insets(0, 5, 0, 5)); }},
                zoomInButton, zoomOutButton
        );

        return controlBox;
    }

    private Button createControlButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: #3A3A3A; -fx-text-fill: #EBEBEB; -fx-background-radius: 3;");
        button.setOnAction(action);
        return button;
    }

    private StackPane createViewport() {
        StackPane viewportContainer = new StackPane();
        viewportContainer.setStyle("-fx-background-color: #212121;");

        // Создаем канвас для сетки (самый нижний слой)
        gridCanvas = new Canvas();

        // Создаем панель для соединений (средний слой)
        connectionPane = new Pane();

        // Создаем панель для узлов (верхний слой)
        nodePane = new Pane();

        // Добавляем компоненты во вьюпорт с правильным порядком слоев
        viewportContainer.getChildren().addAll(gridCanvas, connectionPane, nodePane);

        // Ставим z-order для панелей (чтобы сетка была под всем, а узлы сверху)
        gridCanvas.setViewOrder(10);
        connectionPane.setViewOrder(5);
        nodePane.setViewOrder(1);

        // Настройка канваса для сетки
        gridCanvas.widthProperty().bind(viewportContainer.widthProperty());
        gridCanvas.heightProperty().bind(viewportContainer.heightProperty());
        gridCanvas.setMouseTransparent(true);

        // Обработчики для перемещения и масштабирования вьюпорта
        setupViewportHandlers(viewportContainer);

        // Обработчики для создания соединений
        setupNodePaneHandlers();

        return viewportContainer;
    }

    private void setupViewportHandlers(StackPane viewportContainer) {
        // Масштабирование колесиком мыши
        viewportContainer.setOnScroll(event -> {
            event.consume();

            // Сохраняем точку под курсором
            Point2D mouseWorld = screenToWorld(event.getX(), event.getY());

            // Изменяем масштаб
            double zoomFactor = event.getDeltaY() > 0 ? 1.1 : 0.9;
            double oldZoom = zoom.get();
            zoom.set(zoom.get() * zoomFactor);

            // Ограничиваем масштаб
            clampViewport();

            // Корректируем вид чтобы точка под курсором оставалась на месте
            if (Math.abs(zoom.get() - oldZoom) > 0.001) {
                Point2D newScreen = worldToScreen(mouseWorld.getX(), mouseWorld.getY());
                viewX.set(viewX.get() - (event.getX() - newScreen.getX()) / zoom.get());
                viewY.set(viewY.get() - (event.getY() - newScreen.getY()) / zoom.get());
                clampViewport();
            }

            // Обновляем сетку и узлы
            redrawGrid();
            updateAllNodesAndConnections();

            // После обновления узлов, обеспечиваем правильный Z-index
            ensureCorrectZOrder();
        });

        // Перемещение средней кнопкой мыши или Alt+левая кнопка
        viewportContainer.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.MIDDLE ||
                    (event.getButton() == MouseButton.PRIMARY && event.isAltDown())) {
                isDraggingViewport = true;
                dragStartX = event.getSceneX();
                dragStartY = event.getSceneY();
                viewportContainer.setCursor(Cursor.MOVE);
                event.consume();
            }
        });

        viewportContainer.setOnMouseDragged(event -> {
            if (isDraggingViewport) {
                double deltaX = event.getSceneX() - dragStartX;
                double deltaY = event.getSceneY() - dragStartY;

                viewX.set(viewX.get() - deltaX / zoom.get());
                viewY.set(viewY.get() - deltaY / zoom.get());

                dragStartX = event.getSceneX();
                dragStartY = event.getSceneY();

                clampViewport();
                redrawGrid();
                updateAllNodesAndConnections();
                event.consume();
            }
        });

        viewportContainer.setOnMouseReleased(event -> {
            if (isDraggingViewport) {
                isDraggingViewport = false;
                viewportContainer.setCursor(Cursor.DEFAULT);

                // Обеспечиваем правильный Z-index
                ensureCorrectZOrder();

                event.consume();
            }
        });

        // Обновление при изменении размера окна
        viewportContainer.widthProperty().addListener((obs, old, newVal) -> {
            redrawGrid();
            updateAllNodesAndConnections();
            ensureCorrectZOrder();
        });

        viewportContainer.heightProperty().addListener((obs, old, newVal) -> {
            redrawGrid();
            updateAllNodesAndConnections();
            ensureCorrectZOrder();
        });

        // Обработка нажатий клавиш
        viewportContainer.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                if (selectedNode != null) {
                    deleteNode(selectedNode);
                }
                event.consume();
            }
        });

        // Убеждаемся, что панель может получать фокус для обработки клавиш
        viewportContainer.setFocusTraversable(true);
    }

    private void setupNodePaneHandlers() {
        // Обработчик для создания соединений между узлами
        nodePane.setOnMouseDragged(event -> {
            if (sourceNodeView != null && tempConnectionView != null) {
                tempConnectionView.updateEndPoint(new Point2D(event.getSceneX(), event.getSceneY()));
                highlightPotentialTargets(event.getSceneX(), event.getSceneY());
                event.consume();
            }
        });

        nodePane.setOnMouseReleased(event -> {
            if (sourceNodeView != null && tempConnectionView != null) {
                if (targetNodeView != null) {
                    // Пытаемся создать соединение
                    tryCreateConnection(sourceNodeView.getNode(), targetNodeView.getNode());
                }

                // Удаляем временное соединение
                cleanupTemporaryConnection();
                event.consume();
            }
        });

        // Клик по пустому месту для снятия выделения
        nodePane.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getTarget() == nodePane) {
                if (selectedNode != null) {
                    NodeView view = nodeViews.get(selectedNode);
                    if (view != null) {
                        view.setSelected(false);
                    }
                    selectedNode = null;
                }
            }
        });
    }

    // Новый метод для обеспечения правильного z-order
    private void ensureCorrectZOrder() {
        // Устанавливаем явный z-order для всех компонентов
        gridCanvas.setViewOrder(10);    // Самый задний план
        connectionPane.setViewOrder(5); // Средний план
        nodePane.setViewOrder(1);       // Передний план

        // Самый важный момент: вместо setViewOrder, который иногда не работает,
        // используем явные вызовы методов структурирования дочерних элементов

        // Гарантируем, что панели управления всегда на переднем плане
        // Выполняем эти операции немедленно
        if (topControlPanel != null) {
            // Перемещаем панель на самый верх стека отображения
            topControlPanel.toFront();
        }

        if (bottomControlPanel != null) {
            // Перемещаем панель на самый верх стека отображения
            bottomControlPanel.toFront();
        }

        // Дополнительное обеспечение через Platform.runLater
        Platform.runLater(() -> {
            // Верхняя панель должна быть всегда на переднем плане
            VBox parent = (VBox) getParent();

            if (parent != null) {
                // Ищем и перестраиваем порядок дочерних элементов
                for (javafx.scene.Node node : parent.getChildren()) {
                    if (node instanceof StackPane) {
                        // Viewport должен быть ниже панелей
                        node.toBack();
                    } else if (node instanceof HBox || node instanceof VBox) {
                        // Панели управления должны быть сверху
                        node.toFront();
                    }
                }
            }

            // Еще раз установим z-order через CSS
            if (topControlPanel != null) {
                topControlPanel.setStyle(topControlPanel.getStyle() + " -fx-z-index: 1000;");
                topControlPanel.toFront();
            }

            if (bottomControlPanel != null) {
                bottomControlPanel.setStyle(bottomControlPanel.getStyle() + " -fx-z-index: 1000;");
                bottomControlPanel.toFront();
            }
        });
    }

    private void initializeWorkspace() {
        // Обновляем сетку
        redrawGrid();

        // Если нет узлов, создаем корневой узел
        if (model.getNodes().isEmpty()) {
            Node rootNode = model.createRootNode();
            drawNode(rootNode);
        }

        // Центрируем вид
        centerWorkspace();

        // Устанавливаем правильный z-order для всех компонентов
        ensureCorrectZOrder();
    }

    private Point2D worldToScreen(double worldX, double worldY) {
        double screenX = (worldX - viewX.get()) * zoom.get() + gridCanvas.getWidth() / 2;
        double screenY = (worldY - viewY.get()) * zoom.get() + gridCanvas.getHeight() / 2;
        return new Point2D(screenX, screenY);
    }

    private Point2D screenToWorld(double screenX, double screenY) {
        double worldX = (screenX - gridCanvas.getWidth() / 2) / zoom.get() + viewX.get();
        double worldY = (screenY - gridCanvas.getHeight() / 2) / zoom.get() + viewY.get();
        return new Point2D(worldX, worldY);
    }

    private void clampViewport() {
        double screenWidth = gridCanvas.getWidth();
        double screenHeight = gridCanvas.getHeight();

        // Ограничение масштаба
        double minZoomX = screenWidth / BehaviorTreeModel.WORLD_WIDTH;
        double minZoomY = screenHeight / BehaviorTreeModel.WORLD_HEIGHT;
        double minZoom = Math.min(minZoomX, minZoomY) * 0.9;
        double maxZoom = 2.0;

        zoom.set(Math.max(minZoom, Math.min(maxZoom, zoom.get())));

        // Ограничение позиции
        double visibleWidth = screenWidth / zoom.get();
        double visibleHeight = screenHeight / zoom.get();

        if (visibleWidth >= BehaviorTreeModel.WORLD_WIDTH) {
            viewX.set(BehaviorTreeModel.WORLD_WIDTH / 2);
        } else {
            viewX.set(Math.max(visibleWidth / 2, Math.min(BehaviorTreeModel.WORLD_WIDTH - visibleWidth / 2, viewX.get())));
        }

        if (visibleHeight >= BehaviorTreeModel.WORLD_HEIGHT) {
            viewY.set(BehaviorTreeModel.WORLD_HEIGHT / 2);
        } else {
            viewY.set(Math.max(visibleHeight / 2, Math.min(BehaviorTreeModel.WORLD_HEIGHT - visibleHeight / 2, viewY.get())));
        }
    }

    private void redrawGrid() {
        clampViewport();

        GraphicsContext gc = gridCanvas.getGraphicsContext2D();
        double w = gridCanvas.getWidth();
        double h = gridCanvas.getHeight();

        // Очистка холста - меняем цвет фона на #393939
        gc.setFill(Constants.BG_COLOR); // Color.rgb(57, 57, 57)
        gc.fillRect(0, 0, w, h);

        // Отрисовка границ мира
        Point2D topLeft = worldToScreen(0, 0);
        Point2D bottomRight = worldToScreen(BehaviorTreeModel.WORLD_WIDTH, BehaviorTreeModel.WORLD_HEIGHT);

        gc.setFill(Color.rgb(45, 45, 45)); // Цвет фона рабочей области
        gc.fillRect(
                Math.max(0, topLeft.getX()),
                Math.max(0, topLeft.getY()),
                Math.min(w, bottomRight.getX()) - Math.max(0, topLeft.getX()),
                Math.min(h, bottomRight.getY()) - Math.max(0, topLeft.getY())
        );

        // Параметры сетки
        double gridSize = 50;
        double majorGridSize = 250;

        Point2D viewTopLeft = screenToWorld(0, 0);
        Point2D viewBottomRight = screenToWorld(w, h);

        // Рисуем малые линии сетки
        gc.setStroke(Constants.GRID_COLOR); // Color.rgb(72, 72, 72)
        gc.setLineWidth(1);

        for (int x = Math.max(0, (int)(viewTopLeft.getX() / gridSize) * (int)gridSize);
             x <= Math.min(BehaviorTreeModel.WORLD_WIDTH, viewBottomRight.getX());
             x += gridSize) {
            if (x % majorGridSize != 0) {
                Point2D start = worldToScreen(x, 0);
                Point2D end = worldToScreen(x, BehaviorTreeModel.WORLD_HEIGHT);

                if (start.getX() >= 0 && start.getX() <= w) {
                    gc.strokeLine(start.getX(), Math.max(0, start.getY()),
                            end.getX(), Math.min(h, end.getY()));
                }
            }
        }

        for (int y = Math.max(0, (int)(viewTopLeft.getY() / gridSize) * (int)gridSize);
             y <= Math.min(BehaviorTreeModel.WORLD_HEIGHT, viewBottomRight.getY());
             y += gridSize) {
            if (y % majorGridSize != 0) {
                Point2D start = worldToScreen(0, y);
                Point2D end = worldToScreen(BehaviorTreeModel.WORLD_WIDTH, y);

                if (start.getY() >= 0 && start.getY() <= h) {
                    gc.strokeLine(Math.max(0, start.getX()), start.getY(),
                            Math.min(w, end.getX()), end.getY());
                }
            }
        }

        // Рисуем основные линии сетки
        gc.setStroke(Constants.GRID_MAJOR_COLOR); // Color.rgb(88, 88, 88)
        gc.setLineWidth(1);


        for (int x = Math.max(0, (int)(viewTopLeft.getX() / majorGridSize) * (int)majorGridSize);
             x <= Math.min(BehaviorTreeModel.WORLD_WIDTH, viewBottomRight.getX());
             x += majorGridSize) {
            Point2D start = worldToScreen(x, 0);
            Point2D end = worldToScreen(x, BehaviorTreeModel.WORLD_HEIGHT);

            if (start.getX() >= 0 && start.getX() <= w) {
                gc.strokeLine(start.getX(), Math.max(0, start.getY()),
                        end.getX(), Math.min(h, end.getY()));
            }
        }

        for (int y = Math.max(0, (int)(viewTopLeft.getY() / majorGridSize) * (int)majorGridSize);
             y <= Math.min(BehaviorTreeModel.WORLD_HEIGHT, viewBottomRight.getY());
             y += majorGridSize) {
            Point2D start = worldToScreen(0, y);
            Point2D end = worldToScreen(BehaviorTreeModel.WORLD_WIDTH, y);

            if (start.getY() >= 0 && start.getY() <= h) {
                gc.strokeLine(Math.max(0, start.getX()), start.getY(),
                        Math.min(w, end.getX()), end.getY());
            }
        }

        // Рисуем центральные линии
        gc.setStroke(Color.rgb(80, 80, 80));
        gc.setLineWidth(1);

        Point2D centerV1 = worldToScreen(BehaviorTreeModel.WORLD_WIDTH / 2, 0);
        Point2D centerV2 = worldToScreen(BehaviorTreeModel.WORLD_WIDTH / 2, BehaviorTreeModel.WORLD_HEIGHT);
        if (centerV1.getX() >= 0 && centerV1.getX() <= w) {
            gc.strokeLine(centerV1.getX(), Math.max(0, centerV1.getY()),
                    centerV2.getX(), Math.min(h, centerV2.getY()));
        }

        Point2D centerH1 = worldToScreen(0, BehaviorTreeModel.WORLD_HEIGHT / 2);
        Point2D centerH2 = worldToScreen(BehaviorTreeModel.WORLD_WIDTH, BehaviorTreeModel.WORLD_HEIGHT / 2);
        if (centerH1.getY() >= 0 && centerH1.getY() <= h) {
            gc.strokeLine(Math.max(0, centerH1.getX()), centerH1.getY(),
                    Math.min(w, centerH2.getX()), centerH2.getY());
        }

        // Рисуем рамку мира
        gc.setStroke(Color.rgb(64, 64, 64));
        gc.setLineWidth(2);
        gc.strokeRect(
                Math.max(0, topLeft.getX()),
                Math.max(0, topLeft.getY()),
                Math.min(w, bottomRight.getX()) - Math.max(0, topLeft.getX()),
                Math.min(h, bottomRight.getY()) - Math.max(0, topLeft.getY())
        );
    }

    private void updateAllNodesAndConnections() {
        // Обновляем позиции всех узлов мгновенно
        for (NodeView nodeView : nodeViews.values()) {
            Node node = nodeView.getNode();
            double nodeScale = scaleNodesWithZoom ? zoom.get() : 1.0;
            nodeView.applyScale(nodeScale);
            Point2D screenPos = worldToScreen(node.getX(), node.getY());
            nodeView.updatePosition(screenPos.getX(), screenPos.getY());
        }

        // Затем обновляем все соединения мгновенно
        for (ConnectionView connectionView : connectionViews.values()) {
            // Используем метод для немедленного обновления без анимации
            connectionView.updatePathImmediately();
        }

        // Обновляем временное соединение если оно существует
        if (tempConnectionView != null) {
            tempConnectionView.updatePath();
        }

        // Гарантируем, что контрольные панели всегда сверху
        ensureCorrectZOrder();
    }

    private void updateNodeView(NodeView nodeView) {
        Node node = nodeView.getNode();

        // Применяем масштаб
        double nodeScale = scaleNodesWithZoom ? zoom.get() : 1.0;
        nodeView.applyScale(nodeScale);

        // Обновляем позицию
        Point2D screenPos = worldToScreen(node.getX(), node.getY());
        nodeView.updatePosition(screenPos.getX(), screenPos.getY());
    }

    private void centerWorkspace() {
        if (!model.getNodes().isEmpty()) {
            // Центрируем на первом (корневом) узле
            Node rootNode = model.getNodes().get(0);
            viewX.set(rootNode.getX());
            viewY.set(rootNode.getY());
        } else {
            // Центрируем на центре мира
            viewX.set(BehaviorTreeModel.WORLD_WIDTH / 2.0);
            viewY.set(BehaviorTreeModel.WORLD_HEIGHT / 2.0);
        }

        zoom.set(1.5);
        clampViewport();
        redrawGrid();
        updateAllNodesAndConnections();

        // Гарантируем, что панели управления всегда на переднем плане
        ensureCorrectZOrder();
    }

    private void changeZoom(double factor) {
        double oldZoom = zoom.get();
        zoom.set(zoom.get() * factor);
        clampViewport();
        redrawGrid();
        updateAllNodesAndConnections();
    }

    private void addNewNode() {
        String nodeType = nodeTypeSelector.getValue();
        String description = nodeDescriptionField.getText();
        String parameter = nodeParameterField.getText();

        if (nodeType != null && !description.isEmpty()) {
            // Позиционируем новый узел рядом с текущим видимым центром
            double visibleCenterX = viewX.get();
            double visibleCenterY = viewY.get();

            // Добавляем случайное смещение
            double offsetY = (Math.random() - 0.5) * 300;
            double offsetX = (Math.random() - 0.5) * 300;

            double newX = visibleCenterX + offsetX;
            double newY = visibleCenterY + offsetY;

            // Ограничиваем в пределах мира
            newX = Math.max(20, Math.min(BehaviorTreeModel.WORLD_WIDTH - 280, newX));
            newY = Math.max(20, Math.min(BehaviorTreeModel.WORLD_HEIGHT - 180, newY));

            // Создаем новый узел
            Node newNode = new Node(nodeType, description, parameter);
            newNode.setX(newX);
            newNode.setY(newY);
            newNode.setExpanded(true);

            // Добавляем в модель и регистрируем операцию для Undo/Redo
            model.addNode(newNode);
            undoRedoManager.registerOperation(
                    () -> model.removeNode(newNode),  // Undo
                    () -> model.addNode(newNode)      // Redo
            );

            // Создаем представление узла
            drawNode(newNode);

            // Обновляем все узлы и соединения
            updateAllNodesAndConnections();

            // Сбрасываем поля ввода
            nodeDescriptionField.clear();
            nodeParameterField.clear();

            // Показываем уведомление
            GUIUtils.showNotification(nodePane, "Added node " + nodeType + ": " + description);

            // Гарантируем, что панели управления всегда на переднем плане
            ensureCorrectZOrder();
        } else {
            GUIUtils.showNotification(nodePane, "Please select node type and enter description");
        }
    }

    // Модифицируем метод drawNode() в BehaviorTreeEditor.java

    private void drawNode(Node node) {
        // Создаем представление узла
        NodeView nodeView = new NodeView(node);
        nodeViews.put(node, nodeView);

        // Обновляем список анимаций в узле (если это узел с анимацией)
        updateNodeAnimationsList(nodeView);

        // Настраиваем обработчики для узла
        setupNodeViewHandlers(nodeView);

        // Добавляем на панель
        nodePane.getChildren().add(nodeView);

        // Обновляем позицию и масштаб
        updateNodeView(nodeView);
    }

    // Добавляем новый метод для обновления списка анимаций в узле
    private void updateNodeAnimationsList(NodeView nodeView) {
        if (nodeView.getNode().getType().equals("PlayAnimationNode") ||
                nodeView.getNode().getType().equals("AttackNode")) {

            // Ищем StatsEditorPanel
            StatsEditorPanel statsPanel = findStatsEditorPanel();

            if (statsPanel != null) {
                // Обновляем список анимаций в узле
                nodeView.updateAnimationsList(availableAnimations);
                nodeView.updateAnimationsList(statsPanel.getAvailableAnimationsList());
            }
        }
    }

    private StatsEditorPanel findStatsEditorPanel() {
        // Пытаемся найти StatsEditorPanel в иерархии компонентов

        if (getScene() == null) {
            return null;
        }

        // Ищем корневой элемент (обычно BorderPane)
        javafx.scene.Parent root = getScene().getRoot();

        // Если это TabPane, проверяем его вкладки
        if (root instanceof TabPane) {
            TabPane tabPane = (TabPane) root;
            for (Tab tab : tabPane.getTabs()) {
                if (tab.getContent() instanceof StatsEditorPanel) {
                    return (StatsEditorPanel) tab.getContent();
                }
            }
        }

        // Иначе ищем в иерархии компонентов
        return findStatsEditorPanelInNode(root);
    }

    private List<String> availableAnimations = new ArrayList<>();

    public void updateAnimationsList(List<String> animations) {
        // Сохраняем список анимаций
        availableAnimations = new ArrayList<>(animations);

        // Обновляем все существующие узлы, связанные с анимациями
        for (NodeView nodeView : nodeViews.values()) {
            Node node = nodeView.getNode();
            if (node.getType().equals("PlayAnimationNode") ||
                    node.getType().equals("AttackNode")) {
                nodeView.updateAnimationsList(animations);
            }
        }

        System.out.println("BehaviorTreeEditor: Updated animations list with " + animations.size() + " animations");
    }

    private StatsEditorPanel findStatsEditorPanelInNode(javafx.scene.Node node) {
        if (node instanceof StatsEditorPanel) {
            return (StatsEditorPanel) node;
        }

        if (node instanceof Parent) {
            Parent parent = (Parent) node;

            // Рекурсивно проверяем все дочерние элементы
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                StatsEditorPanel result = findStatsEditorPanelInNode(child);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private void setupNodeViewHandlers(NodeView nodeView) {
        // Выделение узла
        nodeView.setOnNodeSelected(node -> {
            if (selectedNode != null && selectedNode != node) {
                NodeView oldView = nodeViews.get(selectedNode);
                if (oldView != null) {
                    oldView.setSelected(false);
                }
            }
            selectedNode = node;
            nodeView.setSelected(true);
        });

        // Перемещение узла
        nodeView.setOnNodeDragged(view -> {
            // Обновляем все соединения, связанные с этим узлом
            updateConnectionsForNode(view.getNode());

            // Гарантируем, что панели управления всегда на переднем плане
            ensureCorrectZOrder();
        });

        // Окончание перемещения узла
        nodeView.setOnNodeReleased(view -> {
            // Обновляем все соединения
            updateAllNodesAndConnections();
        });

        // Удаление узла
        nodeView.setOnNodeDeleted(() -> {
            deleteNode(nodeView.getNode());
        });

        // Начало создания соединения
        nodeView.setOnConnectionStarted((view, pinType) -> {
            startConnectionDrag(view, pinType);
        });
    }

    private void updateConnectionsForNode(Node node) {
        // Обновляем все связи с этим узлом
        for (Connection connection : model.getConnections()) {
            if (connection.getSourceNode().equals(node) || connection.getTargetNode().equals(node)) {
                ConnectionView view = connectionViews.get(connection);
                if (view != null) {
                    view.updatePath();
                }
            }
        }
    }

    private void startConnectionDrag(NodeView sourceView, String pinType) {
        if (sourceView != null) {
            // Явная проверка, что это выходной пин
            if ("out".equals(pinType)) {
                sourceNodeView = sourceView;
                connectionSourceType = pinType;

                // Создаем временное соединение из выходного пина
                tempConnectionView = new TemporaryConnectionView(sourceView);

                // Устанавливаем обработчик отмены по правому клику
                tempConnectionView.setOnCancelled(() -> {
                    cleanupTemporaryConnection();
                });

                // Добавляем временное соединение в панель соединений
                connectionPane.getChildren().add(tempConnectionView);

                // Важно! Выводим временное соединение на передний план
                tempConnectionView.toFront();

                // Не выводим уведомление - оно может отвлекать и замедлять
                // GUIUtils.showNotification(nodePane, "Hold Shift and drag to input pin");

                // Инициируем немедленное обновление пути, чтобы избежать скачка
                Platform.runLater(() -> {
                    if (tempConnectionView != null) {
                        // Получаем текущие координаты мыши относительно connectionPane
                        double mouseX = 0, mouseY = 0;
                        try {
                            java.awt.Point mousePoint = java.awt.MouseInfo.getPointerInfo().getLocation();
                            // Конвертируем координаты экрана в координаты сцены
                            Point2D scenePoint = connectionPane.screenToLocal(mousePoint.getX(), mousePoint.getY());
                            if (scenePoint != null) {
                                tempConnectionView.updateEndPoint(scenePoint);
                            }
                        } catch (Exception e) {
                            // В случае ошибки, не делаем ничего - соединение обновится при первом движении
                            System.out.println("Couldn't get initial mouse position: " + e.getMessage());
                        }
                    }
                });
            } else {
                // Если пытаются начать соединение с входного пина
                GUIUtils.showNotification(nodePane, "Connections must start from output pins");
            }
        }
    }

    private void highlightPotentialTargets(double sceneX, double sceneY) {
        // Снимаем выделение со всех потенциальных целей
        for (NodeView view : nodeViews.values()) {
            view.highlightInputPin(false);

            // Возвращаем нормальный цвет портам
            if (view.getInputPin().getFill() == Color.RED) {
                view.getInputPin().setFill(Color.rgb(100, 100, 100));
            }
        }

        targetNodeView = null;

        // Находим узел под курсором
        for (NodeView view : nodeViews.values()) {
            if (view != sourceNodeView) {
                Point2D inputPos = view.localToScene(view.getInputPinCenterPosition());
                if (inputPos.distance(sceneX, sceneY) < 20) {
                    // Проверяем возможность создания соединения
                    boolean canConnect = !model.connectionExists(sourceNodeView.getNode(), view.getNode()) &&
                            !view.hasInputConnection(); // Проверяем что порт свободен

                    if (canConnect) {
                        view.highlightInputPin(true);
                        targetNodeView = view;
                    } else {
                        // Показываем что соединение невозможно
                        view.getInputPin().setFill(Color.RED);
                    }

                    break;
                }
            }
        }
    }

    private void tryCreateConnection(Node source, Node target) {
        if (source != target && !model.connectionExists(source, target) && !nodeViews.get(target).hasInputConnection()) {
            // Создаем соединение
            Connection connection = new Connection(source, target);
            model.addConnection(connection);

            // Регистрируем операцию для Undo/Redo
            undoRedoManager.registerOperation(
                    () -> model.removeConnection(connection),  // Undo
                    () -> model.addConnection(connection)     // Redo
            );

            // Создаем представление соединения
            createConnectionView(connection);

            // Обновляем все соединения
            updateAllNodesAndConnections();

            GUIUtils.showNotification(nodePane, "Connection created");
        } else {
            GUIUtils.showNotification(nodePane, "Cannot create connection");
        }
    }

    private void createConnectionView(Connection connection) {
        NodeView sourceView = nodeViews.get(connection.getSourceNode());
        NodeView targetView = nodeViews.get(connection.getTargetNode());

        if (sourceView != null && targetView != null) {
            ConnectionView connectionView = new ConnectionView(connection, sourceView, targetView);

            // Настраиваем обработчик удаления
            connectionView.setOnConnectionDeleted(() -> {
                deleteConnection(connection);
            });

            // Добавляем обработчик удаления по правому клику
            setupConnectionViewHandlers(connectionView);

            // Добавляем на панель соединений
            connectionPane.getChildren().add(connectionView);
            connectionViews.put(connection, connectionView);
        }
    }

    private void setupConnectionViewHandlers(ConnectionView connectionView) {
        connectionView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                deleteConnection(connectionView.getConnection());
                event.consume();
            }
        });
    }

    private void cleanupTemporaryConnection() {
        if (tempConnectionView != null) {
            connectionPane.getChildren().remove(tempConnectionView);
            tempConnectionView = null;
        }

        // Снимаем выделение со всех потенциальных целей
        for (NodeView view : nodeViews.values()) {
            view.highlightInputPin(false);

            // Возвращаем нормальный цвет портам
            if (view.getInputPin().getFill() == Color.RED) {
                view.getInputPin().setFill(Color.rgb(100, 100, 100));
            }
        }

        sourceNodeView = null;
        connectionSourceType = null;
        targetNodeView = null;
    }

    private void deleteNode(Node node) {
        // Запоминаем все соединения, связанные с узлом
        List<Connection> nodeConnections = new ArrayList<>();
        for (Connection connection : model.getConnections()) {
            if (connection.getSourceNode().equals(node) || connection.getTargetNode().equals(node)) {
                nodeConnections.add(connection);
            }
        }

        // Удаляем соединения из представления
        for (Connection connection : nodeConnections) {
            ConnectionView view = connectionViews.remove(connection);
            if (view != null) {
                // Освобождаем порты
                view.dispose();
                connectionPane.getChildren().remove(view);
            }
        }

        // Удаляем представление узла
        NodeView view = nodeViews.remove(node);
        if (view != null) {
            nodePane.getChildren().remove(view);
        }

        // Если это был выделенный узел, снимаем выделение
        if (node.equals(selectedNode)) {
            selectedNode = null;
        }

        // Удаляем узел из модели
        model.removeNode(node);

        // Обновляем все соединения
        updateAllNodesAndConnections();

        GUIUtils.showNotification(nodePane, "Node deleted");
    }

    private void deleteConnection(Connection connection) {
        // Удаляем представление соединения
        ConnectionView view = connectionViews.remove(connection);
        if (view != null) {
            // Освобождаем порты
            view.dispose();
            connectionPane.getChildren().remove(view);
        }

        // Удаляем соединение из модели
        model.removeConnection(connection);

        GUIUtils.showNotification(nodePane, "Connection deleted");
    }

    private void applyAutoLayout() {
        // Применяем автоматическую компоновку
        model.applyAutoLayout();

        // Обновляем все узлы и соединения
        updateAllNodesAndConnections();

        GUIUtils.showNotification(nodePane, "Auto Layout applied");
    }

    private void exportBehaviorTree() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Behavior Tree");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
            File file = fileChooser.showSaveDialog(getScene().getWindow());

            if (file != null) {
                model.saveToFile(file);
                GUIUtils.showNotification(nodePane, "Behavior tree exported successfully!");
            }
        } catch (Exception e) {
            GUIUtils.showAlert("Export Error", "Failed to export behavior tree: " + e.getMessage());
        }
    }

    private void importBehaviorTree() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Load Behavior Tree");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
            File file = fileChooser.showOpenDialog(getScene().getWindow());

            if (file != null) {
                // Очищаем текущие представления
                clearNodeViews();

                // Загружаем из файла
                model.loadFromFile(file);

                // Создаем представления для загруженных узлов и соединений
                for (Node node : model.getNodes()) {
                    drawNode(node);
                }

                for (Connection connection : model.getConnections()) {
                    createConnectionView(connection);
                }

                updateAllNodesAndConnections();
                centerWorkspace();

                GUIUtils.showNotification(nodePane, "Behavior tree imported successfully!");
            }
        } catch (Exception e) {
            GUIUtils.showAlert("Import Error", "Failed to import behavior tree: " + e.getMessage());
        }
    }

    /**
     * Полностью очищает редактор дерева поведения
     */
    public void clearAll() {
        // Очищаем представления узлов и соединений
        clearNodeViews();

        // Сбрасываем модель
        model = new BehaviorTreeModel();

        // Создаем базовый корневой узел
        Node rootNode = model.createRootNode();
        drawNode(rootNode);

        // Центрируем рабочую область
        centerWorkspace();

        // Обновляем панель управления
        Platform.runLater(() -> {
            // Обновляем элементы интерфейса, если необходимо
            updateAllNodesAndConnections();
            ensureCorrectZOrder();
        });
    }


    private void clearNodeViews() {
        // Удаляем все представления соединений
        for (ConnectionView view : connectionViews.values()) {
            connectionPane.getChildren().remove(view);
        }
        connectionViews.clear();

        // Удаляем все представления узлов
        for (NodeView view : nodeViews.values()) {
            nodePane.getChildren().remove(view);
        }
        nodeViews.clear();

        // Сбрасываем выделение
        selectedNode = null;
    }

    public void cleanup() {
        // Освобождаем ресурсы
        clearNodeViews();
        model = null;
    }

    public BehaviorTreeModel getModel() {
        return this.model;
    }
}