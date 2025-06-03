/**
 * 
 */
package org.kyj.fx.wbs.dashboard;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class DatabaseConfig {
	private final StringProperty id; // Unique ID for the configuration
	private final StringProperty dbType;
	private final StringProperty host;
	private final StringProperty port;
	private final StringProperty dbName;
	private final StringProperty username;
	private final StringProperty password; // Be cautious with storing passwords

	public DatabaseConfig(String id, String dbType, String host, String port, String dbName, String username,
			String password) {
		this.id = new SimpleStringProperty(id);
		this.dbType = new SimpleStringProperty(dbType);
		this.host = new SimpleStringProperty(host);
		this.port = new SimpleStringProperty(port);
		this.dbName = new SimpleStringProperty(dbName);
		this.username = new SimpleStringProperty(username);
		this.password = new SimpleStringProperty(password); // In a real app, encrypt this
	}

	// --- Getters for StringProperty (for TableView) ---
	public StringProperty idProperty() {
		return id;
	}

	public StringProperty dbTypeProperty() {
		return dbType;
	}

	public StringProperty hostProperty() {
		return host;
	}

	public StringProperty portProperty() {
		return port;
	}

	public StringProperty dbNameProperty() {
		return dbName;
	}

	public StringProperty usernameProperty() {
		return username;
	}

	public StringProperty passwordProperty() {
		return password;
	} // Not typically shown in table

	// --- Getters for String values ---
	public String getId() {
		return id.get();
	}

	public String getDbType() {
		return dbType.get();
	}

	public String getHost() {
		return host.get();
	}

	public String getPort() {
		return port.get();
	}

	public String getDbName() {
		return dbName.get();
	}

	public String getUsername() {
		return username.get();
	}

	public String getPassword() {
		return password.get();
	}

	// --- Setters for String values ---
	public void setId(String id) {
		this.id.set(id);
	}

	public void setDbType(String dbType) {
		this.dbType.set(dbType);
	}

	public void setHost(String host) {
		this.host.set(host);
	}

	public void setPort(String port) {
		this.port.set(port);
	}

	public void setDbName(String dbName) {
		this.dbName.set(dbName);
	}

	public void setUsername(String username) {
		this.username.set(username);
	}

	public void setPassword(String password) {
		this.password.set(password);
	}

	@Override
	public String toString() {
		return String.join(",", getId(), getDbType(), getHost(), getPort(), getDbName(), getUsername(), getPassword());
	}

	public static DatabaseConfig fromString(String line) {
		String[] parts = line.split(",", 7); // Limit split to 7 parts for password
		if (parts.length == 7) {
			return new DatabaseConfig(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6]);
		}
		return null; // Or throw an exception for malformed lines
	}
}
