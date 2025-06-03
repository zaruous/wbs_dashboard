package org.kyj.fx.wbs.dashboard;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.ProgressBarTreeTableCell;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;

public class WbsProjectManager extends Application {

	private TreeTableView<Task> treeTableView;
	private final ObservableList<Task> rootTasks = FXCollections.observableArrayList();
	private final ObservableList<Task> allTasksFlat = FXCollections.observableArrayList();

	private Label overallProgressLabel;
	private ProgressBar overallProgressBar;
	private PieChart tasksPieChart;
	private TextArea assigneeLoadSummaryArea;

	// Input fields for tasks
	private TextField nameField;
	private TextField assigneeField;
	private DatePicker startDatePicker;
	private DatePicker endDatePicker;
	private Spinner<Integer> progressSpinner;
	private CheckBox categoryCheckBox;
	private TextField predecessorIdsField;
	private Button updateTaskButton;

	// Input fields for project metadata
	private TextField projectNameField;
	private DatePicker projectStartDatePicker;
	private DatePicker projectEndDatePicker;
	private TextField authorField;
	private Label lastModifiedLabel;

	private ProjectMetadata projectMetadata = new ProjectMetadata();

	// Gantt Chart components
	private Canvas ganttCanvas;
	private DatePicker ganttDisplayStartDatePicker;
	private DatePicker ganttDisplayEndDatePicker;
	private ScrollPane ganttScrollPane;
	private Pane ganttChartPane; // Pane to hold task names and bars for scrolling

	private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private final DateTimeFormatter monthDayFormatter = DateTimeFormatter.ofPattern("MM/dd");
	private Stage primaryStage;

	private static final double GANTT_ROW_HEIGHT = 30;
	private static final double GANTT_TASK_NAME_WIDTH = 200;
	private static final double GANTT_DAY_WIDTH = 25; // Width of one day column
	private static final double GANTT_HEADER_HEIGHT = 40;

	private TaskDbManager taskDbManager = new TaskDbManager();

	@Override
	public void start(Stage primaryStage) {
		this.primaryStage = primaryStage;
		primaryStage.setTitle("WBS 프로젝트 관리 도구");

		BorderPane mainLayout = new BorderPane();
		MenuBar menuBar = createMenuBar();
		mainLayout.setTop(menuBar);

		TabPane tabPane = new TabPane();
		Tab wbsTab = new Tab("WBS 관리");
		wbsTab.setClosable(false);
		wbsTab.setContent(createWbsManagementPane());

		Tab dashboardTab = new Tab("대시보드");
		dashboardTab.setClosable(false);
		dashboardTab.setContent(createDashboardPane());

		Tab ganttTab = new Tab("간트 차트 뷰");
		ganttTab.setClosable(false);
		ganttTab.setContent(createGanttChartPane());
		tabPane.getTabs().addAll(wbsTab, dashboardTab, ganttTab);

		mainLayout.setCenter(tabPane);

		allTasksFlat.addListener((ListChangeListener<Task>) c -> {
			while (c.next()) {
				if (c.wasAdded()) {
					for (Task addedTask : c.getAddedSubList()) {
						addListenersToTask(addedTask);
					}
				}
				if (c.wasRemoved()) {
					updateRowNumbers();
				}
			}
			updateDashboard();
			updateRowNumbers();
			drawGanttChart(); // Redraw Gantt chart on task changes
		});

		rootTasks.addListener((ListChangeListener<Task>) c -> {
			while (c.next()) {
				if (c.wasAdded() || c.wasRemoved()) {
					rebuildFlatTaskList();
				}
			}
			updateRowNumbers();
			drawGanttChart(); // Redraw Gantt chart on root task changes
		});

		Scene scene = new Scene(mainLayout, 1600, 950); // Increased width for Gantt
		try {
			String cssPath = Objects.requireNonNull(getClass().getResource("styles.css")).toExternalForm();
			scene.getStylesheets().add(cssPath);
		} catch (NullPointerException e) {
			System.err.println("CSS file not found. Ensure styles.css is in the same directory or classpath.");
			scene.getRoot().setStyle("-fx-base: #3c3f41; -fx-background: #2b2b2b;");
		}

		primaryStage.setScene(scene);
		primaryStage.show();

		updateMetadataInputFields();
		boolean tablesIfNotExist = taskDbManager.createTablesIfNotExist();
		if(tablesIfNotExist)
			loadInitialDataFromDb();
		else
			loadSampleData();
		updateDashboard();
		drawGanttChart(); // Initial draw
		
	}

	private void addListenersToTask(Task task) {
		task.nameProperty().addListener((obs, ov, nv) -> {
			updateDashboard();
			drawGanttChart();
			updateTime();
		});
		task.assigneeProperty().addListener((obs, ov, nv) -> {
			updateDashboard();
			drawGanttChart();
			updateTime();
		});
		task.progressProperty().addListener((obs, oldVal, newVal) -> {
			updateDashboard();
			drawGanttChart();
			updateTime();
		});
		task.startDateProperty().addListener((obs, oldVal, newVal) -> {
			updateDashboard();
			drawGanttChart();
			updateTime();
		});
		task.endDateProperty().addListener((obs, oldVal, newVal) -> {
			updateDashboard();
			drawGanttChart();
			updateTime();
		});
		task.categoryProperty().addListener((obs, ov, nv) -> {
			updateDashboard();
			drawGanttChart();
			updateTime();
		});
		task.lockedProperty().addListener((obs, ov, nv) -> {
			TreeItem<Task> selectedTreeItem = treeTableView.getSelectionModel().getSelectedItem();
			if (selectedTreeItem != null && selectedTreeItem.getValue() == task) {
				handleTaskSelection(task);
			}
			treeTableView.refresh();
			updateDashboard();
			drawGanttChart();
			updateTime();
		});
		task.predecessorIdsProperty().addListener((obs, ov, nv) -> {
			treeTableView.refresh();
			drawGanttChart();
			updateTime();
		});
	}

	private void updateTime() {
		TreeItem<Task> selectedTreeItem = treeTableView.getSelectionModel().getSelectedItem();
		if(selectedTreeItem ==null) return;
		selectedTreeItem.getValue().updateUpdateDate();
	}

