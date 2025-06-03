package org.kyj.fx.wbs.dashboard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

public class Task implements java.io.Serializable {
	// ... Task class unchanged from previous version ...
	private static final long serialVersionUID = 20230531L;

	private String idData;
	private String parentIdData;
	private String nameData;
	private String assigneeData;
	private LocalDate startDateData;
	private LocalDate endDateData;
	private int progressData;
	private boolean categoryData;
	private boolean lockedData = false;
	private List<String> predecessorIdsData = new ArrayList<>();
	private List<Task> childrenData = new ArrayList<>();

	private transient StringProperty id;
	private transient StringProperty name;
	private transient StringProperty assignee;
	private transient ObjectProperty<LocalDate> startDate;
	private transient ObjectProperty<LocalDate> endDate;
	private transient IntegerProperty progress;
	private transient BooleanProperty category;
	private transient BooleanProperty locked;
	private transient ListProperty<String> predecessorIds;
	private transient ObservableList<Task> children;
	
	private ObjectProperty<LocalDate> updateDate;

	public Task(String name, String assignee, LocalDate startDate, LocalDate endDate, int progress) {
		this.idData = "task_" + System.nanoTime() + "_" + (long) (Math.random() * 1000000);
		this.nameData = name;
		this.assigneeData = assignee;
		this.startDateData = startDate;
		this.endDateData = endDate;
		this.progressData = Math.max(0, Math.min(100, progress));
		this.categoryData = false;
		this.lockedData = false;
		this.predecessorIdsData = new ArrayList<>();
		this.updateDate = new SimpleObjectProperty<>(null);
	}

	public Task(String name, boolean isCategory) {
		this.idData = "cat_" + System.nanoTime() + "_" + (long) (Math.random() * 1000000);
		this.nameData = name;
		this.assigneeData = isCategory ? "" : "미지정";
		this.startDateData = null;
		this.endDateData = null;
		this.progressData = 0;
		this.categoryData = isCategory;
		this.lockedData = false;
		this.predecessorIdsData = new ArrayList<>();
		this.updateDate = new SimpleObjectProperty<>(null);
	}

	private Task() {
	}

	private void initializeFxProperties() {
		if (id == null)
			id = new SimpleStringProperty(this.idData);
		else
			id.set(this.idData);
		if (name == null)
			name = new SimpleStringProperty(this.nameData);
		else
			name.set(this.nameData);
		if (assignee == null)
			assignee = new SimpleStringProperty(this.assigneeData);
		else
			assignee.set(this.assigneeData);
		if (startDate == null)
			startDate = new SimpleObjectProperty<>(this.startDateData);
		else
			startDate.set(this.startDateData);
		if (endDate == null)
			endDate = new SimpleObjectProperty<>(this.endDateData);
		else
			endDate.set(this.endDateData);
		if (progress == null)
			progress = new SimpleIntegerProperty(this.progressData);
		else
			progress.set(this.progressData);
		if (category == null)
			category = new SimpleBooleanProperty(this.categoryData);
		else
			category.set(this.categoryData);
		if (locked == null)
			locked = new SimpleBooleanProperty(this.lockedData);
		else
			locked.set(this.lockedData);

		if (this.predecessorIdsData == null)
			this.predecessorIdsData = new ArrayList<>();
		if (predecessorIds == null)
			predecessorIds = new SimpleListProperty<>(FXCollections.observableArrayList(this.predecessorIdsData));
		else
			predecessorIds.set(FXCollections.observableArrayList(this.predecessorIdsData));

		predecessorIds.addListener((ListChangeListener<String>) c -> {
			this.predecessorIdsData.clear();
			this.predecessorIdsData.addAll(predecessorIds.get());
		});

		if (this.childrenData == null) {
			this.childrenData = new ArrayList<>();
		}
		if (children == null || !new ArrayList<>(children).equals(childrenData)) {
			children = FXCollections.observableArrayList(this.childrenData);
			children.addListener((ListChangeListener<Task>) c -> {
				this.childrenData.clear();
				this.childrenData.addAll(children);
			});
		}
	}

