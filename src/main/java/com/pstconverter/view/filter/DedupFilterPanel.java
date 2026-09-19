package com.pstconverter.view.filter;

import com.pstconverter.util.MaterialIcons;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DedupFilterPanel extends VBox {

    private final Runnable validationCallback;

    private CheckBox cbRemoveDuplicates;
    private CheckBox cbDedupSubject;
    private CheckBox cbDedupSender;
    private CheckBox cbDedupRecipients;
    private CheckBox cbDedupDate;
    private CheckBox cbDedupBody;
    private CheckBox cbDedupMessageId;
    private CheckBox cbDedupAttachmentNames;
    private Label lblDedupCriteriaPreview;
    private FlowPane fieldsPane;
    private HBox presetsBox;
    private Label lblDedupFields;

    public DedupFilterPanel(Runnable validationCallback) {
        this.validationCallback = validationCallback;
        buildUI();
    }

    private CheckBox createFilterCheckBox(String text, boolean selected) {
        CheckBox cb = new CheckBox(text);
        cb.getStyleClass().add("filter-checkbox");
        cb.setStyle("-fx-font-weight: 600; -fx-cursor: hand;");
        cb.setSelected(selected);
        cb.selectedProperty().addListener((o, ov, nv) -> {
            updateDedupPreview();
            validationCallback.run();
        });
        return cb;
    }

    private void buildUI() {
        this.setSpacing(12);
        this.setPadding(new Insets(8));

        // ── Section Header ──
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label iconHeader = MaterialIcons.icon(MaterialIcons.REFRESH, "-fx-text-fill: #22d3ee; -fx-font-size: 18px;");
        Label titleHeader = new Label("Email Deduplication");
        titleHeader.getStyleClass().add("filter-section-header");
        header.getChildren().addAll(iconHeader, titleHeader);

        VBox content = new VBox(14);

        // ── Main Deduplication Card ──
        VBox dedupCard = new VBox(12);
        dedupCard.getStyleClass().add("filter-sub-card");

        // Card Sub-header
        HBox cardTitleBox = new HBox(8);
        cardTitleBox.setAlignment(Pos.CENTER_LEFT);
        Label cardIcon = MaterialIcons.icon(MaterialIcons.CLEANING, "-fx-text-fill: #818cf8; -fx-font-size: 15px;");
        Label lblDedupHeader = new Label("Deduplication Options");
        lblDedupHeader.getStyleClass().add("filter-group-label");
        cardTitleBox.getChildren().addAll(cardIcon, lblDedupHeader);

        // Main Checkbox
        cbRemoveDuplicates = new CheckBox("Remove Duplicate Emails");
        cbRemoveDuplicates.getStyleClass().add("filter-main-checkbox");
        cbRemoveDuplicates.setStyle("-fx-font-size: 13.5px; -fx-font-weight: bold; -fx-cursor: hand;");
        cbRemoveDuplicates.setSelected(false);
        cbRemoveDuplicates.setTooltip(new Tooltip("Enable duplicate email detection and removal based on selected fields."));

        // Field Label
        lblDedupFields = new Label("Select Fields to Compare:");
        lblDedupFields.getStyleClass().add("filter-field-label");

        // Comparison Checkboxes
        cbDedupSubject = createFilterCheckBox("Subject", true);
        cbDedupSender = createFilterCheckBox("From / Sender", false);
        cbDedupRecipients = createFilterCheckBox("To / Recipients", false);
        cbDedupDate = createFilterCheckBox("Sent Date & Time", true);
        cbDedupBody = createFilterCheckBox("Message Body / Text", false);
        cbDedupMessageId = createFilterCheckBox("Message-ID Header", false);
        cbDedupAttachmentNames = createFilterCheckBox("Attachment Names", false);

        CheckBox[] fields = {cbDedupSubject, cbDedupSender, cbDedupRecipients, cbDedupDate, cbDedupBody, cbDedupMessageId, cbDedupAttachmentNames};

        fieldsPane = new FlowPane(16, 10);
        fieldsPane.setPadding(new Insets(6, 0, 6, 12));
        fieldsPane.getChildren().addAll(fields);

        // Presets
        presetsBox = new HBox(8);
        presetsBox.setAlignment(Pos.CENTER_LEFT);
        presetsBox.setPadding(new Insets(2, 0, 2, 12));

        Label lblPresetsLabel = new Label("Presets:");
        lblPresetsLabel.getStyleClass().add("filter-hint-label");
        lblPresetsLabel.setStyle("-fx-font-weight: bold;");
        presetsBox.getChildren().add(lblPresetsLabel);

        MFXButton btnStrict = new MFXButton("Strict (All)");
        btnStrict.getStyleClass().add("filter-chip");
        btnStrict.setOnAction(e -> {
            for (CheckBox cb : fields) cb.setSelected(true);
        });

        MFXButton btnMetadata = new MFXButton("Metadata");
        btnMetadata.getStyleClass().add("filter-chip");
        btnMetadata.setOnAction(e -> {
            for (CheckBox cb : fields) cb.setSelected(false);
            cbDedupSubject.setSelected(true);
            cbDedupSender.setSelected(true);
            cbDedupDate.setSelected(true);
        });

        MFXButton btnContent = new MFXButton("Content Only");
        btnContent.getStyleClass().add("filter-chip");
        btnContent.setOnAction(e -> {
            for (CheckBox cb : fields) cb.setSelected(false);
            cbDedupSubject.setSelected(true);
            cbDedupBody.setSelected(true);
            cbDedupAttachmentNames.setSelected(true);
        });

        presetsBox.getChildren().addAll(btnStrict, btnMetadata, btnContent);

        // Preview status label
        lblDedupCriteriaPreview = new Label("");
        lblDedupCriteriaPreview.setWrapText(true);
        lblDedupCriteriaPreview.setPadding(new Insets(4, 0, 0, 12));

        // Master checkbox toggles state
        cbRemoveDuplicates.selectedProperty().addListener((o, ov, nv) -> {
            fieldsPane.setDisable(!nv);
            presetsBox.setDisable(!nv);
            lblDedupFields.setDisable(!nv);
            updateDedupPreview();
            validationCallback.run();
        });

        // Initialize disabled state if unchecked
        fieldsPane.setDisable(true);
        presetsBox.setDisable(true);
        lblDedupFields.setDisable(true);

        VBox dedupContent = new VBox(10);
        dedupContent.getChildren().addAll(
            cbRemoveDuplicates,
            lblDedupFields,
            fieldsPane,
            presetsBox,
            lblDedupCriteriaPreview
        );

        dedupCard.getChildren().addAll(cardTitleBox, new Separator(), dedupContent);

        // Explanatory Note Card
        HBox hintBox = new HBox(8);
        hintBox.setAlignment(Pos.CENTER_LEFT);
        hintBox.getStyleClass().add("filter-hint-card");

        Label hintIcon = MaterialIcons.icon(MaterialIcons.INFO, "-fx-text-fill: #818cf8; -fx-font-size: 14px;");
        Label hintText = new Label("Deduplication prevents importing identical emails. Custom criteria lets you define what makes an email 'duplicate'.");
        hintText.getStyleClass().add("filter-hint-label");
        hintText.setWrapText(true);
        hintBox.getChildren().addAll(hintIcon, hintText);

        content.getChildren().addAll(dedupCard, hintBox);
        this.getChildren().addAll(header, content);

        updateDedupPreview();
    }

    private void updateDedupPreview() {
        if (lblDedupCriteriaPreview == null) return;
        List<String> activeFields = new ArrayList<>();
        if (cbDedupSubject.isSelected()) activeFields.add("Subject");
        if (cbDedupSender.isSelected()) activeFields.add("From");
        if (cbDedupRecipients.isSelected()) activeFields.add("To");
        if (cbDedupDate.isSelected()) activeFields.add("Date");
        if (cbDedupBody.isSelected()) activeFields.add("Body");
        if (cbDedupMessageId.isSelected()) activeFields.add("Message-ID");
        if (cbDedupAttachmentNames.isSelected()) activeFields.add("Attachments");

        if (!cbRemoveDuplicates.isSelected()) {
            lblDedupCriteriaPreview.setText("Deduplication is disabled.");
            lblDedupCriteriaPreview.getStyleClass().clear();
            lblDedupCriteriaPreview.getStyleClass().add("filter-hint-label");
            lblDedupCriteriaPreview.setStyle("-fx-font-size: 11.5px; -fx-font-style: italic;");
        } else if (activeFields.isEmpty()) {
            lblDedupCriteriaPreview.setText("⚠️ Please select at least one field to compare.");
            lblDedupCriteriaPreview.setStyle("-fx-text-fill: #fb7185; -fx-font-size: 11.5px; -fx-font-weight: bold;");
        } else {
            lblDedupCriteriaPreview.setText("Active Match Criteria: " + String.join(" + ", activeFields));
            lblDedupCriteriaPreview.setStyle("-fx-text-fill: #818cf8; -fx-font-size: 11.5px; -fx-font-weight: bold;");
        }
    }

    public void saveProperties(Properties props) {
        props.setProperty("hygiene.removeDuplicates", String.valueOf(cbRemoveDuplicates.isSelected()));
        props.setProperty("hygiene.dedupSubject", String.valueOf(cbDedupSubject.isSelected()));
        props.setProperty("hygiene.dedupSender", String.valueOf(cbDedupSender.isSelected()));
        props.setProperty("hygiene.dedupRecipients", String.valueOf(cbDedupRecipients.isSelected()));
        props.setProperty("hygiene.dedupDate", String.valueOf(cbDedupDate.isSelected()));
        props.setProperty("hygiene.dedupBody", String.valueOf(cbDedupBody.isSelected()));
        props.setProperty("hygiene.dedupMessageId", String.valueOf(cbDedupMessageId.isSelected()));
        props.setProperty("hygiene.dedupAttachmentNames", String.valueOf(cbDedupAttachmentNames.isSelected()));
    }

    public void loadProperties(Properties props) {
        cbRemoveDuplicates.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.removeDuplicates", "false")));
        cbDedupSubject.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.dedupSubject", "false")));
        cbDedupSender.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.dedupSender", "false")));
        cbDedupRecipients.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.dedupRecipients", "false")));
        cbDedupDate.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.dedupDate", "false")));
        cbDedupBody.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.dedupBody", "false")));
        cbDedupMessageId.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.dedupMessageId", "false")));
        cbDedupAttachmentNames.setSelected(Boolean.parseBoolean(props.getProperty("hygiene.dedupAttachmentNames", "false")));
        updateDedupPreview();
    }
}