	private MenuBar createMenuBar() {
		MenuBar menuBar = new MenuBar();
		Menu fileMenu = new Menu("파일");

		MenuItem dbSaveItem = new MenuItem("DB에 프로젝트 저장");
        dbSaveItem.setOnAction(e -> saveAllProjectDataToDb());
        dbSaveItem.setAccelerator(KeyCombination.keyCombination("Ctrl+S")); // Ctrl+S for saving to DB);
        
        // MenuItem loadItem = new MenuItem("프로젝트 불러오기 (.wbs)..."); // Old file load
        // loadItem.setOnAction(e -> loadProjectDataFromFile()); // Old file load
        MenuItem dbLoadItem = new MenuItem("DB에서 프로젝트 불러오기");
        dbLoadItem.setOnAction(e -> loadInitialDataFromDb()); // Re-load from DB
        
		MenuItem saveItem = new MenuItem("프로젝트 저장 (.wbs)...");
		saveItem.setOnAction(e -> saveProjectData());
		MenuItem loadItem = new MenuItem("프로젝트 불러오기 (.wbs)...");
		loadItem.setOnAction(e -> loadProjectData());

		Menu exportMenu = new Menu("내보내기");
		MenuItem exportCsvItem = new MenuItem("CSV로 내보내기...");
		exportCsvItem.setOnAction(e -> exportToCsv());
		MenuItem exportJsonItem = new MenuItem("JSON으로 내보내기...");
		exportJsonItem.setOnAction(e -> exportToJson());
		exportMenu.getItems().addAll(exportCsvItem, exportJsonItem);

		Menu importMenu = new Menu("가져오기");
		MenuItem importCsvItem = new MenuItem("CSV에서 가져오기...");
		importCsvItem.setOnAction(e -> importFromCsv());
		MenuItem importJsonItem = new MenuItem("JSON에서 가져오기...");
		importJsonItem.setOnAction(e -> importFromJson());
		importMenu.getItems().addAll(importCsvItem, importJsonItem);

		
		MenuItem exportDashboard = new MenuItem("Dashboard HTML로 내보내기...");
		exportDashboard.setOnAction(e -> exportDashboardToHtmlReport());
		
		MenuItem exitItem = new MenuItem("종료");
		exitItem.setOnAction(e -> primaryStage.close());

		fileMenu.getItems().addAll(dbSaveItem, dbLoadItem, 
				new SeparatorMenuItem(),
				saveItem, loadItem ,
				new SeparatorMenuItem(),
				exportMenu, importMenu, 
				new SeparatorMenuItem(), exportDashboard, 
				new SeparatorMenuItem(), exitItem);

	
		Menu configMenu = new Menu("설정");
		MenuItem dbConfigMenuItem = new MenuItem("DB 설정");
		dbConfigMenuItem.setOnAction(e -> {
			try {
				FXMLLoader loader = new FXMLLoader();
				loader.setLocation(WbsProjectManager.class.getResource("DatabaseSettingsView.fxml"));
				Parent root = loader.load();
				Stage stage = new Stage();
				stage.setTitle("DatabaseSettings");
				Scene value = new Scene(root, 800, 600);
				stage.setScene(value);
				stage.initOwner(primaryStage);
				stage.show();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		});
		configMenu.getItems().add(dbConfigMenuItem);
		
		menuBar.getMenus().addAll(fileMenu, configMenu);
		return menuBar;
	}

    // New method to save all current data to DB
    private void saveAllProjectDataToDb() {
        updateMetadataFromInputFields();
        projectMetadata.setLastModifiedDate(LocalDate.now());
        taskDbManager.saveProjectMetadata(projectMetadata);
        updateMetadataInputFields(); // Refresh last modified display

        // Clear existing tasks in DB first to avoid duplicates or orphaned data if structure changes significantly
        // Or implement more sophisticated update logic in TaskDbManager.saveTask
        taskDbManager.deleteAllTasks(); // Simpler for now: delete all then re-insert

        for (Task task : rootTasks) {
        	//if(task.isUpdate())
        		taskDbManager.saveTask(task, null); // saveTask is recursive
        }
        showAlert("DB 저장 완료", "프로젝트 데이터가 데이터베이스에 성공적으로 저장되었습니다.");
    }
    

    private void loadInitialDataFromDb() {
        this.projectMetadata = taskDbManager.loadProjectMetadata();
        updateMetadataInputFields();

        List<Task> loadedTasks = taskDbManager.loadAllTasks();
        if (loadedTasks.isEmpty()) {
            // If DB is empty, load sample data and save it to DB for the first time
            //loadSampleData(); // This populates rootTasks
            //saveAllProjectDataToDb(); // Save the sample data to DB
        } else {
            rootTasks.setAll(loadedTasks);
        }
        populateTreeTableViewFromRootTasks(); // This calls updateRowNumbers
        rebuildFlatTaskList(); // This calls updateDashboard and gantt refresh via listeners
    }

	private void updateMetadataFromInputFields() {
		projectMetadata.setProjectName(projectNameField.getText());
		projectMetadata.setProjectStartDate(projectStartDatePicker.getValue());
		projectMetadata.setProjectEndDate(projectEndDatePicker.getValue());
		projectMetadata.setAuthor(authorField.getText());
	}

	private void updateMetadataInputFields() {
		projectNameField.setText(projectMetadata.getProjectName());
		projectStartDatePicker.setValue(projectMetadata.getProjectStartDate());
		projectEndDatePicker.setValue(projectMetadata.getProjectEndDate());
		authorField.setText(projectMetadata.getAuthor());
		lastModifiedLabel.setText("마지막 작성일: " + (projectMetadata.getLastModifiedDate() != null
				? projectMetadata.getLastModifiedDate().format(dateFormatter)
				: "N/A"));
	}

	private void saveProjectData() {
		updateMetadataFromInputFields();
		projectMetadata.setLastModifiedDate(LocalDate.now());
		updateMetadataInputFields();

		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("프로젝트 저장");
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WBS Project Files (*.wbs)", "*.wbs"));
		File file = fileChooser.showSaveDialog(primaryStage);

		if (file != null) {
			ProjectData projectDataToSave = new ProjectData(projectMetadata, new ArrayList<>(rootTasks));
			try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
				oos.writeObject(projectDataToSave);
				showAlert("저장 완료", "프로젝트가 성공적으로 저장되었습니다:\n" + file.getAbsolutePath());
			} catch (IOException e) {
				e.printStackTrace();
				showAlert("저장 오류", "프로젝트 저장 중 오류가 발생했습니다:\n" + e.getMessage());
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void loadProjectData() {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("프로젝트 불러오기");
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WBS Project Files (*.wbs)", "*.wbs"));
		File file = fileChooser.showOpenDialog(primaryStage);

		if (file != null) {
			try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
				Object readObject = ois.readObject();
				if (readObject instanceof ProjectData) {
					ProjectData loadedProjectData = (ProjectData) readObject;
					projectMetadata = loadedProjectData.getMetadata() != null ? loadedProjectData.getMetadata()
							: new ProjectMetadata();
					rootTasks.setAll(
							loadedProjectData.getTasks() != null ? loadedProjectData.getTasks() : new ArrayList<>());
				} else if (readObject instanceof List) {
					projectMetadata = new ProjectMetadata();
					rootTasks.setAll((List<Task>) readObject);
					showAlert("정보", "이전 버전의 파일 형식입니다. 프로젝트 메타데이터가 초기화됩니다.");
				} else {
					throw new IOException("알 수 없는 파일 형식입니다.");
				}

				updateMetadataInputFields();
				allTasksFlat.clear();
				if (treeTableView != null && treeTableView.getRoot() != null) {
					treeTableView.getRoot().getChildren().clear();
				}
				populateTreeTableViewFromRootTasks();
				rebuildFlatTaskList();
				updateDashboard();
				showAlert("불러오기 완료", "프로젝트를 성공적으로 불러왔습니다:\n" + file.getAbsolutePath());
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
				showAlert("불러오기 오류", "프로젝트 불러오기 중 오류가 발생했습니다:\n" + e.getMessage());
			}
		}
	}

	private void populateTreeTableViewFromRootTasks() {
		if (treeTableView == null || treeTableView.getRoot() == null)
			return;
		treeTableView.getRoot().getChildren().clear();
		for (Task task : rootTasks) {
			treeTableView.getRoot().getChildren().add(createTreeItemRecursive(task));
		}
		updateRowNumbers();
	}

	private TreeItem<Task> createTreeItemRecursive(Task task) {
		TreeItem<Task> item = new TreeItem<>(task);
		if (task.getChildren() != null) {
			for (Task childTask : task.getChildren()) {
				item.getChildren().add(createTreeItemRecursive(childTask));
			}
		}
		item.setExpanded(true);
		return item;
	}

	private void updateRowNumbers() {
		if (treeTableView != null) {
			treeTableView.refresh();
		}
	}

	private BorderPane createWbsManagementPane() {
		BorderPane borderPane = new BorderPane();
		borderPane.setPadding(new Insets(10));

		treeTableView = new TreeTableView<>();
		TreeItem<Task> rootTreeItem = new TreeItem<>(new Task("프로젝트 루트", true));
		treeTableView.setRoot(rootTreeItem);
		treeTableView.setShowRoot(false);
		treeTableView.setEditable(true);

		TreeTableColumn<Task, Void> rowNumCol = new TreeTableColumn<>("#");
		rowNumCol.setPrefWidth(40);
		rowNumCol.setSortable(false);
		rowNumCol.setCellFactory(col -> new TreeTableCell<Task, Void>() {
			@Override
			protected void updateItem(Void item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || getTreeTableRow() == null || getTreeTableRow().getIndex() < 0) {
					setText(null);
				} else {
					setText(String.valueOf(getTreeTableRow().getIndex() + 1));
				}
			}
		});

		TreeTableColumn<Task, String> taskIdCol = new TreeTableColumn<>("작업 ID");
		taskIdCol.setPrefWidth(50);
		taskIdCol.setCellValueFactory(param -> {
			if (param.getValue() != null && param.getValue().getValue() != null) {
				return new ReadOnlyStringWrapper(param.getValue().getValue().getId());
			}
			return new ReadOnlyStringWrapper("");
		});

		TreeTableColumn<Task, String> nameCol = new TreeTableColumn<>("업무 항목");
		nameCol.setPrefWidth(250);
		nameCol.setCellValueFactory(param -> param.getValue().getValue().nameProperty());
		nameCol.setCellFactory(TextFieldTreeTableCell.forTreeTableColumn());
		nameCol.setOnEditStart(event -> {
			Task task = event.getRowValue().getValue();
			if (task.isLocked()) {
				event.consume();
				showAlert("수정 불가", "잠긴 작업은 수정할 수 없습니다.");
			}
		});
		nameCol.setOnEditCommit(event -> event.getRowValue().getValue().setName(event.getNewValue()));

		TreeTableColumn<Task, String> assigneeCol = new TreeTableColumn<>("파트/담당자");
		assigneeCol.setPrefWidth(120);
		assigneeCol.setCellValueFactory(param -> param.getValue().getValue().assigneeProperty());
		assigneeCol.setCellFactory(TextFieldTreeTableCell.forTreeTableColumn());
		assigneeCol.setOnEditStart(event -> {
			Task task = event.getRowValue().getValue();
			if (task.isLocked() || task.isCategory()) {
				event.consume();
				showAlert("수정 불가", "잠긴 작업 또는 카테고리의 담당자는 수정할 수 없습니다.");
			}
		});
		assigneeCol.setOnEditCommit(event -> event.getRowValue().getValue().setAssignee(event.getNewValue()));

		TreeTableColumn<Task, LocalDate> startDateCol = new TreeTableColumn<>("시작일");
		startDateCol.setPrefWidth(100);
		startDateCol.setCellValueFactory(param -> param.getValue().getValue().startDateProperty());
		startDateCol.setCellFactory(createDateCellFactory(true));
		startDateCol.setOnEditCommit(event -> event.getRowValue().getValue().setStartDate(event.getNewValue()));

		TreeTableColumn<Task, LocalDate> endDateCol = new TreeTableColumn<>("종료일");
		endDateCol.setPrefWidth(100);
		endDateCol.setCellValueFactory(param -> param.getValue().getValue().endDateProperty());
		endDateCol.setCellFactory(createDateCellFactory(false));
		endDateCol.setOnEditCommit(event -> event.getRowValue().getValue().setEndDate(event.getNewValue()));

		TreeTableColumn<Task, Double> progressCol = new TreeTableColumn<>("진척률 (%)");
		progressCol.setPrefWidth(120);
		progressCol.setCellValueFactory(param -> {
			if (param == null || param.getValue() == null || param.getValue().getValue() == null) {
				return new SimpleDoubleProperty(0.0).asObject();
			}
			Task task = param.getValue().getValue();
			if (task.isCategory()) {
				return null;
			}
			return task.progressProperty().divide(100.0).asObject();
		});
		progressCol.setCellFactory(col -> new ProgressBarTreeTableCell<Task>() {
			@Override
			public void updateItem(Double item, boolean empty) {
				super.updateItem(item, empty);
				TreeTableRow<Task> currentRow = getTreeTableRow();
				if (empty || item == null || currentRow == null || currentRow.getItem() == null
						|| currentRow.getItem().isCategory() || currentRow.getItem().isLocked()) {
					setText(null);
					setGraphic(null);
				}
			}
		});

		TreeTableColumn<Task, String> predecessorCol = new TreeTableColumn<>("선행 작업 ID");
		predecessorCol.setPrefWidth(150);
		predecessorCol.setCellValueFactory(param -> {
			if (param.getValue() != null && param.getValue().getValue() != null) {
				return new ReadOnlyStringWrapper(String.join(", ", param.getValue().getValue().getPredecessorIds()));
			}
			return new ReadOnlyStringWrapper("");
		});

		treeTableView.getColumns().addAll(rowNumCol, taskIdCol, nameCol, assigneeCol, startDateCol, endDateCol,
				progressCol, predecessorCol);

		treeTableView.setRowFactory(tv -> {
			TreeTableRow<Task> row = new TreeTableRow<>();
			ContextMenu contextMenu = new ContextMenu();
			MenuItem lockMenuItem = new MenuItem();

			lockMenuItem.setOnAction(event -> {
				Task task = row.getItem();
				if (task != null) {
					task.setLocked(!task.isLocked());
				}
			});
			contextMenu.getItems().add(lockMenuItem);

			row.itemProperty().addListener((obs, oldItem, newItem) -> {
				row.getStyleClass().remove("locked-row");
				if (newItem != null) {
					lockMenuItem.textProperty()
							.bind(Bindings.when(newItem.lockedProperty()).then("해제").otherwise("잠금"));
					if (newItem.isLocked()) {
						row.getStyleClass().add("locked-row");
					}
				} else {
					lockMenuItem.textProperty().unbind();
					lockMenuItem.setText("");
				}
			});

			row.setOnMouseClicked(event -> {
				if (event.getButton() == MouseButton.SECONDARY && row.getItem() != null) {
					contextMenu.show(row, event.getScreenX(), event.getScreenY());
				} else {
					if (contextMenu.isShowing()) {
						contextMenu.hide();
					}
				}
			});
			return row;
		});

		borderPane.setCenter(treeTableView);

		GridPane metadataGrid = new GridPane();
		metadataGrid.setHgap(10);
		metadataGrid.setVgap(10);
		metadataGrid.setPadding(new Insets(10, 0, 10, 0));
		projectNameField = new TextField();
		projectStartDatePicker = new DatePicker();
		projectEndDatePicker = new DatePicker();
		authorField = new TextField();
		lastModifiedLabel = new Label();

		metadataGrid.add(new Label("프로젝트명:"), 0, 0);
		metadataGrid.add(projectNameField, 1, 0, 3, 1);
		metadataGrid.add(new Label("프로젝트 시작일:"), 0, 1);
		metadataGrid.add(projectStartDatePicker, 1, 1);
		metadataGrid.add(new Label("프로젝트 종료일:"), 2, 1);
		metadataGrid.add(projectEndDatePicker, 3, 1);
		metadataGrid.add(new Label("작성자:"), 0, 2);
		metadataGrid.add(authorField, 1, 2);
		metadataGrid.add(lastModifiedLabel, 2, 2, 2, 1);

		GridPane inputGrid = new GridPane();
		inputGrid.setHgap(10);
		inputGrid.setVgap(10);
		inputGrid.setPadding(new Insets(0, 0, 10, 0));

		nameField = new TextField();
		nameField.setPromptText("업무명");
		assigneeField = new TextField();
		assigneeField.setPromptText("담당자");
		startDatePicker = new DatePicker(LocalDate.now());
		endDatePicker = new DatePicker(LocalDate.now().plusDays(7));
		progressSpinner = new Spinner<>(0, 100, 0);
		progressSpinner.setEditable(true);
		categoryCheckBox = new CheckBox("카테고리 항목");
		predecessorIdsField = new TextField();
		predecessorIdsField.setPromptText("선행 작업 ID (쉼표로 구분)");

		categoryCheckBox.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
			boolean currentlyLocked = false;
			TreeItem<Task> selectedTreeItem = treeTableView.getSelectionModel().getSelectedItem();
			if (selectedTreeItem != null && selectedTreeItem.getValue() != null) {
				currentlyLocked = selectedTreeItem.getValue().isLocked();
			}
			boolean disableBasedOnCategory = isSelected;
			assigneeField.setDisable(disableBasedOnCategory || currentlyLocked);
			startDatePicker.setDisable(disableBasedOnCategory || currentlyLocked);
			endDatePicker.setDisable(disableBasedOnCategory || currentlyLocked);
			progressSpinner.setDisable(disableBasedOnCategory || currentlyLocked);
			predecessorIdsField.setDisable(currentlyLocked);

			if (isSelected) {
				assigneeField.clear();
				startDatePicker.setValue(null);
				endDatePicker.setValue(null);
				progressSpinner.getValueFactory().setValue(0);
			}
		});

