package com.pstconverter.view;

import com.pstconverter.controller.MainController;
import com.pstconverter.core.model.SourceFileModel;
import com.pstconverter.model.MailMessage;
import com.pstconverter.model.MailboxFolder;
import com.pstconverter.core.adapter.SourceAdapter;
import com.pstconverter.core.adapter.SourceAdapterFactory;
import com.pstconverter.util.SettingsManager;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXCheckbox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.controls.MFXProgressSpinner;
import io.github.palexdev.materialfx.enums.FloatMode;
import javafx.scene.web.WebView;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.util.*;
import java.util.function.BiConsumer;

public class Step2ExplorerView extends BorderPane {

    private final MainController controller;

    private TreeView<String> folderTreeView;
    private MFXCheckbox cbSelectAll;
    private javafx.beans.value.ChangeListener<Boolean> currentIndeterminateListener;
    private TableView<MailMessage> emailTableView;
    private final Map<TreeItem<String>, Integer> folderMessageCounts = new HashMap<>();
    private final Map<TreeItem<String>, Boolean> systemFolders = new HashMap<>();
    private boolean hideEmptyFolders = Boolean.parseBoolean(SettingsManager.getSetting("ignore_empty_folders", "false"));
    private MFXButton btnHideEmpty;

    // Pagination & Search
    private MFXTextField tfSearch;
    private MFXButton btnFirstPage;
    private MFXButton btnPrevPage;
    private MFXButton btnNextPage;
    private MFXButton btnLastPage;
    private Label lblPageInfo;
    private Label lblShowRange;
    private Label emailCountBadge;
    private final List<MailMessage> allEmailsForSelectedFolder = new ArrayList<>();
    private final List<MailMessage> filteredEmailsList = new ArrayList<>();
    private int currentPage = 1;
    private final int pageSize = 50;

    public Step2ExplorerView(MainController controller) {
        this.controller = controller;
        initializeUI();
    }

    public Integer getFolderMessageCount(TreeItem<String> item) {
        if (item == null) return null;
        return folderMessageCounts.get(item);
    }


    @SuppressWarnings("unchecked")
    private void initializeUI() {
        // Main layout: 20% tree | 80% email table
        SplitPane horizontalSplit = new SplitPane();
        horizontalSplit.setDividerPositions(0.28);

        // ── LEFT: Folder Tree (28%) ────────────────────────────────────────────────
        VBox treeBox = new VBox(10);
        treeBox.setPadding(new Insets(0, 10, 0, 0));
        treeBox.prefWidthProperty().bind(horizontalSplit.widthProperty().multiply(0.28));

        VBox treeHeaderContainer = new VBox(8);

        boolean isDark = controller.isDarkMode();

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label lblFolders = new Label(" MAILBOX FOLDERS");
        Label iconFolders = com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.FOLDER_OPEN, 16);
        iconFolders.setStyle("-fx-text-fill: " + (isDark ? "#818cf8" : "#4f46e5") + ";");
        lblFolders.setGraphic(iconFolders);
        lblFolders.getStyleClass().add("section-label");
        lblFolders.setStyle(
            "-fx-font-size: 12px; -fx-font-weight: bold; " +
            "-fx-text-fill: " + (isDark ? "#c7d2fe" : "#3730a3") + "; " +
            "-fx-background-color: " + (isDark ? "rgba(99, 102, 241, 0.12)" : "rgba(99, 102, 241, 0.08)") + "; " +
            "-fx-padding: 4px 10px; -fx-background-radius: 12px; " +
            "-fx-border-color: " + (isDark ? "rgba(99, 102, 241, 0.25)" : "rgba(99, 102, 241, 0.18)") + "; -fx-border-radius: 12px; -fx-border-width: 1px;"
        );

        cbSelectAll = new MFXCheckbox("Select All");
        cbSelectAll.getStyleClass().addAll("filter-checkbox", "select-all-cb");
        cbSelectAll.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#ffffff" : "#0f172a") + ";");

        Region treeHeaderSpacer = new Region();
        HBox.setHgrow(treeHeaderSpacer, Priority.ALWAYS);

        titleRow.getChildren().addAll(lblFolders, treeHeaderSpacer, cbSelectAll);

        GridPane actionGrid = new GridPane();
        actionGrid.setHgap(6);
        actionGrid.setVgap(6);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        actionGrid.getColumnConstraints().addAll(col1, col2);

