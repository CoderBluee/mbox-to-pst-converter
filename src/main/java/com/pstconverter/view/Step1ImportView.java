package com.pstconverter.view;

import com.pstconverter.controller.MainController;
import com.pstconverter.core.model.SourceFileModel;
import com.pstconverter.util.SettingsManager;
import com.pstconverter.core.adapter.SourceAdapterFactory;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class Step1ImportView extends VBox {

    private final MainController controller;
    private final TableView<SourceFileModel> tableView;
    private Label lblTableHeader;
    private Label lblSelectionCount;
    private Label lblTableFooterStatus;
    private javafx.scene.layout.VBox recentPane;
    private javafx.scene.layout.VBox recentBox;
    private javafx.scene.layout.FlowPane incompletePane;
    private javafx.scene.layout.VBox incompleteBox;
    private javafx.scene.layout.FlowPane historyPane;
    private javafx.scene.layout.VBox historyBox;

    public Step1ImportView(MainController controller) {
        super(10);
        this.controller = controller;
        this.tableView = new TableView<>();
        initializeUI();
        refreshRecentMailboxes();
    }

    private void initializeUI() {
        // Controls (Left sidebar concept, using HBox for layout structure)
        HBox mainContent = new HBox(20);
        VBox.setVgrow(mainContent, Priority.ALWAYS);

        // LEFT Side: Menu Button Group styled as a card
        VBox controlBox = new VBox(20);
        controlBox.setPadding(new Insets(20));
        controlBox.setPrefWidth(250); // Optimized width to prevent button text cutting
        controlBox.setMinWidth(250);  // Prevent sidebar shrinkage/clipping on window resize
        controlBox.setAlignment(Pos.TOP_CENTER);
        controlBox.getStyleClass().add("sidebar-card");

        boolean isDark = controller.isDarkMode();

        // Group 1: Import Source
        VBox importBox = new VBox(10);
        importBox.setAlignment(Pos.TOP_LEFT);
        Label lblImport = new Label("1. IMPORT MAILBOX FILES");
        lblImport.getStyleClass().add("section-label");

        MFXButton btnAddFile = new MFXButton(" Select File(s)");
        btnAddFile.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.ADD, 16));
        btnAddFile.getStyleClass().addAll("action-btn", "btn-primary");
        btnAddFile.setMaxWidth(Double.MAX_VALUE); // Expand to fill card width
        btnAddFile.setOnAction(e -> handleAddFiles());

        MFXButton btnAddFolder = new MFXButton(" Select Folder");
        btnAddFolder.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.FOLDER_OPEN, 16));
        btnAddFolder.getStyleClass().addAll("action-btn", "btn-secondary");
        btnAddFolder.setMaxWidth(Double.MAX_VALUE); // Expand to fill card width
        btnAddFolder.setOnAction(e -> handleAddFolder());

        importBox.getChildren().addAll(lblImport, btnAddFile, btnAddFolder);

        // Group 2: Manage Queue
        VBox queueBox = new VBox(10);
        queueBox.setAlignment(Pos.TOP_LEFT);
        Label lblQueue = new Label("2. MANAGE SELECTIONS");
        lblQueue.getStyleClass().add("section-label");

        MFXButton btnRemove = new MFXButton(" Remove Selected");
        btnRemove.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.DELETE, 16));
        btnRemove.getStyleClass().addAll("action-btn", "btn-danger");
        btnRemove.setMaxWidth(Double.MAX_VALUE); // Expand to fill card width
        btnRemove.setOnAction(e -> handleRemoveSelected());

        MFXButton btnRemoveAll = new MFXButton(" Clear All");
        btnRemoveAll.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.CLEAR, 16));
        btnRemoveAll.getStyleClass().addAll("action-btn", "btn-danger-outline");
        btnRemoveAll.setMaxWidth(Double.MAX_VALUE); // Expand to fill card width
        btnRemoveAll.setOnAction(e -> handleRemoveAll());

        queueBox.getChildren().addAll(lblQueue, btnRemove, btnRemoveAll);

        // Group 3: Special Feature Auto Detect
        VBox detectBox = new VBox(10);
        detectBox.setAlignment(Pos.TOP_LEFT);
        Label lblDetect = new Label("⚡ 1-CLICK CLIENT DETECT");
        lblDetect.getStyleClass().add("section-label");

        MFXButton btnAutoDetect = new MFXButton(" Auto Detect Profiles");
        btnAutoDetect.getStyleClass().addAll("action-btn");
        btnAutoDetect.setMaxWidth(Double.MAX_VALUE); // Expand to fill card width
        btnAutoDetect.setStyle("-fx-background-color: linear-gradient(to right, #6366f1, #06b6d4); " +
                               "-fx-text-fill: white; " +
                               "-fx-font-weight: bold; " +
                               "-fx-padding: 9px 16px; " +
                               "-fx-background-radius: 8px; " +
                               "-fx-border-radius: 8px; " +
                               "-fx-cursor: hand;");
        btnAutoDetect.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.TUNE, 16));
        btnAutoDetect.setOnAction(e -> controller.handleAutoDetect());

        // Apply a glowing drop shadow effect to highlight it as a special feature
        javafx.scene.effect.DropShadow glow = new javafx.scene.effect.DropShadow();
        glow.setColor(javafx.scene.paint.Color.web("#6366f1", 0.5));
        glow.setRadius(10);
        glow.setSpread(0.2);
        btnAutoDetect.setEffect(glow);

        detectBox.getChildren().addAll(lblDetect, btnAutoDetect);

        recentBox = new VBox(8);
        recentBox.setAlignment(Pos.TOP_LEFT);
        recentBox.setPadding(new Insets(10, 0, 0, 0));
        Label lblRecent = new Label("RECENT MAILBOXES");
        lblRecent.getStyleClass().add("section-label");
        lblRecent.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #6366f1;");
        
        recentPane = new VBox(6);
        recentPane.setAlignment(Pos.TOP_LEFT);
        
        ScrollPane sidebarRecentScroll = new ScrollPane(recentPane);
        sidebarRecentScroll.setFitToWidth(true);
        sidebarRecentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarRecentScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sidebarRecentScroll.setPrefViewportHeight(200);
        sidebarRecentScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0; -fx-viewport-background: transparent;");
        
        recentBox.getChildren().addAll(lblRecent, sidebarRecentScroll);

        controlBox.getChildren().addAll(importBox, new Separator(), queueBox, new Separator(), detectBox, new Separator(), recentBox);

        // CENTER: Main Table
        setupTable();
        setupDragAndDrop();
        VBox tableContainer = new VBox(10);

        incompleteBox = new VBox(8);
        incompleteBox.setPadding(new Insets(10, 0, 0, 0));
        incompletePane = new FlowPane(10, 10);
        incompletePane.setAlignment(Pos.CENTER_LEFT);
        incompleteBox.getChildren().addAll(incompletePane);

        historyBox = new VBox(8);
        historyBox.setPadding(new Insets(10, 0, 0, 0));
        historyPane = new FlowPane(10, 10);
        historyPane.setAlignment(Pos.CENTER_LEFT);
        historyBox.getChildren().addAll(historyPane);
        
        TabPane dashboardTabs = new TabPane();
        dashboardTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        dashboardTabs.getStyleClass().add("dashboard-tabs");
        
        ScrollPane incompleteScroll = new ScrollPane(incompleteBox);
        incompleteScroll.setFitToWidth(true);
        incompleteScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        Tab tabIncomplete = new Tab("Resume Migrations", incompleteScroll);
        
        ScrollPane historyScroll = new ScrollPane(historyBox);
        historyScroll.setFitToWidth(true);
        historyScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        Tab tabHistory = new Tab("Conversion History", historyScroll);
        
        dashboardTabs.getTabs().addAll(tabIncomplete, tabHistory);
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        
        HBox tableHeaderBar = new HBox(10);
        tableHeaderBar.setAlignment(Pos.CENTER_LEFT);

        lblTableHeader = new Label("Imported Mailbox Files List:");
        lblTableHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + (controller.isDarkMode() ? "#f8fafc" : "#1e293b") + ";");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        lblSelectionCount = new Label("0 files selected");
        lblSelectionCount.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.CHECK_BOX, "-fx-text-fill: #10b981; -fx-font-size: 13px;"));

        tableHeaderBar.getChildren().addAll(lblTableHeader, headerSpacer, lblSelectionCount);

        HBox tableFooterBar = new HBox(10);
        tableFooterBar.setAlignment(Pos.CENTER_LEFT);
        tableFooterBar.setPadding(new Insets(2, 0, 0, 0));

        lblTableFooterStatus = new Label("No mailbox files imported.");
        lblTableFooterStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (controller.isDarkMode() ? "#cbd5e1" : "#64748b") + ";");
        tableFooterBar.getChildren().add(lblTableFooterStatus);

        VBox tableBox = new VBox(8);
        tableBox.getChildren().addAll(tableHeaderBar, tableView, tableFooterBar);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        tableBox.setPadding(new Insets(0, 0, 10, 0));
        
        dashboardTabs.setPrefHeight(250);
        dashboardTabs.setMinHeight(150);
        
        splitPane.getItems().addAll(tableBox, dashboardTabs);
        splitPane.setDividerPositions(0.65); // Give more space to table by default
        
        tableContainer.getChildren().add(splitPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        HBox.setHgrow(tableContainer, Priority.ALWAYS);

        mainContent.getChildren().addAll(controlBox, tableContainer);
        this.getChildren().add(mainContent);

        updateSelectionCount();
    }

    @SuppressWarnings("unchecked")
    private void setupTable() {
        tableView.setEditable(false);
        tableView.getStyleClass().add("mailbox-table");
        tableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        TableColumn<SourceFileModel, String> snoCol = new TableColumn<>("S. No.");
        snoCol.setPrefWidth(55);
        snoCol.setMinWidth(45);
        snoCol.setMaxWidth(65);
        snoCol.setResizable(false);
        snoCol.setStyle("-fx-alignment: CENTER;");

        snoCol.setCellFactory(column -> new TableCell<SourceFileModel, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.valueOf(getIndex() + 1));
                    setStyle("-fx-alignment: CENTER; -fx-font-weight: bold; -fx-text-fill: " + (controller.isDarkMode() ? "#94a3b8" : "#64748b") + ";");
                }
            }
        });

        TableColumn<SourceFileModel, String> nameCol = new TableColumn<>("File Name");
        nameCol.setCellValueFactory(cellData -> cellData.getValue().fileNameProperty());
        nameCol.setPrefWidth(220);
        nameCol.setMinWidth(150);

        TableColumn<SourceFileModel, String> pathCol = new TableColumn<>("File Path");
        pathCol.setCellValueFactory(cellData -> cellData.getValue().filePathProperty());
        pathCol.setPrefWidth(650);
        pathCol.setMinWidth(300);

        pathCol.setCellFactory(column -> new TableCell<SourceFileModel, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    Tooltip tooltip = new Tooltip(item);
                    tooltip.setShowDelay(javafx.util.Duration.millis(200));
                    setTooltip(tooltip);
                }
            }
        });

        TableColumn<SourceFileModel, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(cellData -> cellData.getValue().fileSizeProperty());
        sizeCol.setPrefWidth(100);
        sizeCol.setMinWidth(80);

        TableColumn<SourceFileModel, String> statusCol = new TableColumn<>("Validation Status");
        statusCol.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        statusCol.setPrefWidth(140);
        statusCol.setMinWidth(100);

        statusCol.setCellFactory(column -> new TableCell<SourceFileModel, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    boolean isValid = item.equalsIgnoreCase("Valid");
                    Label badge = new Label(isValid ? "Valid" : "Invalid");
                    boolean isDark = controller.isDarkMode();
                    if (isValid) {
                        badge.setStyle(
                            "-fx-font-size: 11px; -fx-font-weight: bold; " +
                            "-fx-text-fill: " + (isDark ? "#34d399" : "#059669") + "; " +
                            "-fx-background-color: " + (isDark ? "rgba(16, 185, 129, 0.15)" : "rgba(16, 185, 129, 0.1)") + "; " +
                            "-fx-padding: 3px 10px; -fx-background-radius: 12px; " +
                            "-fx-border-color: " + (isDark ? "rgba(16, 185, 129, 0.3)" : "rgba(16, 185, 129, 0.25)") + "; -fx-border-radius: 12px; -fx-border-width: 1px;"
                        );
                    } else {
                        badge.setStyle(
                            "-fx-font-size: 11px; -fx-font-weight: bold; " +
                            "-fx-text-fill: " + (isDark ? "#f87171" : "#dc2626") + "; " +
                            "-fx-background-color: " + (isDark ? "rgba(239, 68, 68, 0.15)" : "rgba(239, 68, 68, 0.1)") + "; " +
                            "-fx-padding: 3px 10px; -fx-background-radius: 12px; " +
                            "-fx-border-color: " + (isDark ? "rgba(239, 68, 68, 0.3)" : "rgba(239, 68, 68, 0.25)") + "; -fx-border-radius: 12px; -fx-border-width: 1px;"
                        );
                    }
                    setText(null);
                    setGraphic(badge);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        tableView.getColumns().addAll(snoCol, nameCol, pathCol, sizeCol, statusCol);
        tableView.setItems(controller.getFileList());
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Listen for selection changes and queue file changes to dynamically update selection counter
        tableView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<SourceFileModel>) change -> updateSelectionCount());
        controller.getFileList().addListener((ListChangeListener<SourceFileModel>) change -> updateSelectionCount());

        // Row factory: Select row on secondary click if not selected
        tableView.setRowFactory(tv -> {
            TableRow<SourceFileModel> row = new TableRow<>();
            row.setOnMousePressed(event -> {
                if (event.isSecondaryButtonDown() && !row.isEmpty()) {
                    if (!tableView.getSelectionModel().getSelectedItems().contains(row.getItem())) {
                        tableView.getSelectionModel().clearAndSelect(row.getIndex());
                    }
                }
            });
            return row;
        });

        // Set context menu for table view
        tableView.setContextMenu(createContextMenu());

        // Keyboard shortcut: Delete / Backspace key to remove selected items with confirmation dialog
        tableView.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.DELETE || event.getCode() == javafx.scene.input.KeyCode.BACK_SPACE) {
                if (!tableView.getSelectionModel().isEmpty()) {
                    handleRemoveSelected();
                    event.consume();
                }
            }
        });

        // Add beautiful placeholder (Modern Dropzone Box)
        VBox placeholder = new VBox(12);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(24, 30, 24, 30));
        placeholder.getStyleClass().add("dropzone-container");

        boolean isDark = controller.isDarkMode();

        Label iconLabel = com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.CLOUD_UPLOAD, 40);
        iconLabel.setStyle("-fx-text-fill: #6366f1;");

        Label lblPlaceholderHeader = new Label("Drag & Drop MBOX Files / Folders Here");
        lblPlaceholderHeader.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#f8fafc" : "#0f172a") + ";");

        Label lblPlaceholderSub = new Label("Supports single .mbox/.mbx files, directories, and email client profiles");
        lblPlaceholderSub.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isDark ? "#94a3b8" : "#64748b") + ";");

        HBox chipBox = new HBox(8);
        chipBox.setAlignment(Pos.CENTER);
        String[] chips = {"📁 .MBOX / .MBX", "⚡ Thunderbird", "🍏 Apple Mail", "📦 Google Takeout"};
        for (String chip : chips) {
            Label lblChip = new Label(chip);
            lblChip.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3px 8px; -fx-background-radius: 6px; " +
                             "-fx-background-color: " + (isDark ? "rgba(99, 102, 241, 0.15)" : "rgba(79, 70, 229, 0.08)") + "; " +
                             "-fx-text-fill: " + (isDark ? "#a5b4fc" : "#4f46e5") + "; " +
                             "-fx-border-color: " + (isDark ? "rgba(99, 102, 241, 0.3)" : "rgba(79, 70, 229, 0.2)") + "; -fx-border-radius: 6px; -fx-border-width: 1px;");
            chipBox.getChildren().add(lblChip);
        }

        placeholder.getChildren().addAll(iconLabel, lblPlaceholderHeader, lblPlaceholderSub, chipBox);
        tableView.setPlaceholder(placeholder);
    }

    public void updateSelectionCount() {
        if (lblSelectionCount == null) return;

        ObservableList<SourceFileModel> allFiles = controller.getFileList();
        ObservableList<SourceFileModel> selectedFiles = tableView.getSelectionModel().getSelectedItems();

        int totalCount = allFiles.size();
        int selectedCount = selectedFiles.size();

        if (totalCount == 0) {
            lblSelectionCount.setText("0 files selected");
            lblSelectionCount.setStyle(
                "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94a3b8; " +
                "-fx-background-color: " + (controller.isDarkMode() ? "rgba(148, 163, 184, 0.12)" : "rgba(148, 163, 184, 0.15)") + "; " +
                "-fx-padding: 3px 10px; -fx-background-radius: 12px; -fx-border-radius: 12px; " +
                "-fx-border-color: " + (controller.isDarkMode() ? "rgba(148, 163, 184, 0.2)" : "rgba(148, 163, 184, 0.3)") + "; -fx-border-width: 1px;"
            );
            if (lblTableFooterStatus != null) {
                lblTableFooterStatus.setText("No mailbox files imported.");
            }
        } else if (selectedCount == 0) {
            lblSelectionCount.setText(totalCount == 1 ? "1 file loaded (0 selected)" : totalCount + " files loaded (0 selected)");
            lblSelectionCount.setStyle(
                "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #6366f1; " +
                "-fx-background-color: " + (controller.isDarkMode() ? "rgba(99, 102, 241, 0.15)" : "rgba(79, 70, 229, 0.08)") + "; " +
                "-fx-padding: 3px 10px; -fx-background-radius: 12px; -fx-border-radius: 12px; " +
                "-fx-border-color: " + (controller.isDarkMode() ? "rgba(99, 102, 241, 0.3)" : "rgba(79, 70, 229, 0.2)") + "; -fx-border-width: 1px;"
            );
            if (lblTableFooterStatus != null) {
                lblTableFooterStatus.setText(String.format("Total: %d mailbox file(s) in queue.", totalCount));
            }
        } else {
            String text = (selectedCount == 1) ? "1 file selected" : (selectedCount + " files selected");
            if (totalCount > selectedCount) {
                text += " (" + selectedCount + " of " + totalCount + ")";
            }
            lblSelectionCount.setText(text);
            lblSelectionCount.setStyle(
                "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #10b981; " +
                "-fx-background-color: " + (controller.isDarkMode() ? "rgba(16, 185, 129, 0.2)" : "rgba(16, 185, 129, 0.12)") + "; " +
                "-fx-padding: 3px 10px; -fx-background-radius: 12px; -fx-border-radius: 12px; " +
                "-fx-border-color: " + (controller.isDarkMode() ? "rgba(16, 185, 129, 0.3)" : "rgba(16, 185, 129, 0.25)") + "; -fx-border-width: 1px;"
            );
            if (lblTableFooterStatus != null) {
                lblTableFooterStatus.setText(String.format("Selected: %d of %d mailbox file(s)", selectedCount, totalCount));
            }
        }
    }

    private ContextMenu createContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getStyleClass().add("table-context-menu");

        // 1. Open File Location
        MenuItem itemOpenLocation = new MenuItem("Open File Location");
        itemOpenLocation.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.FOLDER_OPEN, "-fx-text-fill: #f59e0b; -fx-font-size: 14px;"));
        itemOpenLocation.setOnAction(e -> {
            SourceFileModel selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleOpenFileLocation(selected.getFilePath());
            } else {
                controller.showNotification("Please select a file from the list first.");
            }
        });

        // 2. View File Details
        MenuItem itemDetails = new MenuItem("View File Details");
        itemDetails.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.INFO, "-fx-text-fill: #3b82f6; -fx-font-size: 14px;"));
        itemDetails.setOnAction(e -> {
            SourceFileModel selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleViewFileDetails(selected);
            } else {
                controller.showNotification("Please select a file from the list first.");
            }
        });

        // 3. Validate File
        MenuItem itemValidate = new MenuItem("Validate File");
        itemValidate.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.VERIFIED, "-fx-text-fill: #10b981; -fx-font-size: 14px;"));
        itemValidate.setOnAction(e -> handleValidateSelectedFiles());

        // 4. Remove Selected
        MenuItem itemRemove = new MenuItem("Remove Selected");
        itemRemove.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.DELETE, "-fx-text-fill: #ef4444; -fx-font-size: 14px;"));
        itemRemove.getStyleClass().add("menu-item-danger");
        itemRemove.setOnAction(e -> handleRemoveSelected());

        // 5. Clear All
        MenuItem itemClearAll = new MenuItem("Clear All");
        itemClearAll.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.BLOCK, "-fx-text-fill: #ef4444; -fx-font-size: 14px;"));
        itemClearAll.getStyleClass().add("menu-item-danger");
        itemClearAll.setOnAction(e -> handleRemoveAll());

        // 6. Refresh List
        MenuItem itemRefresh = new MenuItem("Refresh List");
        itemRefresh.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.REFRESH, "-fx-text-fill: #6366f1; -fx-font-size: 14px;"));
        itemRefresh.setOnAction(e -> handleRefreshTableList());

        contextMenu.getItems().addAll(
            itemOpenLocation,
            itemDetails,
            itemValidate,
            itemRemove,
            itemClearAll,
            itemRefresh
        );

        contextMenu.setOnShowing(event -> {
            boolean hasSelection = !tableView.getSelectionModel().isEmpty();
            boolean hasItems = !tableView.getItems().isEmpty();
            itemOpenLocation.setDisable(!hasSelection);
            itemDetails.setDisable(!hasSelection);
            itemValidate.setDisable(!hasItems);
            itemRemove.setDisable(!hasSelection);
            itemClearAll.setDisable(!hasItems);
        });

        return contextMenu;
    }

    private void handleOpenFileLocation(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            controller.showAlert(Alert.AlertType.WARNING, "No File Selected", "File path is empty or invalid.");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && parent.exists()) {
                controller.showAlert(Alert.AlertType.WARNING, "File Missing",
                        "The selected file no longer exists at:\n" + filePath + "\n\nOpening parent folder instead.");
                file = parent;
            } else {
                controller.showAlert(Alert.AlertType.ERROR, "Location Not Found",
                        "Neither the file nor its parent directory could be found:\n" + filePath);
                return;
            }
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                if (file.isFile()) {
                    new ProcessBuilder("explorer.exe", "/select,", file.getAbsolutePath()).start();
                } else {
                    new ProcessBuilder("explorer.exe", file.getAbsolutePath()).start();
                }
            } else if (os.contains("mac")) {
                if (file.isFile()) {
                    new ProcessBuilder("open", "-R", file.getAbsolutePath()).start();
                } else {
                    new ProcessBuilder("open", file.getAbsolutePath()).start();
                }
            } else {
                File targetDir = file.isDirectory() ? file : file.getParentFile();
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                    java.awt.Desktop.getDesktop().open(targetDir);
                } else {
                    new ProcessBuilder("xdg-open", targetDir.getAbsolutePath()).start();
                }
            }
            controller.showNotification("Opened file location: " + file.getName());
        } catch (Exception ex) {
            ex.printStackTrace();
            try {
                File targetDir = file.isDirectory() ? file : file.getParentFile();
                if (targetDir != null && targetDir.exists() && java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(targetDir);
                    controller.showNotification("Opened file location: " + targetDir.getName());
                } else {
                    controller.showAlert(Alert.AlertType.ERROR, "Cannot Open Location",
                            "Failed to open system file manager for: " + filePath + "\nError: " + ex.getMessage());
                }
            } catch (Exception e2) {
                e2.printStackTrace();
                controller.showAlert(Alert.AlertType.ERROR, "Cannot Open Location",
                        "Failed to open system file manager for: " + filePath + "\nError: " + e2.getMessage());
            }
        }
    }

    private void handleViewFileDetails(SourceFileModel model) {
        if (model == null) return;

        File file = new File(model.getFilePath());
        boolean exists = file.exists();
        String sizeStr = model.getFileSize();
        if (exists && (sizeStr == null || sizeStr.isEmpty())) {
            sizeStr = controller.getFormattedFileSize(file.length());
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String lastModified = exists ? sdf.format(new java.util.Date(file.lastModified())) : "N/A (File missing)";

        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Mailbox File Details");
        dialog.setHeaderText(model.getFileName());
        dialog.initOwner(getScene() != null ? getScene().getWindow() : null);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStyleClass().add("custom-alert-dialog");

        VBox content = new VBox(10);
        content.setPadding(new Insets(10, 0, 10, 0));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        int r = 0;
        addDetailRow(grid, r++, "File Name:", model.getFileName());
        addDetailRow(grid, r++, "Full Path:", model.getFilePath());
        addDetailRow(grid, r++, "Format Type:", model.getSourceType() != null ? model.getSourceType().toUpperCase() : "Unknown");
        addDetailRow(grid, r++, "File Size:", sizeStr);
        addDetailRow(grid, r++, "Validation Status:", model.getStatus());
        addDetailRow(grid, r++, "Import Method:", model.getImportSourceType() + ("Folder".equalsIgnoreCase(model.getImportSourceType()) ? " (" + model.getSourceFolderName() + ")" : ""));
        addDetailRow(grid, r++, "Last Modified:", lastModified);
        addDetailRow(grid, r++, "File Access:", exists ? (file.canRead() ? "Readable" : "Access Denied") : "File Not Found");

        content.getChildren().add(grid);
        dialogPane.setContent(content);

        dialog.showAndWait();
    }

    private void addDetailRow(GridPane grid, int rowIndex, String labelText, String valueText) {
        Label lblKey = new Label(labelText);
        lblKey.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: " + (controller.isDarkMode() ? "#94a3b8" : "#64748b") + ";");

        Label lblVal = new Label(valueText != null ? valueText : "-");
        lblVal.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (controller.isDarkMode() ? "#f8fafc" : "#0f172a") + ";");
        lblVal.setWrapText(true);

        grid.add(lblKey, 0, rowIndex);
        grid.add(lblVal, 1, rowIndex);
    }

    private void handleValidateSelectedFiles() {
        ObservableList<SourceFileModel> selectedItems = tableView.getSelectionModel().getSelectedItems();
        List<SourceFileModel> targets = new ArrayList<>(selectedItems.isEmpty() ? tableView.getItems() : selectedItems);

        if (targets.isEmpty()) {
            controller.showNotification("No imported files to validate.");
            return;
        }

        int validCount = 0;
        int invalidCount = 0;

        for (SourceFileModel model : targets) {
            File file = new File(model.getFilePath());
            boolean isValid = file.exists() && file.isFile() && file.canRead() && file.length() > 0 && SourceAdapterFactory.isSupported(file);

            if (isValid) {
                model.setStatus("Valid");
                model.setValid(true);
                validCount++;
            } else {
                model.setStatus("Invalid");
                model.setValid(false);
                invalidCount++;
            }
        }

        tableView.refresh();
        long totalValid = controller.getFileList().stream().filter(SourceFileModel::isValid).count();
        if (controller.getPrimaryStage() != null) {
            controller.setNextButtonDisable(totalValid == 0);
        }

        controller.showNotification(String.format("Validation re-checked: %d Valid, %d Invalid", validCount, invalidCount));
    }

    private void handleRefreshTableList() {
        handleValidateSelectedFiles();
        refreshRecentMailboxes();
        tableView.refresh();
        controller.showNotification("Imported mailbox list refreshed.");
    }

    private void handleAddFiles() {
        System.out.println("User triggered manual file selection dialog.");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Mailbox File(s)");
        
        java.util.Set<String> supportedExts = SourceAdapterFactory.getSupportedExtensions();
        java.util.List<String> wildcards = new java.util.ArrayList<>();
        for (String ext : supportedExts) {
            wildcards.add("*." + ext.toLowerCase());
            wildcards.add("*." + ext.toUpperCase());
        }
        if (!wildcards.isEmpty()) {
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("MBOX Mailbox Files (*.mbox, *.mbx)", wildcards)
            );
        }
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files (*.*)", "*.*"));

        String lastDir = SettingsManager.getSetting("last_file_directory", null);
        if (lastDir != null) {
            File initDir = new File(lastDir);
            if (initDir.exists() && initDir.isDirectory()) {
                fileChooser.setInitialDirectory(initDir);
            }
        }

        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(controller.getPrimaryStage());
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            System.out.println("User selected " + selectedFiles.size() + " file(s) for import.");
            File parentDir = selectedFiles.get(0).getParentFile();
            if (parentDir != null) {
                SettingsManager.saveSetting("last_file_directory", parentDir.getAbsolutePath());
            }

            int addedCount = 0;
            int duplicateCount = 0;
            List<String> duplicateNames = new ArrayList<>();

            for (File file : selectedFiles) {
                boolean isDuplicate = false;
                for (SourceFileModel item : controller.getFileList()) {
                    if (item.getFilePath().equalsIgnoreCase(file.getAbsolutePath())) {
                        isDuplicate = true;
                        break;
                    }
                }

                if (isDuplicate) {
                    duplicateCount++;
                    duplicateNames.add(file.getName());
                    System.out.println("Skipped file import (Duplicate path in queue): " + file.getAbsolutePath());
                } else {
                    System.out.println("Attempting to import file: " + file.getAbsolutePath());
                    if (controller.addFileToList(file, "File", "")) {
                        addedCount++;
                        SettingsManager.addRecentFile(file.getAbsolutePath());
                    }
                }
            }

            if (!duplicateNames.isEmpty()) {
                System.out.println("A total of " + duplicateCount + " duplicate file(s) were rejected from the selection dialog.");
                StringBuilder sb = new StringBuilder(
                        "The following file(s) are already selected and were skipped:\n\n");
                for (String name : duplicateNames) {
                    sb.append("- ").append(name).append("\n");
                }
                controller.showAlert(Alert.AlertType.WARNING, "Duplicate Selection", sb.toString());
            }

            System.out.println("File import session completed. Successfully added: " + addedCount + ", skipped duplicates: " + duplicateCount);
            controller.showNotification(String.format("Added %d files. (Skipped %d duplicates)", addedCount, duplicateCount));
            refreshRecentMailboxes();
        } else {
            System.out.println("User cancelled file selection dialog.");
        }
    }

    private void handleAddFolder() {
        System.out.println("User triggered manual directory scan dialog.");
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Folder to Scan for Mailbox Files");

        String lastDir = SettingsManager.getSetting("last_folder_directory", null);
        if (lastDir != null) {
            File initDir = new File(lastDir);
            if (initDir.exists() && initDir.isDirectory()) {
                dirChooser.setInitialDirectory(initDir);
            }
        }

        File selectedDir = dirChooser.showDialog(controller.getPrimaryStage());
        if (selectedDir != null) {
            System.out.println("User selected folder to scan: " + selectedDir.getAbsolutePath());
            SettingsManager.saveSetting("last_folder_directory", selectedDir.getAbsolutePath());

            controller.showNotification("Scanning folder recursively for mailbox files...");
            int addedCount = 0;
            int duplicateCount = 0;
            List<String> duplicateNames = new ArrayList<>();

            List<File> mailboxFiles = new ArrayList<>();
            System.out.println("Starting recursive scanning inside directory: " + selectedDir.getAbsolutePath());
            scanFolderRecursively(selectedDir, mailboxFiles);
            System.out.println("Scanning finished. Found " + mailboxFiles.size() + " potential mailbox file(s) matching registered formats.");

            for (File file : mailboxFiles) {
                boolean isDuplicate = false;
                for (SourceFileModel item : controller.getFileList()) {
                    if (item.getFilePath().equalsIgnoreCase(file.getAbsolutePath())) {
                        isDuplicate = true;
                        break;
                    }
                }

                if (isDuplicate) {
                    duplicateCount++;
                    duplicateNames.add(file.getName() + " (in " + file.getParentFile().getName() + ")");
                    System.out.println("Skipped file import (Duplicate path in queue): " + file.getAbsolutePath());
                } else {
                    String relativePath = "";
                    try {
                        String base = selectedDir.getCanonicalPath();
                        String filepath = file.getCanonicalPath();
                        if (filepath.startsWith(base)) {
                            relativePath = filepath.substring(base.length());
                            if (relativePath.startsWith(File.separator)) {
                                relativePath = relativePath.substring(1);
                            }
                            int lastSeparator = relativePath.lastIndexOf(File.separator);
                            if (lastSeparator != -1) {
                                relativePath = relativePath.substring(0, lastSeparator);
                            } else {
                                relativePath = "";
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                    String displayFolderName = selectedDir.getName();
                    if (!relativePath.isEmpty()) {
                        displayFolderName = selectedDir.getName() + File.separator + relativePath;
                    }

                    System.out.println("Attempting to import scanned file: " + file.getAbsolutePath() + " (Associated sub-path: " + displayFolderName + ")");
                    if (controller.addFileToList(file, "Folder", displayFolderName)) {
                        addedCount++;
                        SettingsManager.addRecentFile(file.getAbsolutePath());
                    }
                }
            }

            if (!duplicateNames.isEmpty()) {
                System.out.println("A total of " + duplicateCount + " duplicate file(s) found during directory scan were skipped.");
                StringBuilder sb = new StringBuilder(
                        "The following file(s) scanned from the folder are already in the list and were skipped:\n\n");
                for (String name : duplicateNames) {
                    sb.append("- ").append(name).append("\n");
                }
                controller.showAlert(Alert.AlertType.WARNING, "Duplicate Selection", sb.toString());
            }

            System.out.println("Directory scan session completed. Successfully added: " + addedCount + ", skipped duplicates: " + duplicateCount);
            controller.showNotification(String.format("Scan complete. Added %d files. (Skipped %d duplicates)", addedCount,
                    duplicateCount));
            refreshRecentMailboxes();
        } else {
            System.out.println("User cancelled directory scan dialog.");
        }
    }

    private void scanFolderRecursively(File dir, List<File> mailboxFiles) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    scanFolderRecursively(file, mailboxFiles);
                } else if (file.isFile() && SourceAdapterFactory.isSupported(file)) {
                    System.out.println("  Scanned and matched supported file: " + file.getAbsolutePath());
                    mailboxFiles.add(file);
                }
            }
        }
    }

    private void handleRemoveSelected() {
        ObservableList<SourceFileModel> selectedItems = tableView.getSelectionModel().getSelectedItems();
        if (selectedItems == null || selectedItems.isEmpty()) {
            System.out.println("User clicked Remove Selected, but the table selection was empty.");
            controller.showAlert(Alert.AlertType.WARNING, "Selection Required",
                    "Please select one or more files from the table first before clicking Remove.");
            return;
        }

        int count = selectedItems.size();
        String message = (count == 1)
                ? "1 file selected. Are you sure you want to remove it?"
                : count + " files selected. Are you sure you want to remove them?";

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Removal");
        alert.setHeaderText("Remove Selected Files");
        alert.setContentText(message);
        if (getScene() != null && getScene().getWindow() != null) {
            alert.initOwner(getScene().getWindow());
        }

        DialogPane dp = alert.getDialogPane();
        dp.getStyleClass().add("custom-alert-dialog");

        ButtonType btnConfirm = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnConfirm, btnCancel);

        alert.showAndWait().ifPresent(response -> {
            if (response == btnConfirm) {
                List<SourceFileModel> toRemove = new ArrayList<>(selectedItems);
                System.out.println("Removing selected file(s) from the import queue (Count: " + toRemove.size() + "):");
                for (SourceFileModel model : toRemove) {
                    System.out.println("  Removing file: " + model.getFilePath());
                }
                controller.getFileList().removeAll(toRemove);
                controller.showNotification(String.format("Removed %d selected file(s) from queue.", toRemove.size()));

                long validCount = controller.getFileList().stream().filter(SourceFileModel::isValid).count();
                if (controller.getPrimaryStage() != null) {
                    controller.setNextButtonDisable(validCount == 0);
                }
            }
        });
    }

    private void handleRemoveAll() {
        if (controller.getFileList().isEmpty()) {
            System.out.println("User clicked Clear All, but queue was already empty.");
            return;
        }

        int total = controller.getFileList().size();
        String message = (total == 1)
                ? "1 file in queue. Are you sure you want to clear all files?"
                : total + " files in queue. Are you sure you want to clear all files?";

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Clear All");
        alert.setHeaderText("Clear All Mailbox Files");
        alert.setContentText(message);
        if (getScene() != null && getScene().getWindow() != null) {
            alert.initOwner(getScene().getWindow());
        }

        DialogPane dp = alert.getDialogPane();
        dp.getStyleClass().add("custom-alert-dialog");

        ButtonType btnConfirm = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnConfirm, btnCancel);

        alert.showAndWait().ifPresent(response -> {
            if (response == btnConfirm) {
                System.out.println("Clearing all file(s) from the import queue (Removed Count: " + total + ").");
                controller.getFileList().clear();
                controller.showNotification("Cleared all files from the queue.");
                if (controller.getPrimaryStage() != null) {
                    controller.setNextButtonDisable(true);
                }
            }
        });
    }

    public void refreshAllStyles() {
        refreshRecentMailboxes();
        if (lblTableHeader != null) {
            lblTableHeader.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + (controller.isDarkMode() ? "#f8fafc" : "#1e293b") + ";");
        }
        if (lblTableFooterStatus != null) {
            lblTableFooterStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (controller.isDarkMode() ? "#cbd5e1" : "#64748b") + ";");
        }
        if (tableView != null) {
            tableView.refresh();
        }
    }

    public void refreshRecentMailboxes() {
        refreshIncompleteMigrations();
        refreshConversionHistory();
        if (recentPane == null) return;
        recentPane.getChildren().clear();

        java.util.List<String> paths = SettingsManager.getRecentFiles();
        if (paths == null || paths.isEmpty()) {
            recentBox.setVisible(false);
            recentBox.setManaged(false);
            return;
        }
        recentBox.setVisible(true);
        recentBox.setManaged(true);

        boolean isDark = controller.isDarkMode();
        for (String path : paths) {
            File file = new File(path);
            String name = file.getName();
            double bytes = file.length();
            double sizeMb = bytes / (1024.0 * 1024.0);
            boolean exists = file.exists();
            String sizeStr = (exists && bytes > 0)
                    ? (sizeMb > 1024 ? String.format("%.1f GB", sizeMb / 1024) : String.format("%.1f MB", sizeMb))
                    : "File missing";

            HBox card = new HBox(10);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setPadding(new Insets(8, 10, 8, 10));
            card.setCursor(javafx.scene.Cursor.HAND);
            card.setMaxWidth(Double.MAX_VALUE);

            String baseStyle = "-fx-background-color: " + (isDark ? "rgba(30, 41, 59, 0.7)" : "rgba(248, 250, 252, 0.9)") + "; " +
                    "-fx-border-color: " + (isDark ? "rgba(99, 102, 241, 0.2)" : "rgba(203, 213, 225, 0.6)") + "; " +
                    "-fx-border-width: 1px; " +
                    "-fx-border-radius: 8px; " +
                    "-fx-background-radius: 8px;";
            card.setStyle(baseStyle);

            // Left Icon Badge
            Label lblIcon = com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.INVENTORY_2, 14);
            lblIcon.setStyle("-fx-text-fill: #6366f1; -fx-background-color: " + (isDark ? "rgba(99, 102, 241, 0.15)" : "rgba(99, 102, 241, 0.08)") + "; -fx-padding: 6px; -fx-background-radius: 6px;");

            // Text Container
            VBox textBox = new VBox(2);
            HBox.setHgrow(textBox, Priority.ALWAYS);
            textBox.setAlignment(Pos.CENTER_LEFT);

            Label lblName = new Label(name);
            lblName.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#f8fafc" : "#1e293b") + ";");
            lblName.setMaxWidth(130);
            lblName.setTextOverrun(OverrunStyle.ELLIPSIS);

            Label lblSize = new Label(sizeStr);
            lblSize.setStyle("-fx-font-size: 9.5px; -fx-text-fill: " + (exists ? "#64748b" : "#ef4444") + ";");

            textBox.getChildren().addAll(lblName, lblSize);

            // Quick Add Action Icon
            Label lblAddIcon = com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.ADD, 12);
            lblAddIcon.setStyle("-fx-text-fill: #ea580c; -fx-padding: 3px;");

            card.getChildren().addAll(lblIcon, textBox, lblAddIcon);

            // Hover effect
            card.setOnMouseEntered(ev -> {
                card.setStyle("-fx-background-color: " + (isDark ? "rgba(99, 102, 241, 0.2)" : "rgba(79, 70, 229, 0.08)") + "; " +
                        "-fx-border-color: #6366f1; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 8px; " +
                        "-fx-background-radius: 8px;");
            });
            card.setOnMouseExited(ev -> {
                card.setStyle(baseStyle);
            });

            card.setOnMouseClicked(ev -> {
                handleQuickAdd(file);
            });

            Tooltip tooltip = new Tooltip(path + (exists ? "" : " (File Missing)"));
            tooltip.setShowDelay(javafx.util.Duration.millis(200));
            Tooltip.install(card, tooltip);

            recentPane.getChildren().add(card);
        }
    }

    private void handleQuickAdd(File file) {
        if (!file.exists()) {
            controller.showAlert(Alert.AlertType.ERROR, "File Missing", "The file no longer exists at: " + file.getAbsolutePath());
            java.util.List<String> list = SettingsManager.getRecentFiles();
            list.remove(file.getAbsolutePath());
            SettingsManager.saveRecentFiles(list);
            refreshRecentMailboxes();
            return;
        }
        
        boolean isDuplicate = false;
        for (SourceFileModel item : controller.getFileList()) {
            if (item.getFilePath().equalsIgnoreCase(file.getAbsolutePath())) {
                isDuplicate = true;
                break;
            }
        }
        
        if (isDuplicate) {
            controller.showNotification("File is already in the queue: " + file.getName());
        } else {
            System.out.println("Quick-adding file from recents: " + file.getAbsolutePath());
            if (controller.addFileToList(file, "File", "")) {
                SettingsManager.addRecentFile(file.getAbsolutePath());
                refreshRecentMailboxes();
                controller.showNotification("Quick-added: " + file.getName());
                // Trigger validation on controller side
                long validCount = controller.getFileList().stream().filter(SourceFileModel::isValid).count();
                if (controller.getPrimaryStage() != null) {
                    controller.setNextButtonDisable(validCount == 0);
                }
            }
        }
    }

    private void refreshIncompleteMigrations() {
        if (incompletePane == null) return;
        incompletePane.getChildren().clear();

        // Fetch the migration sessions from settings.db
        // Incomplete sessions are those marked as 'IN_PROGRESS'
        List<com.pstconverter.util.SettingsManager.MigrationSession> sessions = com.pstconverter.util.SettingsManager.getAllMigrationSessions();
        List<com.pstconverter.util.SettingsManager.MigrationSession> incompleteSessions = new ArrayList<>();
        for (com.pstconverter.util.SettingsManager.MigrationSession s : sessions) {
            if ("IN_PROGRESS".equals(s.status())) {
                incompleteSessions.add(s);
            }
        }

        // Hide Resume Center container if no interrupted migrations exist
        if (incompleteSessions.isEmpty()) {
            incompleteBox.setVisible(false);
            incompleteBox.setManaged(false);
            return;
        }

        incompleteBox.setVisible(true);
        incompleteBox.setManaged(true);

        boolean isDark = controller.isDarkMode();
        for (com.pstconverter.util.SettingsManager.MigrationSession session : incompleteSessions) {
            File file = new File(session.sourceFilePath());
            String fileName = file.getName();
            String fileSize = file.exists() ? controller.getFormattedFileSize(file.length()) : "Unknown size";
            
            // Query migration_progress table to get the number of messages successfully processed
            int completedCount = com.pstconverter.util.SettingsManager.getMigratedCountForFile(session.sourceFilePath(), session.format(), session.destinationPath());
            int total = session.totalMessages();
            int remaining = Math.max(0, total - completedCount);

            VBox card = new VBox(8);
            card.setPadding(new Insets(12));
            card.setPrefWidth(320);
            card.setStyle("-fx-background-color: " + (isDark ? "#1e293b" : "#ffffff") + "; " +
                          "-fx-border-color: " + (isDark ? "rgba(99, 102, 241, 0.3)" : "rgba(79, 70, 229, 0.2)") + "; " +
                          "-fx-border-width: 1px; " +
                          "-fx-border-radius: 8px; " +
                          "-fx-background-radius: 8px;");

            // Header: Name and format badge
            HBox header = new HBox(5);
            header.setAlignment(Pos.CENTER_LEFT);
            Label lblName = new Label(fileName);
            lblName.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#ffffff" : "#1e293b") + ";");
            HBox.setHgrow(lblName, Priority.ALWAYS);

            Label lblBadge = new Label(session.format().toUpperCase());
            lblBadge.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #ef4444; -fx-background-color: rgba(239, 68, 68, 0.1); -fx-padding: 2px 6px; -fx-background-radius: 4px;");
            header.getChildren().addAll(lblName, lblBadge);

            // Path & Size
            Label lblPath = new Label("Source: " + session.sourceFilePath() + " (" + fileSize + ")");
            lblPath.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (isDark ? "#94a3b8" : "#64748b") + ";");
            lblPath.setWrapText(true);

            // Progress Metrics
            Label lblMetrics = new Label(String.format("Progress: %d/%d messages converted (%d remaining)", completedCount, total, remaining));
            lblMetrics.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: " + (isDark ? "#cbd5e1" : "#475569") + ";");

            // Destination
            Label lblDest = new Label("Destination: " + session.destinationPath());
            lblDest.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (isDark ? "#94a3b8" : "#64748b") + ";");
            lblDest.setWrapText(true);

            // Time
            // Format the stored timestamp into a friendly local-time string (system timezone)
            String rawTs = session.timestamp();
            String friendlyTs = rawTs;
            if (rawTs != null && !rawTs.isEmpty()) {
                try {
                    java.time.LocalDateTime parsed = java.time.LocalDateTime.parse(
                            rawTs,
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    );
                    friendlyTs = parsed.format(
                            java.time.format.DateTimeFormatter.ofPattern(
                                     "dd MMM yyyy, hh:mm a",
                                    java.util.Locale.getDefault()
                            )
                    );
                } catch (Exception ignored) { /* keep raw string if parse fails */ }
            }
            Label lblTime = new Label("Interrupted on: " + friendlyTs);
            lblTime.setStyle("-fx-font-size: 9px; -fx-text-fill: #94a3b8;");

            // Action Buttons
            HBox actionBox = new HBox(10);
            actionBox.setAlignment(Pos.CENTER_LEFT);

            // Clicking "Resume" re-populates the wizard with stored configurations and moves directly to Step 5
            MFXButton btnResume = new MFXButton("Resume");
            btnResume.getStyleClass().addAll("action-btn", "btn-primary");
            HBox.setHgrow(btnResume, Priority.ALWAYS);
            btnResume.setMaxWidth(Double.MAX_VALUE);
            btnResume.setStyle("-fx-font-size: 11px; -fx-padding: 6px 12px; -fx-font-weight: bold;");
            btnResume.setOnAction(e -> {
                if (completedCount > 0) {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Resume Migration");
                    alert.setHeaderText("Resume or Start Fresh?");
                    alert.setContentText("It will skip " + completedCount + " already migrated messages. Do you want to Continue or Start Fresh?");

                    ButtonType btnContinue = new ButtonType("Continue");
                    ButtonType btnStartFresh = new ButtonType("Start Fresh");
                    ButtonType btnCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

                    alert.getButtonTypes().setAll(btnContinue, btnStartFresh, btnCancel);
                    alert.initOwner(getScene() != null ? getScene().getWindow() : null);

                    DialogPane dp = alert.getDialogPane();
                    dp.getStyleClass().add("custom-alert-dialog");

                    alert.showAndWait().ifPresent(response -> {
                        if (response == btnContinue) {
                            controller.resumeMigrationSession(session);
                        } else if (response == btnStartFresh) {
                            // Clean up past progress from db
                            com.pstconverter.util.SettingsManager.clearMigrationProgressForFile(session.sourceFilePath(), session.format(), session.destinationPath());
                            com.pstconverter.util.DiagnosticLogger.logSessionCleared(session.sourceFilePath(), session.format(), session.destinationPath());
                            controller.rerunMigrationSession(session);
                        }
                    });
                } else {
                    controller.resumeMigrationSession(session);
                }
            });

            // Clicking "Delete" cleans up both session tracking rows and migration progress rows in the SQLite DB
            MFXButton btnDelete = new MFXButton("Delete");
            btnDelete.getStyleClass().addAll("action-btn", "btn-danger-outline");
            btnDelete.setStyle("-fx-font-size: 11px; -fx-padding: 6px 12px; -fx-font-weight: bold;");
            btnDelete.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Delete Migration Record");
                confirm.setHeaderText("Delete this interrupted record?");
                confirm.setContentText("This will delete the session tracking info and past migration progress from the database. It will not delete your exported files.\n\nAre you sure?");
                confirm.initOwner(getScene() != null ? getScene().getWindow() : null);
                confirm.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        com.pstconverter.util.SettingsManager.clearMigrationProgressForFile(session.sourceFilePath(), session.format(), session.destinationPath());
                        refreshIncompleteMigrations();
                    }
                });
            });

            actionBox.getChildren().addAll(btnResume, btnDelete);

            card.getChildren().addAll(header, lblPath, lblMetrics, lblDest, lblTime, actionBox);
            incompletePane.getChildren().add(card);
        }
    }

    private void setupDragAndDrop() {
        tableView.setOnDragOver(event -> {
            if (event.getGestureSource() != tableView && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                
                // Visual highlight during drag-over
                boolean isDark = controller.isDarkMode();
                tableView.setStyle("-fx-border-color: #6366f1; " +
                                   "-fx-border-width: 2px; " +
                                   "-fx-border-style: dashed; " +
                                   "-fx-border-radius: 8px; " +
                                   "-fx-background-color: " + (isDark ? "rgba(99, 102, 241, 0.15)" : "rgba(79, 70, 229, 0.08)") + ";");
            }
            event.consume();
        });

        tableView.setOnDragExited(event -> {
            tableView.setStyle("");
            event.consume();
        });

        tableView.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                handleDroppedFiles(db.getFiles());
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
            tableView.setStyle("");
        });
    }

    private void handleDroppedFiles(List<File> files) {
        if (files == null || files.isEmpty()) return;
        
        int addedCount = 0;
        int duplicateCount = 0;
        List<String> duplicateNames = new ArrayList<>();
        List<String> unsupportedNames = new ArrayList<>();

        for (File file : files) {
            if (file.isDirectory()) {
                // If it's a directory, scan recursively
                List<File> mailboxFiles = new ArrayList<>();
                scanFolderRecursively(file, mailboxFiles);
                
                for (File subFile : mailboxFiles) {
                    boolean isDuplicate = false;
                    for (SourceFileModel item : controller.getFileList()) {
                        if (item.getFilePath().equalsIgnoreCase(subFile.getAbsolutePath())) {
                            isDuplicate = true;
                            break;
                        }
                    }
                    if (isDuplicate) {
                        duplicateCount++;
                        duplicateNames.add(subFile.getName());
                    } else {
                        // Calculate relative sub-path
                        String relativePath = "";
                        try {
                            String base = file.getCanonicalPath();
                            String filepath = subFile.getCanonicalPath();
                            if (filepath.startsWith(base)) {
                                relativePath = filepath.substring(base.length());
                                if (relativePath.startsWith(File.separator)) {
                                    relativePath = relativePath.substring(1);
                                }
                                int lastSeparator = relativePath.lastIndexOf(File.separator);
                                if (lastSeparator != -1) {
                                    relativePath = relativePath.substring(0, lastSeparator);
                                } else {
                                    relativePath = "";
                                }
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                        
                        String displayFolderName = file.getName();
                        if (!relativePath.isEmpty()) {
                            displayFolderName = file.getName() + File.separator + relativePath;
                        }
                        
                        if (controller.addFileToList(subFile, "Folder", displayFolderName)) {
                            addedCount++;
                            SettingsManager.addRecentFile(subFile.getAbsolutePath());
                        }
                    }
                }
            } else if (file.isFile()) {
                if (SourceAdapterFactory.isSupported(file)) {
                    boolean isDuplicate = false;
                    for (SourceFileModel item : controller.getFileList()) {
                        if (item.getFilePath().equalsIgnoreCase(file.getAbsolutePath())) {
                            isDuplicate = true;
                            break;
                        }
                    }
                    if (isDuplicate) {
                        duplicateCount++;
                        duplicateNames.add(file.getName());
                    } else {
                        if (controller.addFileToList(file, "File", "")) {
                            addedCount++;
                            SettingsManager.addRecentFile(file.getAbsolutePath());
                        }
                    }
                } else {
                    unsupportedNames.add(file.getName());
                }
            }
        }

        if (!duplicateNames.isEmpty()) {
            StringBuilder sb = new StringBuilder("The following file(s) are already selected and were skipped:\n\n");
            for (String name : duplicateNames) {
                sb.append("- ").append(name).append("\n");
            }
            controller.showAlert(Alert.AlertType.WARNING, "Duplicate Selection", sb.toString());
        }

        if (!unsupportedNames.isEmpty()) {
            StringBuilder sb = new StringBuilder("The following file(s) are not supported mailbox formats:\n\n");
            for (String name : unsupportedNames) {
                sb.append("- ").append(name).append("\n");
            }
            controller.showAlert(Alert.AlertType.ERROR, "Unsupported Format(s)", sb.toString());
        }

        if (addedCount > 0) {
            controller.showNotification(String.format("Added %d files. (Skipped %d duplicates)", addedCount, duplicateCount));
            refreshRecentMailboxes();
            
            // Trigger next button update
            long validCount = controller.getFileList().stream().filter(SourceFileModel::isValid).count();
            controller.setNextButtonDisable(validCount == 0);
        }
    }

    private void refreshConversionHistory() {
        if (historyPane == null) return;
        historyPane.getChildren().clear();

        // Fetch the migration sessions from settings.db
        // Completed sessions are those marked as 'COMPLETED'
        List<com.pstconverter.util.SettingsManager.MigrationSession> sessions = com.pstconverter.util.SettingsManager.getAllMigrationSessions();
        List<com.pstconverter.util.SettingsManager.MigrationSession> completedSessions = new ArrayList<>();
        for (com.pstconverter.util.SettingsManager.MigrationSession s : sessions) {
            if ("COMPLETED".equals(s.status())) {
                completedSessions.add(s);
            }
        }

        // Hide History container if no completed migrations exist
        if (completedSessions.isEmpty()) {
            historyBox.setVisible(false);
            historyBox.setManaged(false);
            return;
        }

        historyBox.setVisible(true);
        historyBox.setManaged(true);

        boolean isDark = controller.isDarkMode();
        for (com.pstconverter.util.SettingsManager.MigrationSession session : completedSessions) {
            File file = new File(session.sourceFilePath());
            String fileName = file.getName();
            String fileSize = file.exists() ? controller.getFormattedFileSize(file.length()) : "Unknown size";
            
            VBox card = new VBox(8);
            card.setPadding(new Insets(12));
            card.setPrefWidth(320);
            card.setStyle("-fx-background-color: " + (isDark ? "#1e293b" : "#ffffff") + "; " +
                          "-fx-border-color: " + (isDark ? "rgba(16, 185, 129, 0.3)" : "rgba(16, 185, 129, 0.2)") + "; " +
                          "-fx-border-width: 1px; " +
                          "-fx-border-radius: 8px; " +
                          "-fx-background-radius: 8px;");

            // Header: Name and format badge
            HBox header = new HBox(5);
            header.setAlignment(Pos.CENTER_LEFT);
            Label lblName = new Label(fileName);
            lblName.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#ffffff" : "#1e293b") + ";");
            HBox.setHgrow(lblName, Priority.ALWAYS);

            Label lblBadge = new Label(session.format().toUpperCase());
            lblBadge.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #10b981; -fx-background-color: rgba(16, 185, 129, 0.1); -fx-padding: 2px 6px; -fx-background-radius: 4px;");
            header.getChildren().addAll(lblName, lblBadge);

            // Path & Size
            Label lblPath = new Label("Source: " + session.sourceFilePath() + " (" + fileSize + ")");
            lblPath.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (isDark ? "#94a3b8" : "#64748b") + ";");
            lblPath.setWrapText(true);

            // Total messages
            Label lblMetrics = new Label(String.format("Migrated: %d messages fully converted", session.totalMessages()));
            lblMetrics.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: " + (isDark ? "#cbd5e1" : "#475569") + ";");

            // Destination
            Label lblDest = new Label("Destination: " + session.destinationPath());
            lblDest.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (isDark ? "#94a3b8" : "#64748b") + ";");
            lblDest.setWrapText(true);

            // Time
            String rawTs = session.timestamp();
            String friendlyTs = rawTs;
            if (rawTs != null && !rawTs.isEmpty()) {
                try {
                    java.time.LocalDateTime parsed = java.time.LocalDateTime.parse(
                            rawTs,
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    );
                    friendlyTs = parsed.format(
                            java.time.format.DateTimeFormatter.ofPattern(
                                     "dd MMM yyyy, hh:mm a",
                                    java.util.Locale.getDefault()
                            )
                    );
                } catch (Exception ignored) {}
            }
            Label lblTime = new Label("Completed on: " + friendlyTs);
            lblTime.setStyle("-fx-font-size: 9px; -fx-text-fill: #94a3b8;");

            // Action Buttons
            HBox actionBox = new HBox(10);
            actionBox.setAlignment(Pos.CENTER_LEFT);

            // Clicking "Re-Run" re-populates settings and runs a clean conversion
            MFXButton btnReRun = new MFXButton("Re-Run");
            btnReRun.getStyleClass().addAll("action-btn", "btn-primary");
            btnReRun.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 6px 12px; -fx-font-weight: bold;");
            HBox.setHgrow(btnReRun, Priority.ALWAYS);
            btnReRun.setMaxWidth(Double.MAX_VALUE);
            btnReRun.setOnAction(e -> {
                controller.rerunMigrationSession(session);
            });

            // Clicking "Delete" cleans up tracking info
            MFXButton btnDelete = new MFXButton("Delete");
            btnDelete.getStyleClass().addAll("action-btn", "btn-danger-outline");
            btnDelete.setStyle("-fx-font-size: 11px; -fx-padding: 6px 12px; -fx-font-weight: bold;");
            btnDelete.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Delete History Record");
                confirm.setHeaderText("Delete this history record?");
                confirm.setContentText("This will delete the conversion history tracking info from the database. It will not delete your exported files.\n\nAre you sure?");
                confirm.initOwner(getScene() != null ? getScene().getWindow() : null);
                confirm.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        com.pstconverter.util.SettingsManager.clearMigrationProgressForFile(session.sourceFilePath(), session.format(), session.destinationPath());
                        refreshConversionHistory();
                    }
                });
            });

            actionBox.getChildren().addAll(btnReRun, btnDelete);

            card.getChildren().addAll(header, lblPath, lblMetrics, lblDest, lblTime, actionBox);
            historyPane.getChildren().add(card);
        }
    }
}