		Button addTaskButton = new Button("최상위 작업 추가");
		addTaskButton.setOnAction(e -> {
			Task newTask = createTaskFromInputFields();
			rootTasks.add(newTask);
			treeTableView.getRoot().getChildren().add(new TreeItem<>(newTask));
			rebuildFlatTaskList();
			clearInputFields();
			treeTableView.getSelectionModel().clearSelection();
		});

		Button addSubTaskButton = new Button("하위 작업 추가");
		addSubTaskButton.setOnAction(e -> {
			TreeItem<Task> selectedItem = treeTableView.getSelectionModel().getSelectedItem();
			if (selectedItem != null && selectedItem.getValue() != null) {
				if (!selectedItem.getValue().isCategory() && categoryCheckBox.isSelected()) {
					showAlert("오류", "일반 작업 항목에는 카테고리 하위 작업을 추가할 수 없습니다.");
					return;
				}
				Task newTask = createTaskFromInputFields();
				selectedItem.getValue().addChild(newTask);
				selectedItem.getChildren().add(new TreeItem<>(newTask));
				selectedItem.setExpanded(true);
				rebuildFlatTaskList();
				clearInputFields();
				treeTableView.getSelectionModel().clearSelection();
			} else {
				showAlert("선택 오류", "하위 작업을 추가할 상위 작업을 선택해주세요.");
			}
		});

		updateTaskButton = new Button("선택 작업 수정");
		updateTaskButton.setDisable(true);
		updateTaskButton.setOnAction(e -> {
			TreeItem<Task> selectedItem = treeTableView.getSelectionModel().getSelectedItem();
			if (selectedItem != null && selectedItem.getValue() != null) {
				Task taskToUpdate = selectedItem.getValue();
				if (taskToUpdate.isLocked()) {
					showAlert("수정 불가", "잠긴 작업은 수정할 수 없습니다. 먼저 잠금을 해제해주세요.");
					return;
				}
				taskToUpdate.setName(nameField.getText());
				taskToUpdate.setCategory(categoryCheckBox.isSelected());

				List<String> predIds = Arrays.stream(predecessorIdsField.getText().split(",")).map(String::trim)
						.filter(id -> !id.isEmpty()).collect(Collectors.toList());
				taskToUpdate.setPredecessorIds(predIds);

				if (!taskToUpdate.isCategory()) {
					taskToUpdate.setAssignee(assigneeField.getText());
					taskToUpdate.setStartDate(startDatePicker.getValue());
					taskToUpdate.setEndDate(endDatePicker.getValue());
					taskToUpdate.setProgress(progressSpinner.getValue());
				} else {
					taskToUpdate.setAssignee("");
					taskToUpdate.setStartDate(null);
					taskToUpdate.setEndDate(null);
					taskToUpdate.setProgress(0);
				}
				treeTableView.refresh();
				rebuildFlatTaskList();
			}
		});

		Button removeTaskButton = new Button("선택 작업 삭제");
		removeTaskButton.setOnAction(e -> {
			TreeItem<Task> selectedItem = treeTableView.getSelectionModel().getSelectedItem();
			if (selectedItem != null && selectedItem.getValue() != null) {
				Task taskToDelete = selectedItem.getValue();
				if (taskToDelete.isLocked()) {
					showAlert("삭제 불가", "잠긴 작업은 삭제할 수 없습니다. 먼저 잠금을 해제해주세요.");
					return;
				}
				Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
				confirmAlert.setTitle("작업 삭제 확인");
				confirmAlert.setHeaderText("'" + taskToDelete.getName() + "' 작업을 삭제하시겠습니까?");
				confirmAlert.setContentText("이 작업과 모든 하위 작업들이 삭제됩니다.");
				Optional<ButtonType> result = confirmAlert.showAndWait();

				if (result.isPresent() && result.get() == ButtonType.OK) {
					TreeItem<Task> parentItem = selectedItem.getParent();
					if (parentItem != null && parentItem.getValue() != null) {
						if (parentItem == treeTableView.getRoot()) {
							rootTasks.remove(taskToDelete);
						} else {
							parentItem.getValue().removeChild(taskToDelete);
						}
						parentItem.getChildren().remove(selectedItem);
						rebuildFlatTaskList();
						clearInputFields();
						treeTableView.getSelectionModel().clearSelection();
					}
				}
			} else {
				showAlert("선택 오류", "삭제할 작업을 선택해주세요.");
			}
		});

		inputGrid.add(new Label("업무명:"), 0, 0);
		inputGrid.add(nameField, 1, 0);
		inputGrid.add(new Label("담당자:"), 0, 1);
		inputGrid.add(assigneeField, 1, 1);
		inputGrid.add(new Label("선행 작업 ID:"), 0, 2);
		inputGrid.add(predecessorIdsField, 1, 2);

		inputGrid.add(new Label("시작일:"), 2, 0);
		inputGrid.add(startDatePicker, 3, 0);
		inputGrid.add(new Label("종료일:"), 2, 1);
		inputGrid.add(endDatePicker, 3, 1);
		inputGrid.add(new Label("진척률(%):"), 2, 2);
		inputGrid.add(progressSpinner, 3, 2);

		inputGrid.add(categoryCheckBox, 0, 3);

