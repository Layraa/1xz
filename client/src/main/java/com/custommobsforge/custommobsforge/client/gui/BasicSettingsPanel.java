package com.custommobsforge.custommobsforge.client.gui;

import com.custommobsforge.custommobsforge.client.gui.GUIUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class BasicSettingsPanel extends VBox {

    public BasicSettingsPanel() {
        setPadding(new Insets(20));
        setSpacing(15);
        getStyleClass().add("mob-settings");

        Label headerLabel = new Label("Mob Creation");
        headerLabel.getStyleClass().add("header-label");
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 18px;");

        TextField mobNameField = new TextField();
        mobNameField.setPromptText("Mob Name");
        mobNameField.getStyleClass().add(Constants.INPUT_FIELD_STYLE_CLASS);

        ComboBox<String> modelSelector = new ComboBox<>();
        modelSelector.getItems().addAll("Model1", "Model2", "Model3");
        modelSelector.setPromptText("Select Model");
        modelSelector.getStyleClass().add(Constants.INPUT_FIELD_STYLE_CLASS);

        Button applyMobButton = new Button("Apply Mob Settings");
        applyMobButton.getStyleClass().add(Constants.ADD_BUTTON_STYLE_CLASS);
        applyMobButton.setOnAction(e -> {
            String name = mobNameField.getText();
            String model = modelSelector.getValue();
            if (!name.isEmpty() && model != null) {
                GUIUtils.showAlert("Success", "Mob created: " + name + " with " + model);
            } else {
                GUIUtils.showAlert("Error", "Please fill all fields.");
            }
        });

        Label nameLabel = new Label("Mob Name:");
        Label modelLabel = new Label("Model:");

        getChildren().addAll(
                headerLabel,
                nameLabel,
                mobNameField,
                modelLabel,
                modelSelector,
                applyMobButton
        );
    }
}