        MFXButton btnExpandAll = new MFXButton(" Expand");
        Label iconExpand = com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.ADD, 14);
        iconExpand.setStyle("-fx-text-fill: " + (isDark ? "#818cf8" : "#4f46e5") + ";");
        btnExpandAll.setGraphic(iconExpand);
        String expandBaseStyle = "-fx-background-color: " + (isDark ? "rgba(99, 102, 241, 0.15)" : "rgba(99, 102, 241, 0.08)") + "; " +
                                 "-fx-text-fill: " + (isDark ? "#a5b4fc" : "#4f46e5") + "; " +
                                 "-fx-border-color: " + (isDark ? "rgba(99, 102, 241, 0.35)" : "rgba(99, 102, 241, 0.25)") + "; " +
                                 "-fx-border-radius: 10px; -fx-background-radius: 10px; -fx-font-size: 11.5px; -fx-font-weight: bold; -fx-padding: 6px 8px; -fx-cursor: hand;";
        btnExpandAll.setStyle(expandBaseStyle);
        btnExpandAll.setMaxWidth(Double.MAX_VALUE);
        btnExpandAll.setTooltip(new Tooltip("Expand All Folders"));
        btnExpandAll.setOnAction(e -> setAllNodesExpanded(folderTreeView.getRoot(), true));
        btnExpandAll.setOnMouseEntered(ev -> btnExpandAll.setStyle(expandBaseStyle + "-fx-background-color: " + (isDark ? "rgba(99, 102, 241, 0.3)" : "rgba(99, 102, 241, 0.18)") + ";"));
        btnExpandAll.setOnMouseExited(ev -> btnExpandAll.setStyle(expandBaseStyle));

        MFXButton btnCollapseAll = new MFXButton(" Collapse");
        Label iconCollapse = com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.CLEAR, 14);
        iconCollapse.setStyle("-fx-text-fill: " + (isDark ? "#fbbf24" : "#d97706") + ";");
        btnCollapseAll.setGraphic(iconCollapse);
        String collapseBaseStyle = "-fx-background-color: " + (isDark ? "rgba(245, 158, 11, 0.15)" : "rgba(245, 158, 11, 0.08)") + "; " +
                                   "-fx-text-fill: " + (isDark ? "#fcd34d" : "#d97706") + "; " +
                                   "-fx-border-color: " + (isDark ? "rgba(245, 158, 11, 0.35)" : "rgba(245, 158, 11, 0.25)") + "; " +
                                   "-fx-border-radius: 10px; -fx-background-radius: 10px; -fx-font-size: 11.5px; -fx-font-weight: bold; -fx-padding: 6px 8px; -fx-cursor: hand;";
        btnCollapseAll.setStyle(collapseBaseStyle);
        btnCollapseAll.setMaxWidth(Double.MAX_VALUE);
        btnCollapseAll.setTooltip(new Tooltip("Collapse All Folders"));
        btnCollapseAll.setOnAction(e -> {
            if (folderTreeView.getRoot() != null) {
                for (TreeItem<String> child : folderTreeView.getRoot().getChildren()) {
                    setAllNodesExpanded(child, false);
                }
            }
        });
        btnCollapseAll.setOnMouseEntered(ev -> btnCollapseAll.setStyle(collapseBaseStyle + "-fx-background-color: " + (isDark ? "rgba(245, 158, 11, 0.3)" : "rgba(245, 158, 11, 0.18)") + ";"));
        btnCollapseAll.setOnMouseExited(ev -> btnCollapseAll.setStyle(collapseBaseStyle));

        btnHideEmpty = new MFXButton(hideEmptyFolders ? " Show Empty" : " Hide Empty");
        Label iconHide = com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.VISIBILITY, 14);
        iconHide.setStyle("-fx-text-fill: " + (isDark ? "#34d399" : "#059669") + ";");
        btnHideEmpty.setGraphic(iconHide);
        String hideBaseStyle = "-fx-background-color: " + (isDark ? "rgba(16, 185, 129, 0.15)" : "rgba(16, 185, 129, 0.08)") + "; " +
                               "-fx-text-fill: " + (isDark ? "#6ee7b7" : "#059669") + "; " +
                               "-fx-border-color: " + (isDark ? "rgba(16, 185, 129, 0.35)" : "rgba(16, 185, 129, 0.25)") + "; " +
                               "-fx-border-radius: 10px; -fx-background-radius: 10px; -fx-font-size: 11.5px; -fx-font-weight: bold; -fx-padding: 6px 8px; -fx-cursor: hand;";
        btnHideEmpty.setStyle(hideBaseStyle);
        btnHideEmpty.setMaxWidth(Double.MAX_VALUE);
        btnHideEmpty.setTooltip(new Tooltip("Toggle hiding of empty folders"));
        btnHideEmpty.setOnAction(e -> {
            hideEmptyFolders = !hideEmptyFolders;
            SettingsManager.saveSetting("ignore_empty_folders", String.valueOf(hideEmptyFolders));
            btnHideEmpty.setText(hideEmptyFolders ? " Show Empty" : " Hide Empty");
            rebuildTreeHierarchy();
        });
        btnHideEmpty.setOnMouseEntered(ev -> btnHideEmpty.setStyle(hideBaseStyle + "-fx-background-color: " + (isDark ? "rgba(16, 185, 129, 0.3)" : "rgba(16, 185, 129, 0.18)") + ";"));
        btnHideEmpty.setOnMouseExited(ev -> btnHideEmpty.setStyle(hideBaseStyle));

        MFXButton btnExportTree = new MFXButton(" Export Tree");
        Label iconExport = com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.DESCRIPTION, 14);
        iconExport.setStyle("-fx-text-fill: " + (isDark ? "#60a5fa" : "#2563eb") + ";");
        btnExportTree.setGraphic(iconExport);
        String exportBaseStyle = "-fx-background-color: " + (isDark ? "rgba(59, 130, 246, 0.15)" : "rgba(59, 130, 246, 0.08)") + "; " +
                                 "-fx-text-fill: " + (isDark ? "#93c5fd" : "#2563eb") + "; " +
                                 "-fx-border-color: " + (isDark ? "rgba(59, 130, 246, 0.35)" : "rgba(59, 130, 246, 0.25)") + "; " +
                                 "-fx-border-radius: 10px; -fx-background-radius: 10px; -fx-font-size: 11.5px; -fx-font-weight: bold; -fx-padding: 6px 8px; -fx-cursor: hand;";
        btnExportTree.setStyle(exportBaseStyle);
        btnExportTree.setMaxWidth(Double.MAX_VALUE);
        btnExportTree.setTooltip(new Tooltip("Export selected tree paths to a text report"));
        btnExportTree.setOnAction(e -> handleExportTree());
        btnExportTree.setOnMouseEntered(ev -> btnExportTree.setStyle(exportBaseStyle + "-fx-background-color: " + (isDark ? "rgba(59, 130, 246, 0.3)" : "rgba(59, 130, 246, 0.18)") + ";"));
        btnExportTree.setOnMouseExited(ev -> btnExportTree.setStyle(exportBaseStyle));

        actionGrid.add(btnExpandAll, 0, 0);
        actionGrid.add(btnCollapseAll, 1, 0);
        actionGrid.add(btnHideEmpty, 0, 1);
        actionGrid.add(btnExportTree, 1, 1);

        treeHeaderContainer.getChildren().addAll(titleRow, actionGrid);

        folderTreeView = new TreeView<>();
        folderTreeView.getStyleClass().add("mailbox-table");

        folderTreeView.setCellFactory(tv -> new CheckBoxTreeCell<String>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("tree-cell-sourcefile", "tree-cell-system", "tree-cell-custom", "tree-cell-uigroup");

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    TreeItem<String> treeItem = getTreeItem();
                    Integer count = folderMessageCounts.get(treeItem);
                    String countStr = (count != null) ? " (" + count + ")" : "";
                    boolean isDark = controller.isDarkMode();

                    String emojiPrefix = "";
                    String customTextColor = null;
                    boolean isBold = false;

                    String itemLower = item.toLowerCase().trim();

                    if (treeItem.getParent() == null) {
                        emojiPrefix = "🌐";
                        customTextColor = isDark ? "#818cf8" : "#4f46e5";
                        isBold = true;
                    } else if (isSourceFileNode(treeItem)) {
                        emojiPrefix = "📦";
                        customTextColor = isDark ? "#fbbf24" : "#d97706";
                        isBold = true;
                    } else {
                        // Check for special item types first (Contact, Calendar, Task, Note)
                        if (itemLower.contains("contact")) {
                            emojiPrefix = "👥";
                            customTextColor = isDark ? "#34d399" : "#059669";
                            isBold = true;
                        } else if (itemLower.contains("calendar") || itemLower.contains("appointment")) {
                            emojiPrefix = "📅";
                            customTextColor = isDark ? "#fbbf24" : "#d97706";
                            isBold = true;
                        } else if (itemLower.contains("task")) {
                            emojiPrefix = "✅";
                            customTextColor = isDark ? "#60a5fa" : "#2563eb";
                            isBold = true;
                        } else if (itemLower.contains("note")) {
                            emojiPrefix = "📓";
                            customTextColor = isDark ? "#f472b6" : "#db2777";
                            isBold = true;
                        } else if (isSourceNativeFolder(treeItem)) {
                            Boolean isSys = systemFolders.get(treeItem);
                            File sourceFile = getSourceFileForNode(treeItem);
                            SourceAdapter parser = null;
                            if (sourceFile != null && sourceFile.exists()) {
                                parser = SourceAdapterFactory.getAdapterForFile(sourceFile);
                            }
                            if (isSys != null && isSys && parser != null) {
                                emojiPrefix = parser.getFolderEmoji(item);
                            } else {
                                emojiPrefix = "📁";
                            }

                            // Color coding rule based on data availability
                            if (count != null && count > 0) {
                                // Has data -> Vibrant highlight color
                                customTextColor = isDark ? "#818cf8" : "#4f46e5";
                                isBold = true;
                            } else {
                                // Empty / No data -> Normal black / dark neutral text
                                customTextColor = isDark ? "#94a3b8" : "#0f172a";
                                isBold = false;
                            }
                        } else {
                            if (item.equalsIgnoreCase("All Mailboxes")) {
                                emojiPrefix = "🌐";
                            } else if (item.contains("Mailbox File")) {
                                emojiPrefix = "🗂️";
                            } else {
                                emojiPrefix = "📂";
                            }
                            customTextColor = isDark ? "#c7d2fe" : "#334155";
                            isBold = true;
                        }
                    }

                    setText(emojiPrefix + " " + item + countStr);
                    StringBuilder styleBuf = new StringBuilder();
                    if (customTextColor != null) {
                        styleBuf.append("-fx-text-fill: ").append(customTextColor).append("; ");
                    }
                    if (isBold) {
                        styleBuf.append("-fx-font-weight: bold; ");
                    } else {
                        styleBuf.append("-fx-font-weight: normal; ");
                    }
                    setStyle(styleBuf.toString());
                }
            }
        });

        folderTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadFolderEmails(newVal);
            }
        });

        treeBox.getChildren().addAll(treeHeaderContainer, folderTreeView);
        VBox.setVgrow(folderTreeView, Priority.ALWAYS);

        // ── RIGHT: Email Panel (80%) ──────────────────────────────────────────────
        VBox emailPanelBox = new VBox(0);
        emailPanelBox.getStyleClass().add("content-card");

        HBox emailToolbar = new HBox(12);
        emailToolbar.setAlignment(Pos.CENTER_LEFT);
        emailToolbar.setPadding(new Insets(10, 16, 10, 16));
        emailToolbar.getStyleClass().add("card-header");

        Label iconLbl = com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.INBOX, 16);
        iconLbl.setStyle("-fx-text-fill: " + (controller.isDarkMode() ? "#818cf8" : "#4f46e5") + ";");

        Label lblEmails = new Label("MESSAGES");
        lblEmails.getStyleClass().add("card-header-title");

        emailCountBadge = new Label("0 items");
        emailCountBadge.getStyleClass().add("count-badge");

        Region emailToolbarSpacer = new Region();
        HBox.setHgrow(emailToolbarSpacer, Priority.ALWAYS);

        tfSearch = new MFXTextField();
        tfSearch.setFloatMode(FloatMode.DISABLED);
        tfSearch.setPromptText("🔍 Search emails...");
        tfSearch.getStyleClass().add("search-field");
        tfSearch.setPrefWidth(200);
        tfSearch.textProperty().addListener((obs, oldVal, newVal) -> handleSearchChange(newVal));

        Label lblHint = new Label("Double-click to open");
        lblHint.setGraphic(com.pstconverter.util.MaterialIcons.icon(com.pstconverter.util.MaterialIcons.INFO, 13));
        lblHint.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (controller.isDarkMode() ? "#a5b4fc" : "#4f46e5") + "; -fx-font-style: italic;");

        emailToolbar.getChildren().addAll(iconLbl, lblEmails, emailCountBadge, emailToolbarSpacer, tfSearch, lblHint);

        emailTableView = new TableView<>();
        emailTableView.getStyleClass().add("email-table");
        emailTableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        emailTableView.setFixedCellSize(32); // Fixed row height 32px for silky smooth 60fps scrolling

        Label placeholder = new Label("Select a folder in the tree to view messages");
        placeholder.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        emailTableView.setPlaceholder(placeholder);

        // # - row index column
        TableColumn<MailMessage, String> indexCol = new TableColumn<>("#");
        indexCol.setCellValueFactory(cd -> new SimpleStringProperty(""));
        indexCol.setCellFactory(col -> new TableCell<MailMessage, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else {
                    setText(String.valueOf(getIndex() + 1 + (currentPage - 1) * pageSize));
                }
            }
        });
        indexCol.setMinWidth(45);
        indexCol.setMaxWidth(45);
        indexCol.setPrefWidth(45);
        indexCol.setStyle("-fx-alignment: CENTER;");
        indexCol.setSortable(false);

        // Type column
        TableColumn<MailMessage, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getItemType()));
        typeCol.setCellFactory(col -> new TableCell<MailMessage, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    Label badge = new Label();
                    boolean isDark = controller.isDarkMode();
                    badge.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-padding: 2px 8px; -fx-background-radius: 10px;");
                    switch (item) {
                        case "Contact":
                            badge.setText("👥 Contact");
                            badge.setStyle(badge.getStyle() + "-fx-background-color: " + (isDark ? "rgba(16, 185, 129, 0.2)" : "rgba(16, 185, 129, 0.12)") + "; -fx-text-fill: " + (isDark ? "#34d399" : "#059669") + ";");
                            break;
                        case "Calendar":
                            badge.setText("📅 Calendar");
                            badge.setStyle(badge.getStyle() + "-fx-background-color: " + (isDark ? "rgba(245, 158, 11, 0.2)" : "rgba(245, 158, 11, 0.12)") + "; -fx-text-fill: " + (isDark ? "#fbbf24" : "#d97706") + ";");
                            break;
                        case "Task":
                            badge.setText("✅ Task");
                            badge.setStyle(badge.getStyle() + "-fx-background-color: " + (isDark ? "rgba(59, 130, 246, 0.2)" : "rgba(59, 130, 246, 0.12)") + "; -fx-text-fill: " + (isDark ? "#60a5fa" : "#2563eb") + ";");
                            break;
                        case "Note":
                            badge.setText("📓 Note");
                            badge.setStyle(badge.getStyle() + "-fx-background-color: " + (isDark ? "rgba(236, 72, 153, 0.2)" : "rgba(236, 72, 153, 0.12)") + "; -fx-text-fill: " + (isDark ? "#f472b6" : "#db2777") + ";");
                            break;
                        default:
                            badge.setText("📧 Email");
                            badge.setStyle(badge.getStyle() + "-fx-background-color: " + (isDark ? "rgba(139, 92, 246, 0.2)" : "rgba(139, 92, 246, 0.12)") + "; -fx-text-fill: " + (isDark ? "#a78bfa" : "#7c3aed") + ";");
                            break;
                    }
                    setText(null);
                    setGraphic(badge);
                    setTooltip(new Tooltip(item + " Item"));
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });
        typeCol.setMinWidth(110);
        typeCol.setMaxWidth(110);
        typeCol.setPrefWidth(110);
        typeCol.setStyle("-fx-alignment: CENTER-LEFT;");

        TableColumn<MailMessage, String> fromCol = new TableColumn<>("From");
        fromCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFrom()));
        fromCol.setPrefWidth(220);
        fromCol.setMinWidth(140);
        fromCol.setCellFactory(col -> new TableCell<MailMessage, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().remove("from-column-cell");
                } else {
                    setText(item);
                    if (!getStyleClass().contains("from-column-cell")) {
                        getStyleClass().add("from-column-cell");
                    }
                }
            }
        });

        TableColumn<MailMessage, String> subjectCol = new TableColumn<>("Subject");
        subjectCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getSubject()));
        subjectCol.setPrefWidth(420);
        subjectCol.setMinWidth(200);
        subjectCol.setCellFactory(col -> new TableCell<MailMessage, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    setStyle("");
                } else {
                    MailMessage msg = null;
                    try {
                        msg = getTableView().getItems().get(getIndex());
                    } catch (Exception ignored) {}
                    if (msg != null && msg.getAttachments() != null && !msg.getAttachments().isEmpty()) {
                        setText("📎 " + item);
                        setStyle("-fx-text-fill: #eab308; -fx-font-weight: bold;");
                        setTooltip(new Tooltip("Attachments (" + msg.getAttachments().size() + "):\n" + String.join("\n", msg.getAttachments())));
                    } else {
                        setText(item);
                        setStyle("");
                        setTooltip(null);
                    }
                }
            }
        });

        TableColumn<MailMessage, String> dateCol = new TableColumn<>("Date Received");
        dateCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getDate()));
        dateCol.setPrefWidth(190);
        dateCol.setMinWidth(170);
        dateCol.setStyle("-fx-alignment: CENTER-LEFT;");
        dateCol.setCellFactory(col -> new TableCell<MailMessage, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
            }
        });

        emailTableView.getColumns().addAll(indexCol, typeCol, fromCol, subjectCol, dateCol);

        emailTableView.setRowFactory(tv -> {
            TableRow<MailMessage> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    showEmailDetailPopup(row.getItem());
                }
            });
            return row;
        });

        // Pagination Bar
        HBox paginationBar = new HBox(10);
        paginationBar.setAlignment(Pos.CENTER);
        paginationBar.setPadding(new Insets(10, 16, 10, 16));
        paginationBar.getStyleClass().add("pagination-bar");
        paginationBar.setStyle("-fx-background-color: rgba(255, 255, 255, 0.02); -fx-border-color: rgba(255, 255, 255, 0.06); -fx-border-width: 1 0 0 0;");

        btnFirstPage = new MFXButton("«");
        btnFirstPage.getStyleClass().addAll("action-btn", "btn-secondary");
        btnFirstPage.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4px 10px;");
        btnFirstPage.setOnAction(e -> {
            currentPage = 1;
            updatePagination();
        });

        btnPrevPage = new MFXButton("‹");
        btnPrevPage.getStyleClass().addAll("action-btn", "btn-secondary");
        btnPrevPage.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4px 10px;");
        btnPrevPage.setOnAction(e -> {
            if (currentPage > 1) {
                currentPage--;
                updatePagination();
            }
        });

        lblPageInfo = new Label("Page 1 of 1");
        lblPageInfo.getStyleClass().add("pagination-label");
        lblPageInfo.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + (controller.isDarkMode() ? "#ffffff" : "#1e293b") + ";");

        btnNextPage = new MFXButton("›");
        btnNextPage.getStyleClass().addAll("action-btn", "btn-secondary");
        btnNextPage.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4px 10px;");
        btnNextPage.setOnAction(e -> {
            int totalItems = filteredEmailsList.size();
            int totalPages = (int) Math.ceil((double) totalItems / pageSize);
            if (currentPage < totalPages) {
                currentPage++;
                updatePagination();
            }
        });

        btnLastPage = new MFXButton("»");
        btnLastPage.getStyleClass().addAll("action-btn", "btn-secondary");
        btnLastPage.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4px 10px;");
        btnLastPage.setOnAction(e -> {
            int totalItems = filteredEmailsList.size();
            int totalPages = (int) Math.ceil((double) totalItems / pageSize);
            currentPage = totalPages > 0 ? totalPages : 1;
            updatePagination();
        });

        lblShowRange = new Label("Showing 0-0 of 0");
        lblShowRange.getStyleClass().add("pagination-label");
        lblShowRange.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: " + (controller.isDarkMode() ? "#cbd5e1" : "#64748b") + ";");

        Region paginationSpacer = new Region();
        HBox.setHgrow(paginationSpacer, Priority.ALWAYS);

        paginationBar.getChildren().addAll(lblShowRange, paginationSpacer, btnFirstPage, btnPrevPage, lblPageInfo, btnNextPage, btnLastPage);

        emailPanelBox.getChildren().addAll(emailToolbar, emailTableView, paginationBar);
        VBox.setVgrow(emailTableView, Priority.ALWAYS);

        horizontalSplit.getItems().addAll(treeBox, emailPanelBox);
        this.setCenter(horizontalSplit);
    }

    public void rebuildTreeHierarchy() {
        this.hideEmptyFolders = Boolean.parseBoolean(SettingsManager.getSetting("ignore_empty_folders", "false"));
        if (btnHideEmpty != null) {
            btnHideEmpty.setText(hideEmptyFolders ? " Show Empty" : " Hide Empty");
        }
        boolean hasSavedState = (folderTreeView != null && folderTreeView.getRoot() != null);
        Set<String> expandedPaths = new HashSet<>();
        Set<String> checkedPaths = new HashSet<>();
        if (hasSavedState) {
            saveTreeStates(folderTreeView.getRoot(), new ArrayList<>(), expandedPaths, checkedPaths);
        }

        folderMessageCounts.clear();
        systemFolders.clear();
        CheckBoxTreeItem<String> rootNode = buildTreeStructure(null);

        if (cbSelectAll != null) {
            cbSelectAll.selectedProperty().unbind();
            cbSelectAll.indeterminateProperty().unbind();
            if (folderTreeView != null && folderTreeView.getRoot() instanceof CheckBoxTreeItem && currentIndeterminateListener != null) {
                ((CheckBoxTreeItem<String>) folderTreeView.getRoot()).indeterminateProperty().removeListener(currentIndeterminateListener);
            }
            cbSelectAll.selectedProperty().bindBidirectional(rootNode.selectedProperty());
            
            currentIndeterminateListener = (obs, oldVal, newVal) -> {
                cbSelectAll.setIndeterminate(newVal);
            };
            rootNode.indeterminateProperty().addListener(currentIndeterminateListener);
            cbSelectAll.setIndeterminate(rootNode.isIndeterminate());
            cbSelectAll.setSelected(true);
        }

        if (hideEmptyFolders) {
            pruneEmptyNodes(rootNode);
        }

        if (hasSavedState && rootNode != null) {
            restoreTreeStates(rootNode, new ArrayList<>(), expandedPaths, checkedPaths);
        }

        folderTreeView.setRoot(rootNode);
        folderTreeView.setShowRoot(false);
    }

    private CheckBoxTreeItem<String> buildTreeStructure(BiConsumer<SourceFileModel, Integer> progressCallback) {
        CheckBoxTreeItem<String> rootNode = new CheckBoxTreeItem<>("All Mailboxes");
        rootNode.setSelected(true);
        rootNode.setExpanded(true);

        CheckBoxTreeItem<String> singleFileRoot = new CheckBoxTreeItem<>("Mailbox File (Single)");
        singleFileRoot.setSelected(true);
        singleFileRoot.setExpanded(true);

        CheckBoxTreeItem<String> folderRoot = new CheckBoxTreeItem<>("Mailbox File (Folder)");
        folderRoot.setSelected(true);
        folderRoot.setExpanded(true);

        Map<String, CheckBoxTreeItem<String>> folderNodesMap = new HashMap<>();
        boolean hasSingleFiles = false;
        boolean hasFolderFiles = false;

        List<SourceFileModel> validFiles = new ArrayList<>();
        for (SourceFileModel item : controller.getFileList()) {
            if (item.isValid()) validFiles.add(item);
        }
        int total = validFiles.size();

        for (int i = 0; i < total; i++) {
            SourceFileModel item = validFiles.get(i);
            if (progressCallback != null) {
                progressCallback.accept(item, i);
            }

            CheckBoxTreeItem<String> fileNode = new CheckBoxTreeItem<>(item.getFileName());
            fileNode.setSelected(true);
            fileNode.setExpanded(false);

            File file = new File(item.getFilePath());
            SourceAdapter parser = SourceAdapterFactory.getAdapterForFile(file);
            if (parser != null) {
                try {
                    MailboxFolder parsedStructure = parser.parseFolderStructure(file, null);
                    if (parsedStructure != null && !parsedStructure.getChildren().isEmpty()) {
                        for (MailboxFolder childFolder : parsedStructure.getChildren()) {
                            fileNode.getChildren().add(buildTreeItemFromModel(childFolder));
                        }
                    } else {
                        item.setStatus("Empty or Corrupt");
                        CheckBoxTreeItem<String> errNode = new CheckBoxTreeItem<>("⚠️ Empty or Corrupt Mailbox");
                        errNode.setSelected(true);
                        fileNode.getChildren().add(errNode);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: Failed to parse mailbox structure: " + e.getMessage());
                    item.setStatus("Parsing Error");
                    CheckBoxTreeItem<String> errNode = new CheckBoxTreeItem<>("⚠️ Unparseable: " + e.getMessage());
                    errNode.setSelected(true);
                    fileNode.getChildren().add(errNode);
                }
            } else {
                item.setStatus("Unsupported Format");
                CheckBoxTreeItem<String> errNode = new CheckBoxTreeItem<>("⚠️ Unsupported Mailbox Format");
                errNode.setSelected(true);
                fileNode.getChildren().add(errNode);
            }

            if (item.getImportSourceType().equalsIgnoreCase("File")) {
                singleFileRoot.getChildren().add(fileNode);
                hasSingleFiles = true;
            } else if (item.getImportSourceType().equalsIgnoreCase("Folder")) {
                String folderName = item.getSourceFolderName();
                CheckBoxTreeItem<String> folderSubRoot = folderNodesMap.get(folderName);
                if (folderSubRoot == null) {
                    folderSubRoot = new CheckBoxTreeItem<>(folderName);
                    folderSubRoot.setSelected(true);
                    folderSubRoot.setExpanded(true);
                    folderNodesMap.put(folderName, folderSubRoot);
                    folderRoot.getChildren().add(folderSubRoot);
                }
                folderSubRoot.getChildren().add(fileNode);
                hasFolderFiles = true;
            }
        }

        if (hasSingleFiles) {
            rootNode.getChildren().add(singleFileRoot);
        }
        if (hasFolderFiles) {
            rootNode.getChildren().add(folderRoot);
        }

        return rootNode;
    }

    public void startAnalysis(Runnable onSucceeded) {
        long validCount = controller.getFileList().stream().filter(SourceFileModel::isValid).count();
        if (validCount == 0) {
            if (onSucceeded != null) onSucceeded.run();
            return;
        }

        boolean hasSavedState = (folderTreeView != null && folderTreeView.getRoot() != null);
        Set<String> expandedPaths = new HashSet<>();
        Set<String> checkedPaths = new HashSet<>();
        if (hasSavedState) {
            saveTreeStates(folderTreeView.getRoot(), new ArrayList<>(), expandedPaths, checkedPaths);
        }

        Stage loadingStage = new Stage(StageStyle.UNDECORATED);
        loadingStage.initOwner(controller.getPrimaryStage());
        loadingStage.initModality(Modality.APPLICATION_MODAL);
        loadingStage.setResizable(false);

        MFXProgressSpinner spinner = new MFXProgressSpinner();
        spinner.setMaxSize(52, 52);

        Label titleLbl = new Label("Analyzing Mailbox Files");
        titleLbl.getStyleClass().add("loading-title");

        Label hintLbl = new Label("Reading folder structure, please wait…");
        hintLbl.getStyleClass().add("loading-hint");

        Label fileNameLbl = new Label("");
        fileNameLbl.getStyleClass().add("loading-filename");
        fileNameLbl.setWrapText(true);
        fileNameLbl.setMaxWidth(340);

        Label counterLbl = new Label("");
        counterLbl.getStyleClass().add("loading-counter");

        javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar(-1);
        progressBar.setPrefWidth(300);
        progressBar.setStyle("-fx-accent: #6366f1;");

        VBox loadingBox = new VBox(14);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPadding(new Insets(36, 44, 36, 44));
        loadingBox.getStyleClass().add("loading-box");
        loadingBox.getChildren().addAll(spinner, titleLbl, hintLbl, progressBar, fileNameLbl, counterLbl);

        StackPane backdropPane = new StackPane(loadingBox);
        backdropPane.getStyleClass().add("loading-backdrop");
        backdropPane.setPrefSize(480, 320);
        backdropPane.setAlignment(Pos.CENTER);

        Scene loadingScene = new Scene(backdropPane, 480, 320);
        loadingScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        try {
            String css = controller.getActiveThemeStylesheet();
            loadingScene.getStylesheets().add(css);
        } catch (Exception ignored) {}
        loadingStage.setScene(loadingScene);

        Task<CheckBoxTreeItem<String>> parseTask = new Task<>() {
            @Override
            protected CheckBoxTreeItem<String> call() throws Exception {
                List<SourceFileModel> validFiles = new ArrayList<>();
                for (SourceFileModel item : controller.getFileList()) {
                    if (item.isValid()) validFiles.add(item);
                }
                final int total = validFiles.size();

                return buildTreeStructure((item, index) -> {
                    final String fname = item.getFileName();
                    final String counter = (index + 1) + " / " + total;
                    Platform.runLater(() -> {
                        fileNameLbl.setText(fname);
                        counterLbl.setText("File " + counter);
                        progressBar.setProgress((double) (index + 1) / total);
                    });
                });
            }
        };

        parseTask.setOnSucceeded(e -> {
            CheckBoxTreeItem<String> builtRoot = parseTask.getValue();

            if (cbSelectAll != null) {
                cbSelectAll.selectedProperty().unbind();
                cbSelectAll.indeterminateProperty().unbind();
                if (folderTreeView != null && folderTreeView.getRoot() instanceof CheckBoxTreeItem && currentIndeterminateListener != null) {
                    ((CheckBoxTreeItem<String>) folderTreeView.getRoot()).indeterminateProperty().removeListener(currentIndeterminateListener);
                }
                cbSelectAll.selectedProperty().bindBidirectional(builtRoot.selectedProperty());
                
                currentIndeterminateListener = (obs, oldVal, newVal) -> {
                    cbSelectAll.setIndeterminate(newVal);
                };
                builtRoot.indeterminateProperty().addListener(currentIndeterminateListener);
                cbSelectAll.setIndeterminate(builtRoot.isIndeterminate());
                cbSelectAll.setSelected(true);
            }

            if (hideEmptyFolders) {
                pruneEmptyNodes(builtRoot);
            }

            if (hasSavedState && builtRoot != null) {
                restoreTreeStates(builtRoot, new ArrayList<>(), expandedPaths, checkedPaths);
            }

            folderTreeView.setRoot(builtRoot);
            folderTreeView.setShowRoot(false);

            loadingStage.close();
            if (onSucceeded != null) onSucceeded.run();
        });

        parseTask.setOnFailed(e -> {
            loadingStage.close();
            Throwable ex = parseTask.getException();
            controller.showAlert(Alert.AlertType.ERROR, "Error Loading Mailbox",
                    "An error occurred while reading the data files:\n" +
                            (ex != null ? ex.getMessage() : "Unknown error"));
        });

        Thread bgThread = new Thread(parseTask);
        bgThread.setDaemon(true);
        bgThread.start();

        loadingStage.show();
    }

    private CheckBoxTreeItem<String> buildTreeItemFromModel(MailboxFolder folderModel) {
        CheckBoxTreeItem<String> node = new CheckBoxTreeItem<>(folderModel.getName());
        node.setSelected(true);
        node.setExpanded(false);
        folderMessageCounts.put(node, folderModel.getContentCount());
        systemFolders.put(node, folderModel.isSystemFolder());

        for (MailboxFolder child : folderModel.getChildren()) {
            node.getChildren().add(buildTreeItemFromModel(child));
        }
        return node;
    }

    private void loadFolderEmails(TreeItem<String> selectedItem) {
        emailTableView.getItems().clear();
        Label loadingLabel = new Label("Loading preview messages...");
        loadingLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #4f46e5; -fx-font-style: italic;");
        emailTableView.setPlaceholder(loadingLabel);
        clearEmailPreview();

        Task<List<MailMessage>> loadTask = new Task<>() {
            @Override
            protected List<MailMessage> call() throws Exception {
                return getEmailsForNode(selectedItem, 1000);
            }
        };

        loadTask.setOnSucceeded(e -> {
            List<MailMessage> emails = loadTask.getValue();
            setFolderEmails(emails);
            emailTableView.setPlaceholder(new Label("Select a folder in the tree to view messages"));
            
            Integer totalCount = folderMessageCounts.get(selectedItem);
            if (totalCount != null && totalCount > 1000) {
                controller.showNotification("Preview limited to first 1,000 emails for performance.");
            }
        });

        loadTask.setOnFailed(e -> {
            Throwable ex = loadTask.getException();
            System.err.println("Failed to load folder emails: " + ex.getMessage());
            emailTableView.setPlaceholder(new Label("Failed to load messages: " + (ex != null ? ex.getMessage() : "Unknown error")));
        });

        Thread th = new Thread(loadTask);
        th.setDaemon(true);
        th.start();
    }

    public List<MailMessage> getEmailsForNode(TreeItem<String> selectedItem) {
        return getEmailsForNode(selectedItem, -1);
    }

    public List<MailMessage> getEmailsForNode(TreeItem<String> selectedItem, int limit) {
        List<MailMessage> emails = new ArrayList<>();
        if (selectedItem == null || (selectedItem.getValue() != null && selectedItem.getValue().startsWith("⚠️"))) {
            return emails;
        }

        File sourceFile = getSourceFileForNode(selectedItem);
        List<String> path = getFolderPathFromNode(selectedItem);

        if (sourceFile != null && sourceFile.exists() && sourceFile.length() > 0) {
            SourceAdapter parser = SourceAdapterFactory.getAdapterForFile(sourceFile);
            if (parser != null) {
                try {
                    parser.streamEmails(sourceFile, path, emails::add, limit);
                    return emails;
                } catch (Exception e) {
                    System.err.println("Warning: Error loading actual folder emails: " + e.getMessage());
                }
            }
        }

        // Fallback to mock emails
        String clean = selectedItem.getValue().trim().toLowerCase();

        if (clean.startsWith("inbox") || clean.equals("work projects") || clean.equals("milestones")
                || clean.equals("budgets") || clean.equals("team communications")) {
            int mockLimit = clean.startsWith("inbox") ? 105 : 12;
            for (int i = 1; i <= mockLimit; i++) {
                MailMessage m = new MailMessage(
                        i % 2 == 0 ? "support@microsoft.com" : "billing@stripe.com",
                        i % 2 == 0 ? "Welcome to Outlook Support Portal #" + i : "Invoice receipt for Subscription PRO #" + i,
                        "Today, " + (i % 12 + 1) + ":" + String.format("%02d", i % 60) + (i % 2 == 0 ? " AM" : " PM"),
                        "Hello User,\n\nWelcome to Outlook! This is a support notice to confirm your credentials and system sync setup.\n\nBest Regards,\nMicrosoft Support"
                );
                if (i % 2 != 0) {
                    m.addAttachment("stripe_invoice_" + i + ".pdf");
                    m.addAttachment("terms_and_conditions.pdf");
                }
                emails.add(m);
            }
        } else if (clean.equals("personal") || clean.equals("travel") || clean.equals("receipts")) {
            for (int i = 1; i <= 18; i++) {
                emails.add(new MailMessage(
                        "bookings@expedia.com",
                        "Flight confirmation: London to New York #" + i,
                        "May 28, 4:40 PM",
                        "Hi Traveler,\n\nYour flight is confirmed. Departs London Heathrow at 11:00 AM on July 10. Check-in online 24 hours prior.\n\nSafe travels!\nExpedia"
                ));
            }
        } else if (clean.equalsIgnoreCase("sent items")) {
            for (int i = 1; i <= 89; i++) {
                emails.add(new MailMessage(
                        "me@company.com",
                        "Re: Follow up on project milestone schedule #" + i,
                        "Today, 9:15 AM",
                        "Hi Team,\n\nI have reviewed the schedule and looks good. Let's merge the codebase tonight.\n\nThanks,\nAdmin"
                ));
            }
        } else if (clean.startsWith("archive") || clean.equals("archive 2024") || clean.equals("archive 2025")) {
            int mockLimit = clean.equals("archive 2024") ? 60 : (clean.equals("archive 2025") ? 50 : 110);
            for (int i = 1; i <= mockLimit; i++) {
                emails.add(new MailMessage(
                        "newsletter@stackexchange.com",
                        "Weekly stack overflow top answers #" + i,
                        "Dec 20, 2024, 8:00 AM",
                        "Hello developer,\n\nHere are the top questions and answers from Stack Overflow this week in Java, Spring, and SQL.\n\nStackExchange"
                ));
            }
        } else if (clean.equals("junk email")) {
            for (int i = 1; i <= 15; i++) {
                emails.add(new MailMessage(
                        "win@lottery-mega-jackpot.net",
                        "CONGRATULATIONS! You won $10,000,000 Cash Prize! #" + i,
                        "Today, 2:10 AM",
                        "Dear Winner,\n\nYou have been randomly selected as the grand prize winner of our promotional lottery draw. Send your details immediately!\n\nClaims Department"
                ));
            }
        } else if (clean.equals("contacts")) {
            MailMessage c1 = new MailMessage("Google LLC", "Alice Cooper", "N/A", "Alice is our lead architect partner.", "Contact");
            c1.addMetadata("Full Name", "Alice Cooper");
            c1.addMetadata("Company", "Google LLC");
            c1.addMetadata("Job Title", "Lead Cloud Architect");
            c1.addMetadata("Email Address", "alice.cooper@google.com");
            c1.addMetadata("Business Phone", "+1 (555) 019-2834");
            c1.addMetadata("Mobile Phone", "+1 (555) 019-9988");
            c1.addAttachment("alice_vcard.vcf");
            c1.addAttachment("avatar_photo.jpg");
            emails.add(c1);

            MailMessage c2 = new MailMessage("GitHub Inc.", "Bob Smith", "N/A", "Bob is our repository enterprise relations manager.", "Contact");
            c2.addMetadata("Full Name", "Bob Smith");
            c2.addMetadata("Company", "GitHub Inc.");
            c2.addMetadata("Job Title", "Enterprise Accounts Manager");
            c2.addMetadata("Email Address", "bob.smith@github.com");
            c2.addMetadata("Business Phone", "+1 (555) 998-1122");
            emails.add(c2);

            for (int i = 3; i <= 5; i++) {
                MailMessage contact = new MailMessage("Partner Company #" + i, "Partner Contact #" + i, "N/A", "Partner description for contact #" + i, "Contact");
                contact.addMetadata("Full Name", "Partner Contact #" + i);
                contact.addMetadata("Company", "Partner Company #" + i);
                contact.addMetadata("Email Address", "partner" + i + "@example.com");
                emails.add(contact);
            }
        } else if (clean.equals("calendar")) {
            MailMessage cal1 = new MailMessage("Alice Cooper", "Project Q3 Kickoff Meeting", "Today, 10:00 AM", "Agenda:\n1. Roadmap presentation\n2. Q&A\n3. Next actions", "Calendar");
            cal1.addMetadata("Start Time", "Mon Jun 08 10:00:00 IST 2026");
            cal1.addMetadata("End Time", "Mon Jun 08 11:30:00 IST 2026");
            cal1.addMetadata("Location", "Google Meet (Online)");
            cal1.addMetadata("Organizer", "Alice Cooper");
            cal1.addMetadata("Status", "Organizer");
            cal1.addAttachment("q3_roadmap.pptx");
            emails.add(cal1);

            MailMessage cal2 = new MailMessage("Main Office", "Team Weekly Sync", "Mon Jun 08 14:00:00 IST 2026", "Status report meeting for all engineering teams.", "Calendar");
            cal2.addMetadata("Start Time", "Mon Jun 08 14:00:00 IST 2026");
            cal2.addMetadata("End Time", "Mon Jun 08 14:45:00 IST 2026");
            cal2.addMetadata("Location", "Conference Room A");
            cal2.addMetadata("Organizer", "Engineering Director");
            cal2.addMetadata("Status", "Attendee");
            emails.add(cal2);

            for (int i = 3; i <= 4; i++) {
                MailMessage cal = new MailMessage("Organizer #" + i, "Sync Meeting #" + i, "Jun 09, 2026", "Weekly routine sync details.", "Calendar");
                cal.addMetadata("Start Time", "Tue Jun 09 11:00:00 IST 2026");
                cal.addMetadata("End Time", "Tue Jun 09 12:00:00 IST 2026");
                cal.addMetadata("Location", "Virtual");
                cal.addMetadata("Status", "Attendee");
                emails.add(cal);
            }
        } else if (clean.equals("tasks")) {
            MailMessage t1 = new MailMessage("Engineering Lead", "Refactor MainController Class", "Today, 9:00 AM", "Task Description:\nSplit the God class into modular view panels (Step1, Step2, Step3) to improve codebase maintenance and modularity.", "Task");
            t1.addMetadata("Start Date", "Sun Jun 07 09:00:00 IST 2026");
            t1.addMetadata("Due Date", "Wed Jun 10 18:00:00 IST 2026");
            t1.addMetadata("Status", "In Progress");
            t1.addMetadata("Priority", "High");
            t1.addAttachment("refactoring_spec.pdf");
            emails.add(t1);

            MailMessage t2 = new MailMessage("UI Designer", "Polish Theme Transitions", "Yesterday, 3:00 PM", "Ensure smooth neon glows and frosted glass transitions when changing between Light and Dark mode.", "Task");
            t2.addMetadata("Start Date", "Sat Jun 06 12:00:00 IST 2026");
            t2.addMetadata("Due Date", "Tue Jun 09 17:00:00 IST 2026");
            t2.addMetadata("Status", "Not Started");
            t2.addMetadata("Priority", "Normal");
            emails.add(t2);

            MailMessage t3 = new MailMessage("Self", "Write Walkthrough Documentation", "Jun 06, 2026", "Draft user instructions and update final walkthrough.md report.", "Task");
            t3.addMetadata("Start Date", "Sat Jun 06 10:00:00 IST 2026");
            t3.addMetadata("Due Date", "Wed Jun 10 12:00:00 IST 2026");
            t3.addMetadata("Status", "Complete");
            t3.addMetadata("Priority", "Low");
            emails.add(t3);
        } else if (clean.equals("notes")) {
            MailMessage n1 = new MailMessage("My Notes", "Ideas for Next Version", "Today, 10:30 AM", "💡 Ideas:\n- Add progress charts for Mailbox parsing\n- Support direct Cloud Source import\n- Implement batch export for attachments", "Note");
            emails.add(n1);

            MailMessage n2 = new MailMessage("My Notes", "Database SQLite Schema", "Yesterday, 4:00 PM", "Settings Table:\nCREATE TABLE settings (\n  key TEXT PRIMARY KEY,\n  value TEXT\n);", "Note");
            emails.add(n2);

            MailMessage n3 = new MailMessage("My Notes", "Server IP config details", "Jun 02, 2026", "Dev IP: 192.168.1.45\nStaging IP: 10.0.4.12\nPort: 8080", "Note");
            emails.add(n3);
        }
        return emails;
    }

    private void setFolderEmails(List<MailMessage> emails) {
        allEmailsForSelectedFolder.clear();
        allEmailsForSelectedFolder.addAll(emails);

        if (tfSearch != null) {
            if (tfSearch.getText() != null && !tfSearch.getText().isEmpty()) {
                tfSearch.setText("");
            } else {
                filteredEmailsList.clear();
                filteredEmailsList.addAll(allEmailsForSelectedFolder);
                currentPage = 1;
                updatePagination();
            }
        } else {
            filteredEmailsList.clear();
            filteredEmailsList.addAll(allEmailsForSelectedFolder);
            currentPage = 1;
            updatePagination();
        }
    }

    private void handleSearchChange(String query) {
        if (query == null || query.trim().isEmpty()) {
            filteredEmailsList.clear();
            filteredEmailsList.addAll(allEmailsForSelectedFolder);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            filteredEmailsList.clear();
            for (MailMessage email : allEmailsForSelectedFolder) {
                boolean match = (email.getFrom() != null && email.getFrom().toLowerCase().contains(lowerQuery))
                        || (email.getSubject() != null && email.getSubject().toLowerCase().contains(lowerQuery))
                        || (email.getDate() != null && email.getDate().toLowerCase().contains(lowerQuery))
                        || (email.getBody() != null && email.getBody().toLowerCase().contains(lowerQuery));
                if (match) {
                    filteredEmailsList.add(email);
                }
            }
        }
        currentPage = 1;
        updatePagination();
    }

    private void updatePagination() {
        int totalItems = filteredEmailsList.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        if (totalPages <= 0) {
            totalPages = 1;
        }
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }
        if (currentPage < 1) {
            currentPage = 1;
        }

        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(currentPage * pageSize, totalItems);

        ObservableList<MailMessage> pageItems = FXCollections.observableArrayList();
        if (fromIndex < toIndex) {
            pageItems.addAll(filteredEmailsList.subList(fromIndex, toIndex));
        }

        emailTableView.setItems(pageItems);

        if (!pageItems.isEmpty()) {
            emailTableView.getSelectionModel().select(0);
        } else {
            clearEmailPreview();
        }

        if (lblPageInfo != null) {
            lblPageInfo.setText("Page " + currentPage + " of " + totalPages);
        }

        int showStart = totalItems == 0 ? 0 : fromIndex + 1;
        int showEnd = toIndex;
        if (lblShowRange != null) {
            lblShowRange.setText("Showing " + showStart + "-" + showEnd + " of " + totalItems);
        }

        if (btnFirstPage != null) btnFirstPage.setDisable(currentPage == 1);
        if (btnPrevPage != null) btnPrevPage.setDisable(currentPage == 1);
        if (btnNextPage != null) btnNextPage.setDisable(currentPage >= totalPages);
        if (btnLastPage != null) btnLastPage.setDisable(currentPage >= totalPages);

        if (emailCountBadge != null) {
            if (allEmailsForSelectedFolder.size() == totalItems) {
                emailCountBadge.setText(totalItems + " items");
            } else {
                emailCountBadge.setText(totalItems + " of " + allEmailsForSelectedFolder.size() + " found");
            }
        }
    }

    private void clearEmailPreview() {
        // No-op
    }

    private void showEmailDetailPopup(MailMessage email) {
        Stage popup = new Stage();
        popup.initOwner(controller.getPrimaryStage());
        popup.initModality(Modality.WINDOW_MODAL);
        popup.setTitle(email.getItemType() + " Detail");
        popup.setMinWidth(700);
        popup.setMinHeight(560);

        // Root VBox
        VBox root = new VBox(0);
        root.getStyleClass().add("preview-root");

        // Header Box
        VBox headerBar = new VBox(8);
        headerBar.setPadding(new Insets(20, 24, 18, 24));
        headerBar.getStyleClass().add("preview-header");

        // Badge Pill Row
        HBox badgeRow = new HBox(8);
        badgeRow.setAlignment(Pos.CENTER_LEFT);

        Label typeBadge = new Label();
        typeBadge.getStyleClass().add("preview-badge");
        
        String type = email.getItemType();
        switch (type) {
            case "Contact":
                typeBadge.setText("👥 Contact");
                typeBadge.getStyleClass().add("preview-badge-contact");
                break;
            case "Calendar":
                typeBadge.setText("📅 Calendar");
                typeBadge.getStyleClass().add("preview-badge-calendar");
                break;
            case "Task":
                typeBadge.setText("✅ Task");
                typeBadge.getStyleClass().add("preview-badge-task");
                break;
            case "Note":
                typeBadge.setText("📓 Sticky Note");
                typeBadge.getStyleClass().add("preview-badge-note");
                break;
            default:
                typeBadge.setText("✉️ Email");
                typeBadge.getStyleClass().add("preview-badge-mail");
                break;
        }
        badgeRow.getChildren().add(typeBadge);

        Label subjectLbl = new Label(email.getSubject());
        subjectLbl.getStyleClass().add("preview-subject");
        subjectLbl.setWrapText(true);

        HBox metaRow = new HBox(24);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.setPadding(new Insets(4, 0, 0, 0));

        Label fromLbl = new Label("From: " + email.getFrom());
        fromLbl.getStyleClass().add("preview-from");

        Label dateLbl = new Label("Date: " + email.getDate());
        dateLbl.getStyleClass().add("preview-date");

        metaRow.getChildren().addAll(fromLbl, dateLbl);
        headerBar.getChildren().addAll(badgeRow, subjectLbl, metaRow);

        // Main content VBox: includes metadata cards (if any), body area, and attachment chips
        VBox mainContent = new VBox(14);
        mainContent.setPadding(new Insets(20, 24, 20, 24));
        VBox.setVgrow(mainContent, Priority.ALWAYS);
        mainContent.getStyleClass().add("preview-content-bg");

        // Metadata grid/cards
        if (email.getMetadata() != null && !email.getMetadata().isEmpty()) {
            FlowPane metadataPane = new FlowPane();
            metadataPane.setHgap(10);
            metadataPane.setVgap(10);
            metadataPane.setPadding(new Insets(0, 0, 6, 0));

            for (Map.Entry<String, String> entry : email.getMetadata().entrySet()) {
                VBox card = new VBox(3);
                card.setPadding(new Insets(8, 12, 8, 12));
                card.getStyleClass().add("preview-metadata-card");

                Label keyLbl = new Label(entry.getKey().toUpperCase());
                keyLbl.getStyleClass().add("preview-metadata-key");

                Label valLbl = new Label(entry.getValue());
                valLbl.getStyleClass().add("preview-metadata-value");
                valLbl.setWrapText(true);

                card.getChildren().addAll(keyLbl, valLbl);
                metadataPane.getChildren().add(card);
            }
            mainContent.getChildren().add(metadataPane);
        }

        // Body Content
        String rawBody = email.getBody();
        String displayBody = rawBody;
        if (rawBody != null && (rawBody.trim().toLowerCase().startsWith("<html") || rawBody.contains("<body") || rawBody.contains("<div") || rawBody.contains("<p>") || rawBody.contains("<br"))) {
            displayBody = rawBody.replaceAll("<[^>]*>", "")
                                 .replaceAll("&nbsp;", " ")
                                 .replaceAll("&amp;", "&")
                                 .replaceAll("&lt;", "<")
                                 .replaceAll("&gt;", ">")
                                 .trim();
        }
        if (displayBody == null || displayBody.isEmpty()) {
            displayBody = "(No content)";
        }
        
        TextArea bodyArea = new TextArea(displayBody);
        bodyArea.setEditable(false);
        bodyArea.setWrapText(true);
        bodyArea.getStyleClass().add("preview-body");
        if (type.equals("Note")) {
            bodyArea.getStyleClass().add("preview-body-note");
        }
        VBox.setVgrow(bodyArea, Priority.ALWAYS);
        mainContent.getChildren().add(bodyArea);

        // Attachments Row
        if (email.getAttachments() != null && !email.getAttachments().isEmpty()) {
            VBox attachmentsWrapper = new VBox(6);
            attachmentsWrapper.setPadding(new Insets(8, 0, 0, 0));

            Label attTitle = new Label("Attachments (" + email.getAttachments().size() + ")");
            attTitle.getStyleClass().add("preview-attachments-title");

            FlowPane chipsPane = new FlowPane();
            chipsPane.setHgap(8);
            chipsPane.setVgap(8);

            for (String att : email.getAttachments()) {
                HBox chip = new HBox(6);
                chip.setAlignment(Pos.CENTER_LEFT);
                chip.setPadding(new Insets(6, 12, 6, 12));
                chip.getStyleClass().add("preview-attachment-chip");

                Label clipIcon = new Label("📎");
                clipIcon.setStyle("-fx-font-size: 11px;");

                Label filenameLbl = new Label(att);
                filenameLbl.getStyleClass().add("preview-attachment-name");

                chip.getChildren().addAll(clipIcon, filenameLbl);

                // Add nice tooltips
                Tooltip.install(chip, new Tooltip("Attachment: " + att));

                chipsPane.getChildren().add(chip);
            }

            attachmentsWrapper.getChildren().addAll(attTitle, chipsPane);
            mainContent.getChildren().add(attachmentsWrapper);
        }

        // Footer Box
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(14, 24, 14, 24));
        footer.getStyleClass().add("preview-footer");

        Button openBrowserBtn = new Button("🌐 Open in Browser");
        openBrowserBtn.getStyleClass().addAll("action-btn", "btn-secondary");
        openBrowserBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8px 24px;");
        openBrowserBtn.setOnAction(e -> {
            try {
                String bodyText = email.getBody();
                if (bodyText == null) {
                    bodyText = "";
                }
                
                String htmlContent;
                if (bodyText.trim().toLowerCase().startsWith("<html") || bodyText.contains("<body") || bodyText.contains("<div") || bodyText.contains("<p>") || bodyText.contains("<br")) {
                    htmlContent = bodyText;
                } else {
                    String escaped = bodyText
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;");
                    htmlContent = "<html><body style='font-family: sans-serif; font-size: 14px; color: #0f172a; background-color: #ffffff; white-space: pre-wrap; word-wrap: break-word;'>" + escaped + "</body></html>";
                }
                
                File tempFile = File.createTempFile("mailbox_mail_preview_", ".html");
                tempFile.deleteOnExit();
                try (java.io.PrintWriter pw = new java.io.PrintWriter(tempFile, "UTF-8")) {
                    pw.write(htmlContent);
                }
                
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", tempFile.getAbsolutePath()});
                } else {
                    if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                        java.awt.Desktop.getDesktop().browse(tempFile.toURI());
                    } else {
                        System.err.println("Unsupported desktop action, cannot open browser.");
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                controller.showAlert(Alert.AlertType.ERROR, "Error", "Could not open browser: " + ex.getMessage());
            }
        });

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().addAll("action-btn", "btn-primary");
        closeBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8px 24px;");
        closeBtn.setOnAction(e -> popup.close());
        footer.getChildren().addAll(openBrowserBtn, closeBtn);

        root.getChildren().addAll(headerBar, mainContent, footer);

        Scene scene = new Scene(root, 760, 600);
        try {
            String css = controller.getActiveThemeStylesheet();
            scene.getStylesheets().add(css);
        } catch (Exception ignored) {}

        popup.setScene(scene);
        popup.show();
    }

    public boolean hasSelectedFolders() {
        List<String> checkedPaths = new ArrayList<>();
        if (folderTreeView != null && folderTreeView.getRoot() != null) {
            collectCheckedPaths(folderTreeView.getRoot(), new ArrayList<>(), checkedPaths);
        }
        return !checkedPaths.isEmpty();
    }

    public void autoSaveSelectedTreeToSession() {
        if (controller.getSessionDir() == null) return;
        File migrationDataDir = new File(controller.getSessionDir(), "migration_data");
        if (!migrationDataDir.exists()) {
            migrationDataDir.mkdirs();
        }
        File targetFile = new File(migrationDataDir, "selected_folders.txt");
        exportTreeToFile(targetFile);
    }

    private void exportTreeToFile(File targetFile) {
        if (targetFile == null) return;
        try (java.io.PrintWriter pw = new java.io.PrintWriter(targetFile)) {
            pw.print(generateTreeReport());
            System.out.println("Exported selected tree to: " + targetFile.getAbsolutePath());
        } catch (Exception ex) {
            System.err.println("Failed to export selected tree: " + ex.getMessage());
            controller.showAlert(Alert.AlertType.ERROR, "Export Failed", "Could not export selected tree structure:\n" + ex.getMessage());
        }
    }

    private String generateTreeReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=========================================\n");
        sb.append(com.pstconverter.config.BrandConfig.TOOL_NAME.toUpperCase()).append(" - FOLDER SELECTION REPORT\n");
        sb.append("Generated: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())).append("\n");
        sb.append("=========================================\n\n");

        sb.append("SUMMARY OF SELECTED FOLDERS:\n");
        List<String> checkedPaths = new ArrayList<>();
        if (folderTreeView != null && folderTreeView.getRoot() != null) {
            collectCheckedPaths(folderTreeView.getRoot(), new ArrayList<>(), checkedPaths);
        }
        if (checkedPaths.isEmpty()) {
            sb.append("(No folders selected)\n");
        } else {
            for (String p : checkedPaths) {
                sb.append("- ").append(p).append("\n");
            }
        }
        sb.append("\n-----------------------------------------\n");
        sb.append("FULL HIERARCHY TREE SELECTION:\n");
        sb.append("-----------------------------------------\n");
        if (folderTreeView != null && folderTreeView.getRoot() != null) {
            buildTextTreeRepresentation(folderTreeView.getRoot(), "", true, sb);
        } else {
            sb.append("(Tree not loaded)\n");
        }
        return sb.toString();
    }

    private void collectCheckedPaths(TreeItem<String> item, List<String> currentPath, List<String> checkedPaths) {
        if (item == null) return;
        List<String> nextPath = new ArrayList<>(currentPath);
        nextPath.add(item.getValue());
        if (item instanceof CheckBoxTreeItem) {
            CheckBoxTreeItem<String> cbItem = (CheckBoxTreeItem<String>) item;
            if (cbItem.isSelected() && isSourceNativeFolder(cbItem)) {
                checkedPaths.add(String.join(" > ", nextPath));
            }
        }
        for (TreeItem<String> child : item.getChildren()) {
            collectCheckedPaths(child, nextPath, checkedPaths);
        }
    }

    private void buildTextTreeRepresentation(TreeItem<String> item, String indent, boolean isLast, StringBuilder sb) {
        if (item == null) return;

        sb.append(indent);
        if (item.getParent() == null) {
            sb.append("");
        } else {
            sb.append(isLast ? "└── " : "├── ");
        }

        String checkMark = "[ ]";
        if (item instanceof CheckBoxTreeItem) {
            CheckBoxTreeItem<String> cbItem = (CheckBoxTreeItem<String>) item;
            if (cbItem.isSelected()) {
                checkMark = "[X]";
            } else if (cbItem.isIndeterminate()) {
                checkMark = "[-]";
            }
        }

        Integer count = folderMessageCounts.get(item);
        String countStr = (count != null) ? " (" + count + ")" : "";
        sb.append(checkMark).append(" ").append(item.getValue()).append(countStr).append("\n");

        List<TreeItem<String>> children = item.getChildren();
        for (int i = 0; i < children.size(); i++) {
            String nextIndent = indent + (item.getParent() == null ? "" : (isLast ? "    " : "│   "));
            buildTextTreeRepresentation(children.get(i), nextIndent, i == children.size() - 1, sb);
        }
    }

    private void handleExportTree() {
        if (folderTreeView.getRoot() == null) {
            controller.showAlert(Alert.AlertType.WARNING, "No Data", "No tree structure is loaded yet.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Selected Tree Structure");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt"));
        fileChooser.setInitialFileName("Mailbox_Tree_Report.txt");

        String lastDir = SettingsManager.getSetting("last_file_directory", null);
        if (lastDir != null) {
            File initDir = new File(lastDir);
            if (initDir.exists() && initDir.isDirectory()) {
                fileChooser.setInitialDirectory(initDir);
            }
        }

        File targetFile = fileChooser.showSaveDialog(controller.getPrimaryStage());
        if (targetFile != null) {
            exportTreeToFile(targetFile);
            autoSaveSelectedTreeToSession();
            controller.showNotification("Successfully exported tree report.");
        }
    }

    private void addMockInternalFolders(CheckBoxTreeItem<String> parent) {
        java.util.Set<String> supportedTypes = new java.util.HashSet<>();
        boolean hasFiles = false;
        for (SourceFileModel model : controller.getFileList()) {
            if (model.isValid()) {
                hasFiles = true;
                File f = new File(model.getFilePath());
                com.pstconverter.core.adapter.SourceAdapter adapter = com.pstconverter.core.adapter.SourceAdapterFactory.getAdapterForFile(f);
                if (adapter != null) {
                    supportedTypes.addAll(adapter.getSupportedItemTypes());
                }
            }
        }
        if (!hasFiles) {
            supportedTypes.addAll(java.util.Arrays.asList("Emails", "Calendar Items", "Contacts", "Tasks", "Notes", "Journal Entries"));
        }

        CheckBoxTreeItem<String> inbox = new CheckBoxTreeItem<>("Inbox");
        folderMessageCounts.put(inbox, 105);

        CheckBoxTreeItem<String> milestones = new CheckBoxTreeItem<>("Milestones");
        folderMessageCounts.put(milestones, 12);
        CheckBoxTreeItem<String> budgets = new CheckBoxTreeItem<>("Budgets");
        folderMessageCounts.put(budgets, 3);
        CheckBoxTreeItem<String> teamComms = new CheckBoxTreeItem<>("Team Communications");
        folderMessageCounts.put(teamComms, 7);

        CheckBoxTreeItem<String> work = new CheckBoxTreeItem<>("Work Projects");
        folderMessageCounts.put(work, 22);
        work.getChildren().addAll(milestones, budgets, teamComms);

        CheckBoxTreeItem<String> travel = new CheckBoxTreeItem<>("Travel");
        folderMessageCounts.put(travel, 10);
        CheckBoxTreeItem<String> receipts = new CheckBoxTreeItem<>("Receipts");
        folderMessageCounts.put(receipts, 8);

        CheckBoxTreeItem<String> personal = new CheckBoxTreeItem<>("Personal");
        folderMessageCounts.put(personal, 18);
        personal.getChildren().addAll(travel, receipts);

        inbox.getChildren().addAll(work, personal);

        CheckBoxTreeItem<String> sent = new CheckBoxTreeItem<>("Sent Items");
        folderMessageCounts.put(sent, 89);
        CheckBoxTreeItem<String> drafts = new CheckBoxTreeItem<>("Drafts");
        folderMessageCounts.put(drafts, 4);

        CheckBoxTreeItem<String> archive24 = new CheckBoxTreeItem<>("Archive 2024");
        folderMessageCounts.put(archive24, 60);
        CheckBoxTreeItem<String> archive25 = new CheckBoxTreeItem<>("Archive 2025");
        folderMessageCounts.put(archive25, 50);

        CheckBoxTreeItem<String> archive = new CheckBoxTreeItem<>("Archive");
        folderMessageCounts.put(archive, 110);
        archive.getChildren().addAll(archive24, archive25);

        CheckBoxTreeItem<String> junk = new CheckBoxTreeItem<>("Junk Email");
        folderMessageCounts.put(junk, 15);

        CheckBoxTreeItem<String> deleted = new CheckBoxTreeItem<>("Deleted Items");
        folderMessageCounts.put(deleted, 0);

        parent.getChildren().addAll(inbox, sent, drafts, archive, junk, deleted);

        if (supportedTypes.contains("Contacts")) {
            CheckBoxTreeItem<String> contacts = new CheckBoxTreeItem<>("Contacts");
            folderMessageCounts.put(contacts, 5);
            parent.getChildren().add(contacts);
        }

        if (supportedTypes.contains("Calendar Items")) {
            CheckBoxTreeItem<String> calendar = new CheckBoxTreeItem<>("Calendar");
            folderMessageCounts.put(calendar, 4);
            parent.getChildren().add(calendar);
        }

        if (supportedTypes.contains("Tasks")) {
            CheckBoxTreeItem<String> tasks = new CheckBoxTreeItem<>("Tasks");
            folderMessageCounts.put(tasks, 3);
            parent.getChildren().add(tasks);
        }

        if (supportedTypes.contains("Notes")) {
            CheckBoxTreeItem<String> notes = new CheckBoxTreeItem<>("Notes");
            folderMessageCounts.put(notes, 3);
            parent.getChildren().add(notes);
        }
    }

    private void setAllNodesExpanded(TreeItem<String> item, boolean expanded) {
        if (item != null) {
            item.setExpanded(expanded);
            for (TreeItem<String> child : item.getChildren()) {
                setAllNodesExpanded(child, expanded);
            }
        }
    }

    private boolean pruneEmptyNodes(TreeItem<String> item) {
        if (item == null) return false;

        List<TreeItem<String>> children = new ArrayList<>(item.getChildren());
        for (TreeItem<String> child : children) {
            boolean keep = pruneEmptyNodes(child);
            if (!keep) {
                item.getChildren().remove(child);
            }
        }

        if (item.getChildren().isEmpty()) {
            if (isUiGroupOrFileNode(item)) {
                return false;
            }
            Integer count = folderMessageCounts.get(item);
            return count != null && count > 0;
        }

        return true;
    }

    private boolean isUiGroupOrFileNode(TreeItem<String> item) {
        if (item == null) return true;
        String val = item.getValue();
        if (val == null) return true;
        if (val.equals("All Mailboxes") || val.equals("Mailbox File (Single)") || val.equals("Mailbox File (Folder)")) {
            return true;
        }
        if (val.contains(".") && SourceAdapterFactory.isSupported(new File(val))) {
            return true;
        }
        for (SourceFileModel f : controller.getFileList()) {
            if (val.equalsIgnoreCase(f.getFileName())) {
                return true;
            }
        }
        return !isSourceNativeFolder(item);
    }

    public boolean isSourceNativeFolder(TreeItem<String> treeItem) {
        if (treeItem == null) return false;
        TreeItem<String> parent = treeItem.getParent();
        while (parent != null) {
            String val = parent.getValue();
            if (val != null) {
                if (val.contains(".") && SourceAdapterFactory.isSupported(new File(val))) {
                    return true;
                }
                for (SourceFileModel f : controller.getFileList()) {
                    if (val.equalsIgnoreCase(f.getFileName())) {
                        return true;
                    }
                }
            }
            parent = parent.getParent();
        }
        return false;
    }

    private boolean isSourceFileNode(TreeItem<String> treeItem) {
        if (treeItem == null) return false;
        String val = treeItem.getValue();
        if (val == null) return false;
        if (val.contains(".") && SourceAdapterFactory.isSupported(new File(val))) return true;
        for (SourceFileModel f : controller.getFileList()) {
            if (val.equalsIgnoreCase(f.getFileName())) {
                return true;
            }
        }
        return false;
    }

    private void saveTreeStates(TreeItem<String> item, List<String> path, Set<String> expandedPaths, Set<String> checkedPaths) {
        if (item == null) return;
        List<String> newPath = new ArrayList<>(path);
        newPath.add(item.getValue());
        String pathStr = String.join(" > ", newPath);
        if (item.isExpanded()) {
            expandedPaths.add(pathStr);
        }
        if (item instanceof CheckBoxTreeItem && ((CheckBoxTreeItem<String>) item).isSelected()) {
            checkedPaths.add(pathStr);
        }
        for (TreeItem<String> child : item.getChildren()) {
            saveTreeStates(child, newPath, expandedPaths, checkedPaths);
        }
    }

    private void restoreTreeStates(TreeItem<String> item, List<String> path, Set<String> expandedPaths, Set<String> checkedPaths) {
        if (item == null) return;
        List<String> newPath = new ArrayList<>(path);
        newPath.add(item.getValue());
        String pathStr = String.join(" > ", newPath);

        if (expandedPaths.contains(pathStr)) {
            item.setExpanded(true);
        }

        if (item instanceof CheckBoxTreeItem) {
            CheckBoxTreeItem<String> cbItem = (CheckBoxTreeItem<String>) item;
            if (checkedPaths.contains(pathStr)) {
                cbItem.setSelected(true);
            } else {
                cbItem.setSelected(false);
            }
        }

        for (TreeItem<String> child : item.getChildren()) {
            restoreTreeStates(child, newPath, expandedPaths, checkedPaths);
        }
    }

    public File getSourceFileForNode(TreeItem<String> selectedItem) {
        TreeItem<String> current = selectedItem;
        while (current != null) {
            String val = current.getValue();
            if (val != null) {
                for (SourceFileModel item : controller.getFileList()) {
                    if (item.getFileName().equals(val)) {
                        if (item.getImportSourceType().equalsIgnoreCase("Folder")) {
                            TreeItem<String> parent = current.getParent();
                            if (parent != null && parent.getValue().equals(item.getSourceFolderName())) {
                                return new File(item.getFilePath());
                            }
                        } else {
                            return new File(item.getFilePath());
                        }
                    }
                }
            }
            current = current.getParent();
        }
        return null;
    }

    private TreeItem<String> getSourceFileNodeForItem(TreeItem<String> selectedItem) {
        TreeItem<String> current = selectedItem;
        while (current != null) {
            String val = current.getValue();
            if (val != null) {
                for (SourceFileModel item : controller.getFileList()) {
                    if (item.getFileName().equals(val)) {
                        if (item.getImportSourceType().equalsIgnoreCase("Folder")) {
                            TreeItem<String> parent = current.getParent();
                            if (parent != null && parent.getValue().equals(item.getSourceFolderName())) {
                                return current;
                            }
                        } else {
                            return current;
                        }
                    }
                }
            }
            current = current.getParent();
        }
        return null;
    }

    public List<String> getFolderPathFromNode(TreeItem<String> selectedItem) {
        List<String> path = new ArrayList<>();
        TreeItem<String> sourceFileNode = getSourceFileNodeForItem(selectedItem);
        if (sourceFileNode == null) {
            return path;
        }

        TreeItem<String> current = selectedItem;
        while (current != null && !current.equals(sourceFileNode)) {
            path.add(0, current.getValue());
            current = current.getParent();
        }
        return path;
    }

    public List<String> getSelectedSenders() {
        List<String> senders = new ArrayList<>();
        if (folderTreeView == null || folderTreeView.getRoot() == null) {
            return senders;
        }
        
        List<TreeItem<String>> checkedItems = new ArrayList<>();
        collectCheckedTreeItems(folderTreeView.getRoot(), checkedItems);
        
        Set<String> uniqueSenders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        
        for (TreeItem<String> item : checkedItems) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            if (isSourceNativeFolder(item)) {
                File sourceFile = getSourceFileForNode(item);
                List<String> path = getFolderPathFromNode(item);
                if (sourceFile != null && sourceFile.exists()) {
                    SourceAdapter parser = SourceAdapterFactory.getAdapterForFile(sourceFile);
                    if (parser != null) {
                        try {
                            parser.streamEmailsMetadata(sourceFile, path, msg -> {
                                if (Thread.currentThread().isInterrupted()) {
                                    return;
                                }
                                String sender = msg.getFrom();
                                if (sender != null && !sender.trim().isEmpty() && !sender.equalsIgnoreCase("Unknown Sender")) {
                                    uniqueSenders.add(sender.trim());
                                }
                            });
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        senders.addAll(uniqueSenders);
        return senders;
    }

    public List<String> getSelectedRecipients() {
        List<String> recipients = new ArrayList<>();
        if (folderTreeView == null || folderTreeView.getRoot() == null) {
            return recipients;
        }
        
        List<TreeItem<String>> checkedItems = new ArrayList<>();
        collectCheckedTreeItems(folderTreeView.getRoot(), checkedItems);
        
        Set<String> uniqueRecipients = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        
        for (TreeItem<String> item : checkedItems) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            if (isSourceNativeFolder(item)) {
                File sourceFile = getSourceFileForNode(item);
                List<String> path = getFolderPathFromNode(item);
                if (sourceFile != null && sourceFile.exists()) {
                    SourceAdapter parser = SourceAdapterFactory.getAdapterForFile(sourceFile);
                    if (parser != null) {
                        try {
                            parser.streamEmailsMetadata(sourceFile, path, msg -> {
                                if (Thread.currentThread().isInterrupted()) {
                                    return;
                                }
                                String toStr = msg.getTo();
                                if (toStr == null || toStr.trim().isEmpty()) {
                                    toStr = msg.getMetadata().get("To");
                                }
                                if (toStr != null && !toStr.trim().isEmpty()) {
                                    String[] parts = toStr.split("[;,]");
                                    for (String p : parts) {
                                        String clean = p.trim();
                                        if (!clean.isEmpty() && !clean.equalsIgnoreCase("Unknown Recipient")) {
                                            uniqueRecipients.add(clean);
                                        }
                                    }
                                }
                            });
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        recipients.addAll(uniqueRecipients);
        return recipients;
    }

    private void collectCheckedTreeItems(TreeItem<String> item, List<TreeItem<String>> checkedItems) {
        if (item == null) return;
        if (item instanceof CheckBoxTreeItem) {
            CheckBoxTreeItem<String> cbItem = (CheckBoxTreeItem<String>) item;
            if (cbItem.isSelected()) {
                checkedItems.add(cbItem);
            }
        }
        for (TreeItem<String> child : item.getChildren()) {
            collectCheckedTreeItems(child, checkedItems);
        }
    }

    /**
     * Returns the root TreeItem of the folder tree as it is currently configured
     * (with checked/unchecked state). Used by Step5 to mirror the same hierarchy
     * on the conversion progress panel.
     */
    public TreeItem<String> getSelectedFolderTreeRoot() {
        if (folderTreeView == null) return null;
        return folderTreeView.getRoot();
    }

    public void resetView() {
        if (folderTreeView != null) {
            if (folderTreeView.getRoot() instanceof CheckBoxTreeItem && currentIndeterminateListener != null) {
                ((CheckBoxTreeItem<String>) folderTreeView.getRoot()).indeterminateProperty().removeListener(currentIndeterminateListener);
            }
            folderTreeView.setRoot(null);
        }
        if (emailTableView != null) {
            emailTableView.getItems().clear();
        }
        folderMessageCounts.clear();
        systemFolders.clear();
        allEmailsForSelectedFolder.clear();
        filteredEmailsList.clear();
        currentPage = 1;
        if (lblPageInfo != null) lblPageInfo.setText("Page 1 of 1");
        if (lblShowRange != null) lblShowRange.setText("Showing 0-0 of 0");
        if (emailCountBadge != null) emailCountBadge.setText("0 items");
        if (cbSelectAll != null) {
            cbSelectAll.selectedProperty().unbind();
            cbSelectAll.setSelected(false);
        }
    }

    public List<TreeItem<String>> getCheckedTreeItems() {
        List<TreeItem<String>> checked = new ArrayList<>();
        if (folderTreeView != null && folderTreeView.getRoot() != null) {
            collectCheckedTreeItems(folderTreeView.getRoot(), checked);
        }
        return checked;
    }

    /**
     * Returns the total number of mail folder nodes currently loaded in the tree
     * (regardless of check state). Used by DiagnosticLogger to record how many
     * folders were analysed before the user made a selection.
     */
    public int getTotalFolderCount() {
        if (folderTreeView == null || folderTreeView.getRoot() == null) {
            return 0;
        }
        return countFolderNodes(folderTreeView.getRoot());
    }

    /** Recursively counts all source-native folder nodes in the tree. */
    private int countFolderNodes(TreeItem<String> item) {
        if (item == null) return 0;
        int count = isSourceNativeFolder(item) ? 1 : 0;
        for (TreeItem<String> child : item.getChildren()) {
            count += countFolderNodes(child);
        }
        return count;
    }

    /**
     * Resets the entire tree selection and checks only the folders specified in the folderKeys.
     * This is used during the Resume flow to restore the user's previously selected folders.
     */
    public void selectFoldersByKeys(List<String> folderKeys) {
        if (folderTreeView == null || folderTreeView.getRoot() == null || folderKeys == null || folderKeys.isEmpty()) {
            return;
        }
        // First deselect all to ensure clean slate
        if (folderTreeView.getRoot() instanceof CheckBoxTreeItem) {
            ((CheckBoxTreeItem<String>) folderTreeView.getRoot()).setSelected(false);
        }
        for (TreeItem<String> child : folderTreeView.getRoot().getChildren()) {
            selectFoldersByKeysRecursive(child, "", folderKeys);
        }
    }

    /**
     * Helper recursive method to matches tree nodes against folderKeys by rebuilding their unique hierarchical paths.
     * Parents of matched nodes are expanded automatically so the restored folders are visible.
     */
    private void selectFoldersByKeysRecursive(TreeItem<String> item, String pathPrefix, List<String> folderKeys) {
        if (item == null) return;
        String label = item.getValue();
        String uniqueKey = pathPrefix + "/" + label;
        if (folderKeys.contains(uniqueKey)) {
            if (item instanceof CheckBoxTreeItem) {
                CheckBoxTreeItem<String> cbItem = (CheckBoxTreeItem<String>) item;
                cbItem.setSelected(true);
                // Expand all parents up to the root
                TreeItem<String> parent = item.getParent();
                while (parent != null) {
                    parent.setExpanded(true);
                    parent = parent.getParent();
                }
            }
        }
        for (TreeItem<String> child : item.getChildren()) {
            selectFoldersByKeysRecursive(child, uniqueKey, folderKeys);
        }
    }
}
