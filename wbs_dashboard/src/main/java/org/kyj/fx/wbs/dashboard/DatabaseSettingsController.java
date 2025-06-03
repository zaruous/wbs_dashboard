package org.kyj.fx.wbs.dashboard; // Replace with your actual package name

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class DatabaseSettingsController {

    @FXML private ComboBox<String> dbTypeComboBox;
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField dbNameField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;

    @FXML private Button clearButton;
    @FXML private Button addButton; // Will handle both Add and Update
    @FXML private Button deleteButton;
    @FXML private Button saveAllButton;

    @FXML private TableView<DatabaseConfig> configsTableView;
    @FXML private TableColumn<DatabaseConfig, String> idColumn;
    @FXML private TableColumn<DatabaseConfig, String> dbTypeColumn;
    @FXML private TableColumn<DatabaseConfig, String> hostColumn;
    @FXML private TableColumn<DatabaseConfig, String> dbNameColumn;
    @FXML private TableColumn<DatabaseConfig, String> usernameColumn;

    private ObservableList<DatabaseConfig> dbConfigs;
    private static final String CONFIG_FILE_NAME = "connections_config.csv";
    private DatabaseConfig selectedConfig = null;

    @FXML
    public void initialize() {
        dbConfigs = FXCollections.observableArrayList();
        dbTypeComboBox.setItems(FXCollections.observableArrayList("MariaDB", "MSSQL"));

        // Setup TableView columns
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        dbTypeColumn.setCellValueFactory(new PropertyValueFactory<>("dbType"));
        hostColumn.setCellValueFactory(new PropertyValueFactory<>("host"));
        dbNameColumn.setCellValueFactory(new PropertyValueFactory<>("dbName"));
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));

        configsTableView.setItems(dbConfigs);

        // Listener for TableView selection
        configsTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            selectedConfig = newSelection;
            if (newSelection != null) {
                populateFields(newSelection);
                addButton.setText("수정"); // Change button text to "Update"
                deleteButton.setDisable(false);
            } else {
                clearFormFields();
                addButton.setText("추가"); // Change button text back to "Add"
                deleteButton.setDisable(true);
            }
        });

        loadConfigsFromFile();
        deleteButton.setDisable(true); // Initially no item is selected
    }

    @FXML
    private void handleClearForm() {
        configsTableView.getSelectionModel().clearSelection();
        clearFormFields();
        selectedConfig = null;
        addButton.setText("추가");
        hostField.requestFocus();
    }

    @FXML
    private void handleAddOrUpdateConfig() {
        String dbType = dbTypeComboBox.getValue();
        String host = hostField.getText().trim();
        String port = portField.getText().trim();
        String dbName = dbNameField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText(); // Password can be empty

        if (dbType == null || host.isEmpty() || port.isEmpty() || dbName.isEmpty() || username.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "입력 오류", "모든 필수 필드를 입력해주세요 (비밀번호 제외).");
            return;
        }

        if (selectedConfig != null) { // Update existing
            selectedConfig.setDbType(dbType);
            selectedConfig.setHost(host);
            selectedConfig.setPort(port);
            selectedConfig.setDbName(dbName);
            selectedConfig.setUsername(username);
            selectedConfig.setPassword(password);
            configsTableView.refresh(); // Refresh table to show changes
            showAlert(Alert.AlertType.INFORMATION, "성공", "설정이 업데이트되었습니다.");
        } else { // Add new
            String id = UUID.randomUUID().toString().substring(0, 8); // Simple unique ID
            DatabaseConfig newConfig = new DatabaseConfig(id, dbType, host, port, dbName, username, password);
            dbConfigs.add(newConfig);
            showAlert(Alert.AlertType.INFORMATION, "성공", "새 설정이 추가되었습니다.");
        }
        // Optionally clear form after add/update, or keep data for further edits
        // handleClearForm(); // Uncomment if you want to clear after add/update
    }

    @FXML
    private void handleDeleteConfig() {
        if (selectedConfig == null) {
            showAlert(Alert.AlertType.WARNING, "선택 오류", "삭제할 설정을 선택해주세요.");
            return;
        }

        Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION,
                "정말로 '" + selectedConfig.getHost() + " (" + selectedConfig.getDbName() + ")' 설정을 삭제하시겠습니까?",
                ButtonType.YES, ButtonType.NO);
        confirmationDialog.setTitle("삭제 확인");
        confirmationDialog.setHeaderText(null);

        Optional<ButtonType> result = confirmationDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            dbConfigs.remove(selectedConfig);
            showAlert(Alert.AlertType.INFORMATION, "삭제 완료", "선택한 설정이 삭제되었습니다.");
            handleClearForm(); // Clear fields and selection
        }
    }

    @FXML
    private void handleSaveAllConfigs() {
        File configFile = new File(CONFIG_FILE_NAME);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            // Write header (optional, but good for CSV)
            // writer.write("ID,DBType,Host,Port,DBName,Username,Password\n");
            for (DatabaseConfig config : dbConfigs) {
                writer.write(config.toString());
                writer.newLine();
            }
            showAlert(Alert.AlertType.INFORMATION, "저장 완료", "모든 설정이 '" + CONFIG_FILE_NAME + "' 파일에 저장되었습니다.");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "저장 실패", "설정을 파일에 저장하는 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void populateFields(DatabaseConfig config) {
        dbTypeComboBox.setValue(config.getDbType());
        hostField.setText(config.getHost());
        portField.setText(config.getPort());
        dbNameField.setText(config.getDbName());
        usernameField.setText(config.getUsername());
        passwordField.setText(config.getPassword());
    }

    private void clearFormFields() {
        dbTypeComboBox.getSelectionModel().clearSelection();
        dbTypeComboBox.setPromptText("선택");
        hostField.clear();
        portField.clear();
        dbNameField.clear();
        usernameField.clear();
        passwordField.clear();
    }

    private void loadConfigsFromFile() {
        File configFile = new File(CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            return; // No config file yet, do nothing
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            // Skip header if you wrote one
            // reader.readLine(); 
            while ((line = reader.readLine()) != null) {
                DatabaseConfig config = DatabaseConfig.fromString(line);
                if (config != null) {
                    dbConfigs.add(config);
                }
            }
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "로드 실패", "설정 파일 로드 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}