		HBox buttonBox = new HBox(10, addTaskButton, addSubTaskButton, updateTaskButton, removeTaskButton);
		buttonBox.setAlignment(Pos.CENTER_LEFT);
		inputGrid.add(buttonBox, 0, 4, 4, 1);

		VBox topContainer = new VBox(10, metadataGrid, inputGrid);
		borderPane.setTop(topContainer);

		treeTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
			if (newSelection != null && newSelection.getValue() != null) {
				handleTaskSelection(newSelection.getValue());
			} else {
				handleTaskSelection(null);
			}
		});

		return borderPane;
	}

	private void handleTaskSelection(Task task) {
		if (task != null) {
			populateInputFieldsFromTask(task);
			updateTaskButton.setDisable(task.isLocked());
		} else {
			clearInputFields();
			updateTaskButton.setDisable(true);
		}
	}

	private Task createTaskFromInputFields() {
		Task newTask;
		if (categoryCheckBox.isSelected()) {
			newTask = new Task(nameField.getText(), true);
		} else {
			newTask = new Task(nameField.getText(), assigneeField.getText(), startDatePicker.getValue(),
					endDatePicker.getValue(), progressSpinner.getValue());
		}
		List<String> predIds = Arrays.stream(predecessorIdsField.getText().split(",")).map(String::trim)
				.filter(id -> !id.isEmpty()).collect(Collectors.toList());
		newTask.setPredecessorIds(predIds);
		return newTask;
	}

	private void populateInputFieldsFromTask(Task task) {
		boolean isTaskLocked = task.isLocked();

		nameField.setText(task.getName());
		nameField.setDisable(isTaskLocked);

		categoryCheckBox.setSelected(task.isCategory());
		categoryCheckBox.setDisable(isTaskLocked);

		predecessorIdsField.setText(String.join(", ", task.getPredecessorIds()));
		predecessorIdsField.setDisable(isTaskLocked);

		boolean disableNonCategorySpecificFields = isTaskLocked || categoryCheckBox.isSelected();

		assigneeField.setDisable(disableNonCategorySpecificFields);
		startDatePicker.setDisable(disableNonCategorySpecificFields);
		endDatePicker.setDisable(disableNonCategorySpecificFields);
		progressSpinner.setDisable(disableNonCategorySpecificFields);

		if (!task.isCategory()) {
			assigneeField.setText(task.getAssignee());
			startDatePicker.setValue(task.getStartDate());
			endDatePicker.setValue(task.getEndDate());
			progressSpinner.getValueFactory().setValue(task.getProgress());
		} else {
			assigneeField.clear();
			startDatePicker.setValue(null);
			endDatePicker.setValue(null);
			progressSpinner.getValueFactory().setValue(0);
		}
	}

	private void clearInputFields() {
		nameField.clear();
		nameField.setDisable(false);
		assigneeField.clear();
		assigneeField.setDisable(false);
		startDatePicker.setValue(LocalDate.now());
		startDatePicker.setDisable(false);
		endDatePicker.setValue(LocalDate.now().plusDays(7));
		endDatePicker.setDisable(false);
		progressSpinner.getValueFactory().setValue(0);
		progressSpinner.setDisable(false);
		categoryCheckBox.setSelected(false);
		categoryCheckBox.setDisable(false);
		predecessorIdsField.clear();
		predecessorIdsField.setDisable(false);
	}

	private void showAlert(String title, String content) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.setContentText(content);
		DialogPane dialogPane = alert.getDialogPane();
		try {
			String cssPath = Objects.requireNonNull(getClass().getResource("styles.css")).toExternalForm();
			dialogPane.getStylesheets().add(cssPath);
			dialogPane.getStyleClass().add("custom-alert");
		} catch (Exception e) {
			System.err.println("Failed to load CSS for alert: " + e.getMessage());
			dialogPane.setStyle("-fx-base: #3c3f41; -fx-text-fill: white; -fx-background-color: #2b2b2b;");
		}
		alert.showAndWait();
	}

	private Callback<TreeTableColumn<Task, LocalDate>, TreeTableCell<Task, LocalDate>> createDateCellFactory(
			boolean isStartDateColumn) {
		return column -> new TreeTableCell<Task, LocalDate>() {
			private final DatePicker datePicker = new DatePicker();
			{
				datePicker.setConverter(new StringConverter<LocalDate>() {
					@Override
					public String toString(LocalDate date) {
						return (date == null) ? "" : dateFormatter.format(date);
					}

					@Override
					public LocalDate fromString(String string) {
						return (string == null || string.isEmpty()) ? null : LocalDate.parse(string, dateFormatter);
					}
				});
				datePicker.setOnAction(event -> {
					if (getTreeTableRow() != null && getTreeTableRow().getItem() != null) {
						if (getTreeTableRow().getItem().isLocked() || getTreeTableRow().getItem().isCategory()) {
							showAlert("수정 불가", "잠긴 작업 또는 카테고리 작업의 날짜는 수정할 수 없습니다.");
							datePicker.setValue(getItem());
							cancelEdit();
							return;
						}
						commitEdit(datePicker.getValue());
					}
				});
			}

			@Override
			public void startEdit() {
				if (getTreeTableRow() != null && getTreeTableRow().getItem() != null) {
					Task currentTask = getTreeTableRow().getItem();
					if (currentTask.isLocked() || currentTask.isCategory()) {
						cancelEdit();
						return;
					}
				}
				super.startEdit();
				if (isEmpty() || getItem() == null && !isEditing()) {
					cancelEdit();
					return;
				}
				setText(null);
				setGraphic(datePicker);
				datePicker.setValue(getItem());
				datePicker.requestFocus();
			}

			@Override
			public void cancelEdit() {
				super.cancelEdit();
				setText(getItem() == null ? "" : dateFormatter.format(getItem()));
				setGraphic(null);
			}

			@Override
			protected void updateItem(LocalDate item, boolean empty) {
				super.updateItem(item, empty);
				if (empty) {
					setText(null);
					setGraphic(null);
				} else {
					if (isEditing() && getTreeTableRow() != null && getTreeTableRow().getItem() != null
							&& !getTreeTableRow().getItem().isLocked() && !getTreeTableRow().getItem().isCategory()) {
						setText(null);
						datePicker.setValue(item);
						setGraphic(datePicker);
					} else {
						setText(item == null ? "" : dateFormatter.format(item));
						setGraphic(null);
					}
				}
			}
		};
	}

	private VBox createDashboardPane() {
		VBox dashboardPane = new VBox(20);
		dashboardPane.setPadding(new Insets(20));
		dashboardPane.setAlignment(Pos.TOP_CENTER);

		overallProgressLabel = new Label("전체 진척률: 0%");
		overallProgressLabel.setStyle("-fx-font-size: 1.5em;");

		overallProgressBar = new ProgressBar(0);
		overallProgressBar.setPrefWidth(400);

		tasksPieChart = new PieChart();
		tasksPieChart.setTitle("업무 상태별 분포");
		tasksPieChart.setLegendVisible(true);

		Label assigneeLoadTitle = new Label("담당자별 작업 수 (진행 중/예정)");
		assigneeLoadTitle.setStyle("-fx-font-size: 1.2em;");
		assigneeLoadSummaryArea = new TextArea();
		assigneeLoadSummaryArea.setEditable(false);
		assigneeLoadSummaryArea.setPrefHeight(150);
		assigneeLoadSummaryArea.setWrapText(true);

		dashboardPane.getChildren().addAll(new Label("프로젝트 전체 현황"), overallProgressLabel, overallProgressBar,
				tasksPieChart, assigneeLoadTitle, assigneeLoadSummaryArea);
		return dashboardPane;
	}

	private BorderPane createGanttChartPane() {
		BorderPane ganttPane = new BorderPane();
		ganttPane.setPadding(new Insets(10));

		ganttDisplayStartDatePicker = new DatePicker(LocalDate.now().withDayOfMonth(1).plusMonths(-2));
		ganttDisplayEndDatePicker = new DatePicker(LocalDate.now().withDayOfMonth(3).plusMonths(1).minusDays(1));
		Button drawGanttButton = new Button("간트 차트 그리기");
		drawGanttButton.setOnAction(e -> drawGanttChart());

		HBox dateSelectorBox = new HBox(10, new Label("시작일:"), ganttDisplayStartDatePicker, new Label("종료일:"),
				ganttDisplayEndDatePicker, drawGanttButton);
		dateSelectorBox.setAlignment(Pos.CENTER_LEFT);
		dateSelectorBox.setPadding(new Insets(0, 0, 10, 0));
		ganttPane.setTop(dateSelectorBox);

		ganttChartPane = new Pane();
		ganttCanvas = new Canvas(1200, 600);

		StackPane canvasContainer = new StackPane(ganttCanvas);
		ganttChartPane.getChildren().add(canvasContainer);

		ganttScrollPane = new ScrollPane(ganttChartPane);
		ganttScrollPane.setFitToWidth(true); // Let ScrollPane handle width fitting
		// ganttScrollPane.setFitToHeight(true); // Let ScrollPane handle height fitting
		// - may not be desired if canvas grows very tall
		ganttPane.setCenter(ganttScrollPane);

		// REMOVED BINDINGS that caused the error
		// ganttChartPane.prefWidthProperty().bind(ganttScrollPane.widthProperty());
		// ganttChartPane.prefHeightProperty().bind(ganttScrollPane.heightProperty());

		return ganttPane;
	}

	private void drawGanttChart() {
		if (ganttCanvas == null || ganttChartPane == null)
			return;

		LocalDate viewStartDate = ganttDisplayStartDatePicker.getValue();
		LocalDate viewEndDate = ganttDisplayEndDatePicker.getValue();

		if (viewStartDate == null || viewEndDate == null || viewStartDate.isAfter(viewEndDate)) {
			showAlert("기간 오류", "간트 차트 표시 기간을 올바르게 설정해주세요.");
			return;
		}

		GraphicsContext gc = ganttCanvas.getGraphicsContext2D();

		List<Task> tasksToDisplay = allTasksFlat.stream()
				.filter(task -> !task.isCategory() && task.getStartDate() != null && task.getEndDate() != null
						&& task.getStartDate().isBefore(viewEndDate.plusDays(1))
						&& task.getEndDate().isAfter(viewStartDate.minusDays(1)))
				.sorted(Comparator.comparing(Task::getStartDate, Comparator.nullsLast(LocalDate::compareTo))
						.thenComparing(Task::getName))
				.collect(Collectors.toList());

		long totalDaysInView = ChronoUnit.DAYS.between(viewStartDate, viewEndDate) + 1;
		if (totalDaysInView <= 0) {
			gc.clearRect(0, 0, ganttCanvas.getWidth(), ganttCanvas.getHeight());
			return;
		}

		double calculatedCanvasWidth = GANTT_TASK_NAME_WIDTH + totalDaysInView * GANTT_DAY_WIDTH;
		double calculatedCanvasHeight = GANTT_HEADER_HEIGHT + tasksToDisplay.size() * GANTT_ROW_HEIGHT + 20;

		ganttCanvas.setWidth(calculatedCanvasWidth);
		ganttCanvas.setHeight(calculatedCanvasHeight);
		// Set the preferred size of the Pane that contains the Canvas.
		// The ScrollPane will use this preferred size.
		ganttChartPane.setPrefSize(calculatedCanvasWidth, calculatedCanvasHeight);

		gc.clearRect(0, 0, ganttCanvas.getWidth(), ganttCanvas.getHeight());

		// --- Draw Header (Dates) ---
		gc.setFill(Color.web("#555555"));
		gc.fillRect(GANTT_TASK_NAME_WIDTH, 0, calculatedCanvasWidth - GANTT_TASK_NAME_WIDTH, GANTT_HEADER_HEIGHT);
		gc.setStroke(Color.web("#777777"));
		gc.setFill(Color.WHITE);
		gc.setFont(Font.font(10));
		gc.setTextAlign(TextAlignment.CENTER);

		for (long i = 0; i < totalDaysInView; i++) {
			LocalDate currentDate = viewStartDate.plusDays(i);
			double x = GANTT_TASK_NAME_WIDTH + i * GANTT_DAY_WIDTH;
			gc.strokeLine(x, 0, x, calculatedCanvasHeight);
			if (i % 7 == 0 || i == 0) {
				gc.fillText(currentDate.format(monthDayFormatter), x + GANTT_DAY_WIDTH / 2,
						GANTT_HEADER_HEIGHT / 2 + 5);
			}
			if (currentDate.getDayOfMonth() == 1 && totalDaysInView > 14) {
				gc.fillText(currentDate.getMonth().toString().substring(0, 3).toUpperCase(), x + GANTT_DAY_WIDTH / 2,
						GANTT_HEADER_HEIGHT * 0.25 + 5);
			}
		}
		gc.strokeLine(GANTT_TASK_NAME_WIDTH, GANTT_HEADER_HEIGHT, calculatedCanvasWidth, GANTT_HEADER_HEIGHT);

		// --- Draw Task Bars ---
		double yPos = GANTT_HEADER_HEIGHT;
		gc.setTextAlign(TextAlignment.LEFT);
		gc.setFont(Font.font(11));

		for (Task task : tasksToDisplay) {
			gc.setFill(Color.WHITE);
			gc.fillText(task.getName(), 10, yPos + GANTT_ROW_HEIGHT / 2 + 5, GANTT_TASK_NAME_WIDTH - 20);
			gc.strokeLine(0, yPos + GANTT_ROW_HEIGHT, calculatedCanvasWidth, yPos + GANTT_ROW_HEIGHT);

			if (task.getStartDate() == null || task.getEndDate() == null
					|| task.getStartDate().isAfter(task.getEndDate())) {
				yPos += GANTT_ROW_HEIGHT;
				continue;
			}

			LocalDate taskStart = task.getStartDate();
			LocalDate taskEnd = task.getEndDate();

			LocalDate effectiveTaskStart = taskStart.isBefore(viewStartDate) ? viewStartDate : taskStart;
			LocalDate effectiveTaskEnd = taskEnd.isAfter(viewEndDate) ? viewEndDate : taskEnd;

			long startOffsetDays = ChronoUnit.DAYS.between(viewStartDate, effectiveTaskStart);
			long endOffsetDays = ChronoUnit.DAYS.between(viewStartDate, effectiveTaskEnd);

			if (startOffsetDays < 0)
				startOffsetDays = 0;
			if (endOffsetDays >= totalDaysInView)
				endOffsetDays = totalDaysInView - 1;

			double barX = GANTT_TASK_NAME_WIDTH + startOffsetDays * GANTT_DAY_WIDTH;
			double barWidth = (endOffsetDays - startOffsetDays + 1) * GANTT_DAY_WIDTH;

			if (barWidth <= 0)
				barWidth = GANTT_DAY_WIDTH / 2;

			Color barColor;
			if (task.isLocked()) {
				barColor = Color.SLATEGRAY;
			} else if (task.getProgress() == 100) {
				barColor = Color.web("#28a745");
			} else if (task.getProgress() > 0) {
				barColor = Color.web("#007bff");
			} else {
				barColor = Color.web("#ffc107");
			}
			gc.setFill(barColor);
			gc.fillRect(barX, yPos + 5, barWidth, GANTT_ROW_HEIGHT - 10);

			if (!task.isLocked() && task.getProgress() > 0 && task.getProgress() < 100) {
				gc.setFill(barColor.darker());
				double progressWidth = barWidth * (task.getProgress() / 100.0);
				gc.fillRect(barX, yPos + 5, progressWidth, GANTT_ROW_HEIGHT - 10);
			}

			yPos += GANTT_ROW_HEIGHT;
		}
		gc.setStroke(Color.web("#777777"));
		gc.strokeRect(0, 0, GANTT_TASK_NAME_WIDTH, calculatedCanvasHeight);
		gc.strokeLine(GANTT_TASK_NAME_WIDTH, 0, GANTT_TASK_NAME_WIDTH, calculatedCanvasHeight);
	}

	private void rebuildFlatTaskList() {
		allTasksFlat.clear();
		List<Task> tempFlatList = new ArrayList<>();
		for (Task rootTask : rootTasks) {
			collectTasksRecursivelyFromData(rootTask, tempFlatList);
		}
		allTasksFlat.addAll(tempFlatList);
	}

	private void collectTasksRecursivelyFromData(Task task, List<Task> flatList) {
		if (task == null)
			return;
		flatList.add(task);
		if (task.getChildren() != null) {
			for (Task child : task.getChildren()) {
				collectTasksRecursivelyFromData(child, flatList);
			}
		}
	}

	private void updateDashboard() {
		if (overallProgressLabel == null || overallProgressBar == null || tasksPieChart == null
				|| assigneeLoadSummaryArea == null) {
			return;
		}

		if (allTasksFlat.isEmpty()) {
			overallProgressBar.setProgress(0);
			overallProgressLabel.setText("전체 진척률: 0%");
			tasksPieChart.setData(FXCollections.observableArrayList(new PieChart.Data("업무 없음", 1)));
			assigneeLoadSummaryArea.setText("담당자별 작업 정보 없음");
			applyPieChartColors();
			return;
		}

		double totalProgressSum = 0;
		int numTasksForProgress = 0;
		long toDoCount = 0;
		long inProgressCount = 0;
		long completedCount = 0;
		Map<String, Integer> assigneeTaskCount = new HashMap<>();

		for (Task task : allTasksFlat) {
			if (!task.isCategory()) {
				totalProgressSum += task.getProgress();
				numTasksForProgress++;
				if (task.getProgress() == 0)
					toDoCount++;
				else if (task.getProgress() == 100)
					completedCount++;
				else
					inProgressCount++;

				if (task.getProgress() < 100 && task.getAssignee() != null && !task.getAssignee().trim().isEmpty()) {
					assigneeTaskCount.merge(task.getAssignee().trim(), 1, Integer::sum);
				}
			}
		}

		double averageProgress = (numTasksForProgress > 0) ? totalProgressSum / numTasksForProgress : 0;
		overallProgressBar.setProgress(averageProgress / 100.0);
		overallProgressLabel.setText(String.format("전체 진척률: %.1f%%", averageProgress));

		ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
		if (toDoCount > 0)
			pieChartData.add(new PieChart.Data("진행 예정 (" + toDoCount + ")", toDoCount));
		if (inProgressCount > 0)
			pieChartData.add(new PieChart.Data("진행 중 (" + inProgressCount + ")", inProgressCount));
		if (completedCount > 0)
			pieChartData.add(new PieChart.Data("완료 (" + completedCount + ")", completedCount));

		if (pieChartData.isEmpty()) {
			pieChartData.add(
					new PieChart.Data(numTasksForProgress == 0 && !allTasksFlat.isEmpty() ? "카테고리만 존재" : "업무 없음", 1));
		}
		
		tasksPieChart.setData(pieChartData);
		applyPieChartColors();

		if (assigneeTaskCount.isEmpty()) {
			assigneeLoadSummaryArea.setText("진행 중이거나 예정된 작업에 할당된 담당자 정보 없음.");
		} else {
			StringBuilder summary = new StringBuilder();
			assigneeTaskCount.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
					.forEach(entry -> summary.append(entry.getKey()).append(": ").append(entry.getValue())
							.append(" 건\n"));
			assigneeLoadSummaryArea.setText(summary.toString());
		}
	}

	private void applyPieChartColors() {
		if (tasksPieChart == null)
			return;
		int i = 0;
		for (PieChart.Data data : tasksPieChart.getData()) {
			Node node = data.getNode();
			if (node != null) {
				node.getStyleClass().removeIf(style -> style.startsWith("custom-pie-color-"));
				String styleClass = "default-color" + (i % 8) + ".chart-pie";
				String specificStyle = "";
				if (data.getName().startsWith("진행 예정"))
					specificStyle = "-fx-pie-color: #ffc107;";
				else if (data.getName().startsWith("진행 중"))
					specificStyle = "-fx-pie-color: #007bff;"; 
				else if (data.getName().startsWith("완료"))
					specificStyle = "-fx-pie-color: #28a745;";
				else if (data.getName().startsWith("업무 없음") || data.getName().startsWith("카테고리만 존재"))
					specificStyle = "-fx-pie-color: #6c757d;";

				if (!specificStyle.isEmpty()) {
					node.setStyle(specificStyle);
				} else {
					if (!node.getStyleClass().contains(styleClass)) {
						node.getStyleClass().add(styleClass);
					}
				}
			}
			i++;
		}
	}

	private void loadSampleData() {
		Task research = new Task("Study & Research", true);
		Task deskResearch = new Task("데스크 리서치", "기획자", LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 10), 80);
		Task meeting = new Task("중간 회의", "팀 전체", LocalDate.of(2024, 5, 15), LocalDate.of(2024, 5, 15), 100);
		research.addChild(deskResearch);
		research.addChild(meeting);
		deskResearch.setPredecessorIds(new ArrayList<>());

		Task ideation = new Task("Ideation & Strategy", true);
		Task researchSummary = new Task("리서치 결과 정리", "기획자", LocalDate.of(2024, 5, 11), LocalDate.of(2024, 5, 14), 100);
		researchSummary.setPredecessorIds(List.of(deskResearch.getId()));
		Task researchReport = new Task("리서치 내용 보고", "기획자", LocalDate.of(2024, 5, 16), LocalDate.of(2024, 5, 17), 0);
		researchReport.setPredecessorIds(List.of(researchSummary.getId(), meeting.getId()));

		ideation.addChild(researchSummary);
		ideation.addChild(researchReport);
		researchReport.setLocked(true);

		Task ux = new Task("UX design & Follow-up", true);
		Task wireframe = new Task("메인 와이어프레임 제작", "디자이너", LocalDate.of(2024, 5, 20), LocalDate.of(2024, 5, 31), 30);
		wireframe.setPredecessorIds(List.of(researchReport.getId()));
		ux.addChild(wireframe);

		Task dev = new Task("Development", "개발자 A", LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 28), 0);
		dev.setPredecessorIds(List.of(wireframe.getId()));

		rootTasks.addAll(research, ideation, ux, new TreeItem<>(dev).getValue());
		populateTreeTableViewFromRootTasks();

		if (treeTableView != null && treeTableView.getRoot() != null) {
			treeTableView.getRoot().getChildren().forEach(item -> {
				if (item.getValue() != null && (item.getValue().getName().equals("Study & Research")
						|| item.getValue().getName().equals("Ideation & Strategy"))) {
					item.setExpanded(true);
				}
			});
		}
		rebuildFlatTaskList(); // This also calls updateDashboard and drawGanttChart via listeners
	}

	private void exportToCsv() {
        updateMetadataFromInputFields(); // Ensure current metadata is captured
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("CSV로 내보내기");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        File file = fileChooser.showSaveDialog(primaryStage);

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                // Write metadata headers and data
                writer.println("Meta_ProjectName," + escapeCsv(projectMetadata.getProjectName()));
                writer.println("Meta_StartDate," + (projectMetadata.getProjectStartDate() != null ? projectMetadata.getProjectStartDate().format(dateFormatter) : ""));
                writer.println("Meta_EndDate," + (projectMetadata.getProjectEndDate() != null ? projectMetadata.getProjectEndDate().format(dateFormatter) : ""));
                writer.println("Meta_Author," + escapeCsv(projectMetadata.getAuthor()));
                writer.println("Meta_LastModified," + (projectMetadata.getLastModifiedDate() != null ? projectMetadata.getLastModifiedDate().format(dateFormatter) : ""));
                writer.println(); // Blank line separator

                // Write task headers
                writer.println("ID,ParentID,Name,Assignee,StartDate,EndDate,Progress,IsCategory,IsLocked,PredecessorIDs");
                List<Task> flatTasksForCsv = new ArrayList<>();
                for (Task task : rootTasks) {
                    collectTasksForCsv(task, "", flatTasksForCsv); // Use empty string for root's parent
                }
                for (Task task : flatTasksForCsv) {
                    writer.println(String.join(",",
                            escapeCsv(task.getId()),
                            escapeCsv(task.getParentId()), 
                            escapeCsv(task.getName()),
                            escapeCsv(task.getAssignee()),
                            task.getStartDate() != null ? task.getStartDate().format(dateFormatter) : "",
                            task.getEndDate() != null ? task.getEndDate().format(dateFormatter) : "",
                            String.valueOf(task.getProgress()),
                            String.valueOf(task.isCategory()),
                            String.valueOf(task.isLocked()),
                            escapeCsv(String.join(";", task.getPredecessorIds())) // Join with semicolon for CSV
                    ));
                }
                showAlert("CSV 내보내기 완료", "프로젝트가 CSV 파일로 성공적으로 내보내졌습니다.");
            } catch (IOException e) {
                e.printStackTrace();
                showAlert("CSV 내보내기 오류", "CSV 파일 내보내기 중 오류 발생: " + e.getMessage());
            }
        }
    }

	private void collectTasksForCsv(Task task, String parentId, List<Task> flatList) {
        task.setParentId(parentId); // Temporarily set parentId for CSV export logic
        flatList.add(task);
        if (task.getChildren() != null) {
            for (Task child : task.getChildren()) {
                collectTasksForCsv(child, task.getId(), flatList);
            }
        }
    }

	 private String escapeCsv(String data) {
	        if (data == null) return "";
	        String escapedData = data.replace("\"", "\"\""); // Escape double quotes
	        if (data.contains(",") || data.contains("\"") || data.contains("\n") || data.contains("\r")) {
	            escapedData = "\"" + escapedData + "\""; // Enclose in double quotes if it contains delimiter, quote, or newline
	        }
	        return escapedData;
	    }

	 private void importFromCsv() {
	        FileChooser fileChooser = new FileChooser();
	        fileChooser.setTitle("CSV에서 가져오기");
	        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
	        File file = fileChooser.showOpenDialog(primaryStage);

	        if (file != null) {
	            try {
	                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
	                Map<String, Task> taskMap = new HashMap<>(); // To build hierarchy
	                List<Task> importedRootTasks = new ArrayList<>();
	                ProjectMetadata importedMetadata = new ProjectMetadata(); // Default
	                
	                boolean readingTasks = false;
	                int taskHeaderIndex = -1;

	                for (int i = 0; i < lines.size(); i++) {
	                    String line = lines.get(i);
	                    if (line.trim().isEmpty()) {
	                        if (!readingTasks) readingTasks = true; 
	                        continue;
	                    }
	                    // Basic CSV split, does not handle quotes within fields perfectly
	                    String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);


	                    if (!readingTasks) { // Reading metadata
	                        if (values.length >= 2) {
	                            String key = values[0].trim();
	                            String value = unescapeCsv(values[1].trim());
	                            try {
	                                if ("Meta_ProjectName".equals(key)) importedMetadata.setProjectName(value);
	                                else if ("Meta_StartDate".equals(key) && !value.isEmpty()) importedMetadata.setProjectStartDate(LocalDate.parse(value, dateFormatter));
	                                else if ("Meta_EndDate".equals(key) && !value.isEmpty()) importedMetadata.setProjectEndDate(LocalDate.parse(value, dateFormatter));
	                                else if ("Meta_Author".equals(key)) importedMetadata.setAuthor(value);
	                                else if ("Meta_LastModified".equals(key) && !value.isEmpty()) importedMetadata.setLastModifiedDate(LocalDate.parse(value, dateFormatter));
	                            } catch (DateTimeParseException e) {
	                                System.err.println("CSV 메타데이터 날짜 파싱 오류: " + value + " - " + e.getMessage());
	                            }
	                        }
	                        continue;
	                    }
	                    
	                    // Find task header row
	                    if (taskHeaderIndex == -1 && values.length > 0 && "ID".equals(unescapeCsv(values[0].trim()))) {
	                        taskHeaderIndex = i;
	                        continue; // Skip header row
	                    }
	                    if (taskHeaderIndex == -1) continue; // Still searching for header or invalid format

	                    if (values.length == 10) { // ID,ParentID,Name,Assignee,StartDate,EndDate,Progress,IsCategory,IsLocked,PredecessorIDs
	                        try {
	                            String id = unescapeCsv(values[0].trim());
	                            String parentId = unescapeCsv(values[1].trim());
	                            String name = unescapeCsv(values[2].trim());
	                            String assignee = unescapeCsv(values[3].trim());
	                            LocalDate startDate = values[4].trim().isEmpty() ? null : LocalDate.parse(values[4].trim(), dateFormatter);
	                            LocalDate endDate = values[5].trim().isEmpty() ? null : LocalDate.parse(values[5].trim(), dateFormatter);
	                            int progress = Integer.parseInt(values[6].trim());
	                            boolean isCategory = Boolean.parseBoolean(values[7].trim());
	                            boolean isLocked = Boolean.parseBoolean(values[8].trim());
	                            List<String> predecessorIds = Arrays.stream(unescapeCsv(values[9].trim()).split(";"))
	                                                              .map(String::trim)
	                                                              .filter(s -> !s.isEmpty())
	                                                              .collect(Collectors.toList());

	                            Task task;
	                            if (isCategory) {
	                                task = new Task(name, true);
	                            } else {
	                                task = new Task(name, assignee, startDate, endDate, progress);
	                            }
	                            task.setIdDataForImport(id); 
	                            task.setParentId(parentId); 
	                            task.setLocked(isLocked);
	                            task.setPredecessorIds(predecessorIds);
	                            
	                            taskMap.put(id, task);

	                        } catch (Exception e) {
	                            System.err.println("CSV 작업 데이터 파싱 오류: " + line + " - " + e.getMessage());
	                        }
	                    }
	                }

	                // Reconstruct hierarchy
	                for (Task task : taskMap.values()) {
	                    String parentId = task.getParentId();
	                    if (parentId != null && !parentId.isEmpty() && taskMap.containsKey(parentId)) {
	                        taskMap.get(parentId).addChild(task);
	                    } else {
	                        importedRootTasks.add(task);
	                    }
	                }
	                
	                projectMetadata = importedMetadata;
	                rootTasks.setAll(importedRootTasks);

	                updateMetadataInputFields();
	                populateTreeTableViewFromRootTasks();
	                rebuildFlatTaskList();
	                showAlert("CSV 가져오기 완료", "CSV 파일에서 데이터를 가져왔습니다.");

	            } catch (IOException e) {
	                e.printStackTrace();
	                showAlert("CSV 가져오기 오류", "CSV 파일 가져오기 중 오류 발생: " + e.getMessage());
	            }
	        }
	    }

	 private String unescapeCsv(String data) {
	        if (data == null) return "";
	        String d = data.trim();
	        if (d.startsWith("\"") && d.endsWith("\"")) {
	            d = d.substring(1, d.length() - 1);
	        }
	        return d.replace("\"\"", "\""); // Unescape double quotes
	    }

	  private void exportToJson() {
	        updateMetadataFromInputFields(); // Ensure current metadata is captured
	        FileChooser fileChooser = new FileChooser();
	        fileChooser.setTitle("JSON으로 내보내기");
	        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
	        File file = fileChooser.showSaveDialog(primaryStage);

	        if (file != null) {
	            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
	                writer.println("{");
	                // Metadata
	                writer.println("  \"metadata\": {");
	                writer.println("    \"projectName\": \"" + escapeJson(projectMetadata.getProjectName()) + "\",");
	                writer.println("    \"projectStartDate\": \"" + (projectMetadata.getProjectStartDate() != null ? projectMetadata.getProjectStartDate().format(dateFormatter) : "") + "\",");
	                writer.println("    \"projectEndDate\": \"" + (projectMetadata.getProjectEndDate() != null ? projectMetadata.getProjectEndDate().format(dateFormatter) : "") + "\",");
	                writer.println("    \"author\": \"" + escapeJson(projectMetadata.getAuthor()) + "\",");
	                writer.println("    \"lastModifiedDate\": \"" + (projectMetadata.getLastModifiedDate() != null ? projectMetadata.getLastModifiedDate().format(dateFormatter) : "") + "\"");
	                writer.println("  },");
	                // Tasks
	                writer.println("  \"tasks\": [");
	                for (int i = 0; i < rootTasks.size(); i++) {
	                    writer.print(taskToJson(rootTasks.get(i), "    "));
	                    if (i < rootTasks.size() - 1) {
	                        writer.println(",");
	                    } else {
	                        writer.println();
	                    }
	                }
	                writer.println("  ]");
	                writer.println("}");
	                showAlert("JSON 내보내기 완료", "프로젝트가 JSON 파일로 성공적으로 내보내졌습니다.");
	            } catch (IOException e) {
	                e.printStackTrace();
	                showAlert("JSON 내보내기 오류", "JSON 파일 내보내기 중 오류 발생: " + e.getMessage());
	            }
	        }
	    }

	private String taskToJson(Task task, String indent) {
		StringBuilder sb = new StringBuilder();
		sb.append(indent).append("{\n");
		sb.append(indent).append("  \"id\": \"").append(escapeJson(task.getId())).append("\",\n");
		// parentId is implicit in JSON structure, but can be added for explicitness if
		// needed for other systems
		// sb.append(indent).append(" \"parentId\":
		// \"").append(escapeJson(task.getParentId())).append("\",\n");
		sb.append(indent).append("  \"name\": \"").append(escapeJson(task.getName())).append("\",\n");
		sb.append(indent).append("  \"assignee\": \"").append(escapeJson(task.getAssignee())).append("\",\n");
		sb.append(indent).append("  \"startDate\": \"")
				.append(task.getStartDate() != null ? task.getStartDate().format(dateFormatter) : "").append("\",\n");
		sb.append(indent).append("  \"endDate\": \"")
				.append(task.getEndDate() != null ? task.getEndDate().format(dateFormatter) : "").append("\",\n");
		sb.append(indent).append("  \"progress\": ").append(task.getProgress()).append(",\n");
		sb.append(indent).append("  \"isCategory\": ").append(task.isCategory()).append(",\n");
		sb.append(indent).append("  \"isLocked\": ").append(task.isLocked()).append(",\n");
		sb.append(indent).append("  \"predecessorIds\": [").append(task.getPredecessorIds().stream()
				.map(id -> "\"" + escapeJson(id) + "\"").collect(Collectors.joining(", "))).append("],\n");
		sb.append(indent).append("  \"children\": [\n");
		if (task.getChildren() != null) {
			for (int i = 0; i < task.getChildren().size(); i++) {
				sb.append(taskToJson(task.getChildren().get(i), indent + "    "));
				if (i < task.getChildren().size() - 1) {
					sb.append(",\n");
				} else {
					sb.append("\n");
				}
			}
		}
		sb.append(indent).append("  ]\n");
		sb.append(indent).append("}");
		return sb.toString();
	}

	private String escapeJson(String data) {
		if (data == null)
			return "";
		return data.replace("\\", "\\\\").replace("\"", "\\\"").replace("\b", "\\b").replace("\f", "\\f")
				.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
	}

	private void importFromJson() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("JSON에서 가져오기");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        File file = fileChooser.showOpenDialog(primaryStage);

        if (file != null) {
            try {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                
                ProjectMetadata newMetadata = parseProjectMetadataFromJson(content);
                List<Task> newRootTasks = parseTasksFromJson(content);

                if (newMetadata != null) {
                    projectMetadata = newMetadata;
                } else {
                    projectMetadata = new ProjectMetadata(); // Reset if metadata parsing failed
                    showAlert("경고", "JSON에서 메타데이터를 파싱하는 데 실패했습니다. 기본 메타데이터가 사용됩니다.");
                }

                rootTasks.setAll(newRootTasks != null ? newRootTasks : new ArrayList<>());

                updateMetadataInputFields();
                populateTreeTableViewFromRootTasks();
                rebuildFlatTaskList(); // This is crucial
                updateDashboard();
                showAlert("JSON 가져오기 완료", "JSON 파일에서 데이터를 성공적으로 가져왔습니다.");

            } catch (IOException e) {
                e.printStackTrace();
                showAlert("JSON 가져오기 오류", "JSON 파일 가져오기 중 오류 발생: " + e.getMessage());
            } catch (Exception e) { // Catch broader parsing exceptions
                e.printStackTrace();
                showAlert("JSON 파싱 오류", "JSON 파일 파싱 중 오류 발생: " + e.getMessage());
            }
        }
    }

	private ProjectMetadata parseProjectMetadataFromJson(String jsonContent) {
        try {
            // Pattern to find the metadata object: "metadata"\s*:\s*\{ (captures content inside braces) \}
            Pattern metaPattern = Pattern.compile("\"metadata\"\\s*:\\s*\\{([^}]*)\\}", Pattern.DOTALL);
            Matcher metaMatcher = metaPattern.matcher(jsonContent);
            if (metaMatcher.find()) {
                String metaBlock = metaMatcher.group(1); // Content inside the metadata braces
                ProjectMetadata meta = new ProjectMetadata();
                meta.setProjectName(extractJsonStringValue(metaBlock, "projectName"));
                String startDateStr = extractJsonStringValue(metaBlock, "projectStartDate");
                if (startDateStr != null && !startDateStr.isEmpty()) meta.setProjectStartDate(LocalDate.parse(startDateStr, dateFormatter));
                String endDateStr = extractJsonStringValue(metaBlock, "projectEndDate");
                if (endDateStr != null && !endDateStr.isEmpty()) meta.setProjectEndDate(LocalDate.parse(endDateStr, dateFormatter));
                meta.setAuthor(extractJsonStringValue(metaBlock, "author"));
                String lastModDateStr = extractJsonStringValue(metaBlock, "lastModifiedDate");
                 if (lastModDateStr != null && !lastModDateStr.isEmpty()) meta.setLastModifiedDate(LocalDate.parse(lastModDateStr, dateFormatter));
                return meta;
            }
        } catch (Exception e) {
            System.err.println("JSON 메타데이터 파싱 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
        return null; // Return null or a default ProjectMetadata if parsing fails
    }

	private List<Task> parseTasksFromJson(String jsonContent) {
        try {
            // Pattern to find the tasks array: "tasks"\s*:\s*\[ (captures content inside brackets) \]
            Pattern tasksPattern = Pattern.compile("\"tasks\"\\s*:\\s*\\[(.*)\\]", Pattern.DOTALL);
            Matcher tasksMatcher = tasksPattern.matcher(jsonContent);
            if (tasksMatcher.find()) {
                String tasksArrayContent = tasksMatcher.group(1).trim(); // Content inside the tasks array brackets
                return parseTaskArrayString(tasksArrayContent);
            }
        } catch (Exception e) {
            System.err.println("JSON 작업 목록 파싱 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
        return new ArrayList<>(); // Return empty list if parsing fails or no tasks found
    }

	// Parses a string representing an array of JSON task objects
    private List<Task> parseTaskArrayString(String tasksArrayContent) {
        List<Task> tasks = new ArrayList<>();
        if (tasksArrayContent.isEmpty()) return tasks;

        // This regex tries to find top-level objects {...} within the array string.
        // It's a simplified approach and might fail for very complex or malformed JSON.
        Pattern taskObjectPattern = Pattern.compile("\\{([^\\{\\}]*+(?:\\{(?:[^\\{\\}]*+(?:\\{[^\\{\\}]*\\})*)*\\}[^\\{\\}]*)*)\\}");
        Matcher taskObjectMatcher = taskObjectPattern.matcher(tasksArrayContent);

        while (taskObjectMatcher.find()) {
            String taskJson = taskObjectMatcher.group(0); // The full matched object string "{...}"
            Task task = parseTaskObject(taskJson);
            if (task != null) {
                tasks.add(task);
            }
        }
        return tasks;
    }


    // Parses a single JSON task object string
    private Task parseTaskObject(String taskJson) {
        try {
            String id = extractJsonStringValue(taskJson, "id");
            String name = extractJsonStringValue(taskJson, "name");
            boolean isCategory = extractJsonBooleanValue(taskJson, "isCategory");

            Task task;
            if (isCategory) {
                task = new Task(name, true);
            } else {
                String assignee = extractJsonStringValue(taskJson, "assignee");
                LocalDate startDate = extractJsonDateValue(taskJson, "startDate");
                LocalDate endDate = extractJsonDateValue(taskJson, "endDate");
                int progress = extractJsonIntValue(taskJson, "progress");
                task = new Task(name, assignee, startDate, endDate, progress);
            }
            task.setIdDataForImport(id); // Set the original ID
            task.setLocked(extractJsonBooleanValue(taskJson, "isLocked"));
            task.setPredecessorIds(extractJsonStringListValue(taskJson, "predecessorIds"));

            // Recursively parse children
            Pattern childrenPattern = Pattern.compile("\"children\"\\s*:\\s*\\[(.*)\\]", Pattern.DOTALL);
            Matcher childrenMatcher = childrenPattern.matcher(taskJson);
            if (childrenMatcher.find()) {
                String childrenArrayContent = childrenMatcher.group(1).trim();
                List<Task> children = parseTaskArrayString(childrenArrayContent); // Recursive call
                for (Task child : children) {
                    if (child != null) { // Ensure child parsed correctly
                       task.addChild(child);
                    }
                }
            }
            return task;
        } catch (Exception e) {
            System.err.println("JSON 작업 객체 파싱 오류: " + taskJson.substring(0, Math.min(taskJson.length(), 100)) + "... - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    // Helper methods for extracting JSON values (simplified, using regex)
    private String extractJsonStringValue(String json, String key) {
        // Pattern: "key"\s*:\s*" (captures value) "
        // The value can contain escaped quotes. (?:[^"\\]|\\.)* matches any char except quote or backslash, or any escaped char.
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\"); // Unescape basic sequences
        }
        return ""; // Return empty for missing or non-string values for simplicity
    }

    private int extractJsonIntValue(String json, String key) {
        // Pattern: "key"\s*:\s* (captures digits)
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) { 
                System.err.println("Error parsing int for key " + key + ": " + matcher.group(1));
                return 0; 
            }
        }
        return 0; // Default if not found or not a number
    }

    private boolean extractJsonBooleanValue(String json, String key) {
        // Pattern: "key"\s*:\s* (true|false)
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Boolean.parseBoolean(matcher.group(1));
        }
        return false; // Default if not found or not a boolean
    }
    
    private LocalDate extractJsonDateValue(String json, String key){
        String dateStr = extractJsonStringValue(json, key); // Dates are stored as strings in our JSON
        if(dateStr != null && !dateStr.isEmpty()){
            try {
                return LocalDate.parse(dateStr, dateFormatter);
            } catch (DateTimeParseException e) {
                System.err.println("Error parsing date for key " + key + ": " + dateStr + " - " + e.getMessage());
                return null;
            }
        }
        return null;
    }
    
    private List<String> extractJsonStringListValue(String json, String key) {
        List<String> list = new ArrayList<>();
        // Pattern: "key"\s*:\s*\[ (captures content inside brackets) \]
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String arrayContent = matcher.group(1).trim();
            if (!arrayContent.isEmpty()) {
                // Split by comma, then trim and remove quotes for each item
                String[] items = arrayContent.split(",");
                for (String item : items) {
                    String trimmedItem = item.trim();
                    if (trimmedItem.startsWith("\"") && trimmedItem.endsWith("\"")) {
                        trimmedItem = trimmedItem.substring(1, trimmedItem.length() - 1);
                    }
                    list.add(trimmedItem.replace("\\\"", "\"").replace("\\\\", "\\")); // Unescape
                }
            }
        }
        return list;
    }

	public static void main(String[] args) {
		launch(args);
	}

	// WbsProjectManager.java 내부에 추가될 메서드 (또는 기존 메서드 수정)

	// ... javafx.scene.SnapshotParameters, javafx.scene.image.WritableImage, javafx.embed.swing.SwingFXUtils ...
	// ... javax.imageio.ImageIO, java.awt.image.BufferedImage, java.io.ByteArrayOutputStream, java.util.Base64 ...
	// 위 import 문들이 WbsProjectManager.java 상단에 필요합니다.

	private String takeSnapshotAndEncode(Node node) {
	    if (node == null || node.getScene() == null || node.getScene().getWindow() == null) {
	        System.err.println("스냅샷을 생성할 노드가 준비되지 않았습니다 (Scene 또는 Window 없음).");
	        return null;
	    }
	    // 스냅샷을 찍기 전에 노드가 실제로 렌더링될 기회를 주기 위해 Platform.runLater를 사용할 수 있으나,
	    // 메뉴 액션 핸들러 내에서는 이미 UI 스레드이므로 직접 호출 가능할 수 있습니다.
	    // 만약 문제가 발생하면 Platform.runLater로 감싸는 것을 고려하세요.
	    
	    WritableImage writableImage = new WritableImage((int) node.getBoundsInParent().getWidth(), (int) node.getBoundsInParent().getHeight());
	    SnapshotParameters params = new SnapshotParameters();
	    // params.setFill(Color.TRANSPARENT); // 배경을 투명하게 하려면
	    node.snapshot(params, writableImage);

	    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	    try {
	        // SwingFXUtils를 사용하려면 java.desktop 모듈이 필요할 수 있습니다.
	        // (module-info.java 파일에 requires java.desktop; 추가)
	        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(writableImage, null);
	        ImageIO.write(bufferedImage, "png", outputStream);
	        byte[] imageBytes = outputStream.toByteArray();
	        return Base64.getEncoder().encodeToString(imageBytes);
	    } catch (IOException e) {
	        System.err.println("이미지 스냅샷 인코딩 오류: " + e.getMessage());
	        e.printStackTrace();
	        return null;
	    }
	}


	private void exportDashboardToHtmlReport() {
	    if (overallProgressBar == null || tasksPieChart == null || assigneeLoadSummaryArea == null) {
	        showAlert("오류", "대시보드 데이터가 아직 준비되지 않았습니다.");
	        return;
	    }

	    // 1. 현재 대시보드 데이터 수집
	    double currentOverallProgress = overallProgressBar.getProgress();
	    Map<String, Long> statusCounts = new HashMap<>();
	    if (tasksPieChart.getData() != null) {
	        for (PieChart.Data data : tasksPieChart.getData()) {
	            String name = data.getName();
	            long count = (long) data.getPieValue();
	            String statusName = name.replaceAll("\\s*\\(.*\\)$", "").trim();
	            statusCounts.put(statusName, count);
	        }
	    }
	    // String assigneeSummaryText = assigneeLoadSummaryArea.getText(); // 텍스트 요약은 이미지로 대체

	    // 2. 스냅샷 생성 및 인코딩
	    String pieChartBase64 = takeSnapshotAndEncode(tasksPieChart);
	    String assigneeLoadBase64 = takeSnapshotAndEncode(assigneeLoadSummaryArea);

	    if (pieChartBase64 == null) {
	        System.err.println("파이 차트 스냅샷 생성 실패. 보고서에 이미지가 포함되지 않을 수 있습니다.");
	    }
	    if (assigneeLoadBase64 == null) {
	        System.err.println("담당자 요약 스냅샷 생성 실패. 보고서에 이미지가 포함되지 않을 수 있습니다.");
	    }


	    // 3. FileChooser로 저장 위치 선택
	    FileChooser fileChooser = new FileChooser();
	    fileChooser.setTitle("대시보드 HTML 보고서 저장");
	    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML Files (*.html)", "*.html"));
	    fileChooser.setInitialFileName("WBS_Dashboard_Report_" + LocalDate.now().format(dateFormatter) + ".html");
	    File file = fileChooser.showSaveDialog(primaryStage);

	    if (file != null) {
	        // 4. DashboardHtmlExporter 호출
	        DashboardHtmlExporter exporter = new DashboardHtmlExporter();
	        exporter.exportDashboardToHtml(
	            this.projectMetadata, 
	            currentOverallProgress, 
	            statusCounts, 
	            pieChartBase64, 
	            assigneeLoadBase64, 
	            file
	        );
	        showAlert("내보내기 완료", "대시보드 보고서가 성공적으로 저장되었습니다:\n" + file.getAbsolutePath());
	    }
	}

	// 이 exportDashboardToHtmlReport 메서드를 호출하는 메뉴 항목을 File 메뉴에 추가해야 합니다.
	// 예시:
	// MenuItem exportDashboardHtmlItem = new MenuItem("대시보드 HTML로 내보내기...");
	// exportDashboardHtmlItem.setOnAction(e -> exportDashboardToHtmlReport());
	// exportMenu.getItems().add(exportDashboardHtmlItem); // 기존 exportMenu에 추가
}
