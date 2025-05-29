package com.custommobsforge.custommobsforge.client.gui;

import com.custommobsforge.custommobsforge.client.gui.Connection;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Point2D;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.Duration;

public class ConnectionView extends Path {
    private final Connection connection;
    private final NodeView sourceView;
    private final NodeView targetView;
    private boolean isSelected = false;
    private Timeline updateAnimation;
    private boolean isBeingEdited = false;

    private Runnable onConnectionDeleted;

    public ConnectionView(Connection connection, NodeView sourceView, NodeView targetView) {
        this.connection = connection;
        this.sourceView = sourceView;
        this.targetView = targetView;

        // Настройка внешнего вида
        setStroke(Color.rgb(200, 200, 200));
        setStrokeWidth(2.5);
        setStrokeLineCap(StrokeLineCap.ROUND);
        setFill(null);

        // Инициализация анимации обновления пути
        updateAnimation = new Timeline();

        // Обновление пути при создании
        createInitialPath();

        // Настройка обработчиков событий
        setupEventHandlers();

        // Устанавливаем соединение между узлами
        sourceView.addOutputConnection(this);
        targetView.addInputConnection(this);
    }

    public void createInitialPath() {
        // Получаем позиции пинов
        Point2D startPos = getSourcePinPosition();
        Point2D endPos = getTargetPinPosition();

        // Очищаем элементы пути
        getElements().clear();

        // Создаем путь
        getElements().add(new MoveTo(startPos.getX(), startPos.getY()));

        // Создаем кривую Безье
        double midX = (startPos.getX() + endPos.getX()) / 2;
        double controlX1 = midX;
        double controlX2 = midX;
        double controlY1 = startPos.getY();
        double controlY2 = endPos.getY();

        getElements().add(new CubicCurveTo(
                controlX1, controlY1,
                controlX2, controlY2,
                endPos.getX(), endPos.getY()
        ));
    }

    public void updatePath() {
        if (isBeingEdited) return; // Не обновляем, если линия редактируется

        Point2D startPos = getSourcePinPosition();
        Point2D endPos = getTargetPinPosition();

        // Если анимация уже запущена, останавливаем её
        updateAnimation.stop();

        // Если путь еще не создан, создаем его сразу
        if (getElements().isEmpty()) {
            createInitialPath();
            return;
        }

        try {
            MoveTo moveTo = (MoveTo) getElements().get(0);
            CubicCurveTo cubicCurve = (CubicCurveTo) getElements().get(1);

            // Для более быстрого обновления уменьшаем длительность анимации
            moveTo.setX(startPos.getX());
            moveTo.setY(startPos.getY());
            cubicCurve.setX(endPos.getX());
            cubicCurve.setY(endPos.getY());

            // Обновляем контрольные точки
            double midX = (startPos.getX() + endPos.getX()) / 2;
            cubicCurve.setControlX1(midX);
            cubicCurve.setControlY1(startPos.getY());
            cubicCurve.setControlX2(midX);
            cubicCurve.setControlY2(endPos.getY());

        } catch (Exception e) {
            // Если что-то пошло не так, пересоздаем путь
            createInitialPath();
        }
    }

    // Оба метода получения позиций пинов
    private Point2D getSourcePinPosition() {
        // Получаем точную позицию центра ВЫХОДНОГО пина
        Circle outputPin = sourceView.getOutputPin();
        Point2D pinSceneCoords = outputPin.localToScene(
                outputPin.getBoundsInLocal().getCenterX(),
                outputPin.getBoundsInLocal().getCenterY()
        );

        if (getParent() != null) {
            return getParent().sceneToLocal(pinSceneCoords);
        }
        return new Point2D(
                outputPin.getBoundsInLocal().getCenterX(),
                outputPin.getBoundsInLocal().getCenterY()
        );
    }

    private Point2D getTargetPinPosition() {
        // Получаем точную позицию центра ВХОДНОГО пина
        Circle inputPin = targetView.getInputPin();
        Point2D pinSceneCoords = inputPin.localToScene(
                inputPin.getBoundsInLocal().getCenterX(),
                inputPin.getBoundsInLocal().getCenterY()
        );

        if (getParent() != null) {
            return getParent().sceneToLocal(pinSceneCoords);
        }
        return new Point2D(
                inputPin.getBoundsInLocal().getCenterX(),
                inputPin.getBoundsInLocal().getCenterY()
        );
    }

    // Метод для немедленного обновления пути без анимации
    public void updatePathImmediately() {
        // Устанавливаем флаг, чтобы предотвратить повторные обновления
        isBeingEdited = true;

        // Получаем позиции сразу
        Point2D startPos = getSourcePinPosition();
        Point2D endPos = getTargetPinPosition();

        // Создаем новые элементы пути
        getElements().clear();
        getElements().add(new MoveTo(startPos.getX(), startPos.getY()));

        double midX = (startPos.getX() + endPos.getX()) / 2;
        getElements().add(new CubicCurveTo(
                midX, startPos.getY(),
                midX, endPos.getY(),
                endPos.getX(), endPos.getY()
        ));

        // Снимаем флаг
        isBeingEdited = false;
    }

    private void setupEventHandlers() {
        // Выделение при наведении
        setOnMouseEntered(e -> {
            if (!isSelected) {
                setStroke(Color.WHITE);
                setStrokeWidth(3);
            }
        });

        setOnMouseExited(e -> {
            if (!isSelected) {
                setStroke(Color.rgb(200, 200, 200));
                setStrokeWidth(2.5);
            }
        });

        // Улучшенное удаление по правому клику
        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                if (onConnectionDeleted != null) {
                    onConnectionDeleted.run();
                }
                e.consume();
            }
        });
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
        if (selected) {
            setStroke(Color.rgb(100, 200, 255));
            setStrokeWidth(3.5);
            setEffect(new DropShadow(5, Color.rgb(100, 200, 255, 0.7)));
        } else {
            setStroke(Color.rgb(200, 200, 200));
            setStrokeWidth(2.5);
            setEffect(null);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public NodeView getSourceView() {
        return sourceView;
    }

    public NodeView getTargetView() {
        return targetView;
    }

    public void setOnConnectionDeleted(Runnable handler) {
        onConnectionDeleted = handler;
    }

    // Геттер для доступа к обработчику удаления
    public Runnable getOnConnectionDeleted() {
        return onConnectionDeleted;
    }

    public void highlightConnectedPins(boolean highlight) {
        // Подсвечиваем пины, связанные с этим соединением
        sourceView.highlightOutputPin(highlight);
        targetView.highlightInputPin(highlight);
    }

    public void dispose() {
        // Освобождаем порты при удалении соединения
        sourceView.removeOutputConnection(this);
        targetView.removeInputConnection(this);
    }

    public void setBeingEdited(boolean editing) {
        this.isBeingEdited = editing;
    }
}