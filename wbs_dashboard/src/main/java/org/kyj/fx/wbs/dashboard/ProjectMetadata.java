package org.kyj.fx.wbs.dashboard;

import java.io.Serializable;
import java.time.LocalDate;

public class ProjectMetadata implements Serializable {
	private static final long serialVersionUID = 20230531L;
	private String projectName;
	private LocalDate projectStartDate;
	private LocalDate projectEndDate;
	private String author;
	private LocalDate lastModifiedDate;

	public ProjectMetadata() {
		this.projectName = "새 프로젝트";
		this.projectStartDate = LocalDate.now();
		this.projectEndDate = LocalDate.now().plusMonths(1);
		this.author = System.getProperty("user.name", "사용자");
		this.lastModifiedDate = LocalDate.now();
	}

	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public LocalDate getProjectStartDate() {
		return projectStartDate;
	}

	public void setProjectStartDate(LocalDate projectStartDate) {
		this.projectStartDate = projectStartDate;
	}

	public LocalDate getProjectEndDate() {
		return projectEndDate;
	}

	public void setProjectEndDate(LocalDate projectEndDate) {
		this.projectEndDate = projectEndDate;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public LocalDate getLastModifiedDate() {
		return lastModifiedDate;
	}

	public void setLastModifiedDate(LocalDate lastModifiedDate) {
		this.lastModifiedDate = lastModifiedDate;
	}
}
