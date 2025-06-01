package com.custommobsforge.custommobsforge.client.gui;

import javafx.scene.paint.Color;
import java.util.HashMap;
import java.util.Map;

public class Constants {
    // Цвета темы
    public static final Color BG_COLOR = Color.rgb(57, 57, 57); // #393939
    public static final Color GRID_COLOR = Color.rgb(72, 72, 72); // #484848
    public static final Color GRID_MAJOR_COLOR = Color.rgb(88, 88, 88); // #585858
    public static final Color NODE_COLOR = Color.rgb(90, 90, 90, 0.9); // rgba(90, 90, 90, 0.9)
    public static final Color NODE_SELECTED_COLOR = Color.rgb(67, 128, 208); // #4380D0
    public static final Color CONNECTION_COLOR = Color.rgb(200, 200, 200); // #C8C8C8
    public static final Color TEXT_COLOR = Color.rgb(224, 224, 224); // #E0E0E0
    public static final Color BORDER_COLOR = Color.rgb(43, 43, 43); // #2B2B2B
    public static final Color INPUT_BG_COLOR = Color.rgb(115, 115, 115); // #737373
    public static final Color INPUT_TEXT_COLOR = Color.rgb(25, 25, 25); // #191919

    // Константы для анимаций
    public static final int ANIMATION_DURATION_SHORT = 150;
    public static final int ANIMATION_DURATION_MEDIUM = 300;
    public static final int NOTIFICATION_DURATION = 2000;

    // Константы для стилей
    public static final String NODE_VIEW_STYLE_CLASS = "node-view";
    public static final String NODE_HEADER_STYLE_CLASS = "node-header";
    public static final String NODE_TITLE_STYLE_CLASS = "node-title";
    public static final String NODE_CONTENT_STYLE_CLASS = "node-content";
    public static final String PIN_STYLE_CLASS = "pin";
    public static final String PIN_CONNECTED_STYLE_CLASS = "pin-connected";
    public static final String PIN_HIGHLIGHTED_STYLE_CLASS = "pin-highlighted";
    public static final String CONNECTION_PATH_STYLE_CLASS = "connection-path";
    public static final String CONNECTION_PATH_SELECTED_STYLE_CLASS = "connection-path-selected";
    public static final String TEMP_CONNECTION_STYLE_CLASS = "temp-connection";
    public static final String PARAMETER_FIELD_STYLE_CLASS = "parameter-field";
    public static final String PARAMETER_LABEL_STYLE_CLASS = "parameter-label";
    public static final String NOTIFICATION_STYLE_CLASS = "notification";
    public static final String CONTROL_PANEL_STYLE_CLASS = "control-panel";
    public static final String CONTROL_BUTTON_STYLE_CLASS = "control-button";
    public static final String ADD_BUTTON_STYLE_CLASS = "add-button";
    public static final String INPUT_FIELD_STYLE_CLASS = "input-field";
}