	private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
		in.defaultReadObject();
		if (this.predecessorIdsData == null) {
			this.predecessorIdsData = new ArrayList<>();
		}
		initializeFxProperties();
	}

	public String getId() {
		return idData;
	}

	public void setIdDataForImport(String id) {
		this.idData = id;
		if (this.id != null)
			this.id.set(id);
	}

	public StringProperty idProperty() {
		if (id == null)
			initializeFxProperties();
		return id;
	}

	public String getParentId() {
		return parentIdData;
	}

	public void setParentId(String parentId) {
		this.parentIdData = parentId;
	}

	public String getName() {
		return nameData;
	}

	public void setName(String nameVal) {
		this.nameData = nameVal;
		if (name != null)
			name.set(nameVal);
		else
			initializeFxProperties();
	}

	public StringProperty nameProperty() {
		if (name == null)
			initializeFxProperties();
		return name;
	}

	public String getAssignee() {
		return assigneeData;
	}

	public void setAssignee(String assigneeVal) {
		this.assigneeData = assigneeVal;
		if (assignee != null)
			assignee.set(assigneeVal);
		else
			initializeFxProperties();
	}

	public StringProperty assigneeProperty() {
		if (assignee == null)
			initializeFxProperties();
		return assignee;
	}

	public LocalDate getStartDate() {
		return startDateData;
	}

	public void setStartDate(LocalDate dateVal) {
		this.startDateData = dateVal;
		if (startDate != null)
			startDate.set(dateVal);
		else
			initializeFxProperties();
	}

	public ObjectProperty<LocalDate> startDateProperty() {
		if (startDate == null)
			initializeFxProperties();
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDateData;
	}

	public void setEndDate(LocalDate dateVal) {
		this.endDateData = dateVal;
		if (endDate != null)
			endDate.set(dateVal);
		else
			initializeFxProperties();
	}

	public ObjectProperty<LocalDate> endDateProperty() {
		if (endDate == null)
			initializeFxProperties();
		return endDate;
	}

	public int getProgress() {
		return progressData;
	}

	public void setProgress(int progressVal) {
		this.progressData = Math.max(0, Math.min(100, progressVal));
		if (progress != null)
			progress.set(this.progressData);
		else
			initializeFxProperties();
	}

	public IntegerProperty progressProperty() {
		if (progress == null)
			initializeFxProperties();
		return progress;
	}

	public boolean isCategory() {
		return categoryData;
	}

	public void setCategory(boolean isCat) {
		this.categoryData = isCat;
		if (category != null)
			category.set(isCat);
		else
			initializeFxProperties();
	}

	public BooleanProperty categoryProperty() {
		if (category == null)
			initializeFxProperties();
		return category;
	}

	public boolean isLocked() {
		return lockedData;
	}

	public void setLocked(boolean lockedVal) {
		this.lockedData = lockedVal;
		if (locked != null)
			locked.set(lockedVal);
		else
			initializeFxProperties();
	}

	public BooleanProperty lockedProperty() {
		if (locked == null)
			initializeFxProperties();
		return locked;
	}

	public List<String> getPredecessorIds() {
		if (predecessorIdsData == null)
			predecessorIdsData = new ArrayList<>();
		return predecessorIdsData;
	}

	public void setPredecessorIds(List<String> ids) {
		this.predecessorIdsData.clear();
		if (ids != null)
			this.predecessorIdsData.addAll(ids);
		if (predecessorIds != null)
			predecessorIds.set(FXCollections.observableArrayList(this.predecessorIdsData));
		// else initializeFxProperties(); // Avoid calling this here if predecessorIds
		// is already initialized
	}

	public ListProperty<String> predecessorIdsProperty() {
		if (predecessorIds == null)
			initializeFxProperties();
		return predecessorIds;
	}

	public ObservableList<Task> getChildren() {
		if (children == null)
			initializeFxProperties();
		return children;
	}

	public void addChild(Task child) {
		if (children == null)
			initializeFxProperties();
		children.add(child);
	}

	public void removeChild(Task child) {
		if (children == null)
			initializeFxProperties();
		children.remove(child);
	}

	@Override
	public String toString() {
		return nameProperty().get();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Task task = (Task) o;
		return Objects.equals(idData, task.idData);
	}

	/**
	 * 
	 */
	void updateUpdateDate() {
		this.updateDate.set(LocalDate.now());
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(idData);
	}

	/**
	 * @return
	 */
	public boolean isUpdate() {
		return this.updateDate.get() != null;
	}
}
