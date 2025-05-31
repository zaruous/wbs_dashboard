package org.kyj.fx.wbs.dashboard;

import java.io.Serializable;
import java.util.List;

public class ProjectData implements Serializable {
	private static final long serialVersionUID = 20230531L; // Increment if structure changes
	ProjectMetadata metadata;
	List<Task> tasks;

	public ProjectData(ProjectMetadata metadata, List<Task> tasks) {
		this.metadata = metadata;
		this.tasks = tasks;
	}

	public ProjectMetadata getMetadata() {
		return metadata;
	}

	public List<Task> getTasks() {
		return tasks;
	}
}