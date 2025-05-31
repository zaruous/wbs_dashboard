package org.kyj.fx.wbs.dashboard;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Task 클래스와 ProjectMetadata 클래스가 이 파일과 동일한 패키지 또는 import 가능해야 합니다.
// 여기서는 WbsProjectManager 파일 내부에 static class로 존재한다고 가정합니다.

public class TaskDbManager {

    // --- 데이터베이스 연결 정보 (환경에 맞게 수정 필요) ---
    private static final String DB_URL = "jdbc:mariadb://localhost:3306/wbs"; // 예: your_wbs_db
    private static final String DB_USER = "tester1";
    private static final String DB_PASSWORD = "tester1";

    // 데이터베이스 연결을 가져오는 메서드
    private Connection getConnection() throws SQLException {
        try {
            Class.forName("org.mariadb.jdbc.Driver"); // MariaDB 드라이버 로드
        } catch (ClassNotFoundException e) {
            System.err.println("MariaDB JDBC 드라이버를 찾을 수 없습니다.");
            throw new SQLException("MariaDB JDBC Driver not found", e);
        }
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    // --- ProjectMetadata CRUD ---
    public void saveProjectMetadata(ProjectMetadata metadata) {
        String sql = "INSERT INTO project_metadata (project_id, project_name, project_start_date, project_end_date, author, last_modified_date) " +
                     "VALUES (?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE project_name = VALUES(project_name), project_start_date = VALUES(project_start_date), " +
                     "project_end_date = VALUES(project_end_date), author = VALUES(author), last_modified_date = VALUES(last_modified_date)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, "default_project"); // Assuming a single project for now
            pstmt.setString(2, metadata.getProjectName());
            pstmt.setDate(3, metadata.getProjectStartDate() != null ? java.sql.Date.valueOf(metadata.getProjectStartDate()) : null);
            pstmt.setDate(4, metadata.getProjectEndDate() != null ? java.sql.Date.valueOf(metadata.getProjectEndDate()) : null);
            pstmt.setString(5, metadata.getAuthor());
            pstmt.setDate(6, metadata.getLastModifiedDate() != null ? java.sql.Date.valueOf(metadata.getLastModifiedDate()) : null);
            
            pstmt.executeUpdate();
            System.out.println("Project metadata saved/updated successfully.");
        } catch (SQLException e) {
            System.err.println("프로젝트 메타데이터 저장 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public ProjectMetadata loadProjectMetadata() {
        String sql = "SELECT project_name, project_start_date, project_end_date, author, last_modified_date FROM project_metadata WHERE project_id = ?";
        ProjectMetadata metadata = new ProjectMetadata(); // Default if not found

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, "default_project");
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                metadata.setProjectName(rs.getString("project_name"));
                java.sql.Date startDate = rs.getDate("project_start_date");
                if (startDate != null) metadata.setProjectStartDate(startDate.toLocalDate());
                java.sql.Date endDate = rs.getDate("project_end_date");
                if (endDate != null) metadata.setProjectEndDate(endDate.toLocalDate());
                metadata.setAuthor(rs.getString("author"));
                java.sql.Date lastModDate = rs.getDate("last_modified_date");
                if (lastModDate != null) metadata.setLastModifiedDate(lastModDate.toLocalDate());
            } else {
                 System.out.println("No project metadata found for default_project, using defaults.");
            }
        } catch (SQLException e) {
            System.err.println("프로젝트 메타데이터 로드 오류: " + e.getMessage());
            e.printStackTrace();
        }
        return metadata;
    }


    // --- Task CRUD ---

    /**
     * 단일 작업을 데이터베이스에 추가하거나 업데이트합니다.
     * @param task 저장할 Task 객체
     */
    public void saveTask(Task task, String parentId) {
        // 1. tasks 테이블에 작업 정보 저장 (INSERT ... ON DUPLICATE KEY UPDATE)
        String taskSql = "INSERT INTO tasks (id, parent_id, name, assignee, start_date, end_date, progress, is_category, is_locked) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), name = VALUES(name), assignee = VALUES(assignee), " +
                         "start_date = VALUES(start_date), end_date = VALUES(end_date), progress = VALUES(progress), " +
                         "is_category = VALUES(is_category), is_locked = VALUES(is_locked)";

        try (Connection conn = getConnection();
             PreparedStatement pstmtTask = conn.prepareStatement(taskSql)) {

            conn.setAutoCommit(false); // 트랜잭션 시작

            pstmtTask.setString(1, task.getId());
            pstmtTask.setString(2, parentId); // 부모 ID 설정
            pstmtTask.setString(3, task.getName());
            pstmtTask.setString(4, task.getAssignee());
            pstmtTask.setDate(5, task.getStartDate() != null ? java.sql.Date.valueOf(task.getStartDate()) : null);
            pstmtTask.setDate(6, task.getEndDate() != null ? java.sql.Date.valueOf(task.getEndDate()) : null);
            pstmtTask.setInt(7, task.getProgress());
            pstmtTask.setBoolean(8, task.isCategory());
            pstmtTask.setBoolean(9, task.isLocked());
            pstmtTask.executeUpdate();

            // 2. task_predecessors 테이블 업데이트
            // 먼저 기존 선행 작업 관계 삭제
            String deletePredSql = "DELETE FROM task_predecessors WHERE task_id = ?";
            try (PreparedStatement pstmtDeletePred = conn.prepareStatement(deletePredSql)) {
                pstmtDeletePred.setString(1, task.getId());
                pstmtDeletePred.executeUpdate();
            }

            // 새로운 선행 작업 관계 추가
            if (task.getPredecessorIds() != null && !task.getPredecessorIds().isEmpty()) {
                String insertPredSql = "INSERT INTO task_predecessors (task_id, predecessor_id) VALUES (?, ?)";
                try (PreparedStatement pstmtInsertPred = conn.prepareStatement(insertPredSql)) {
                    for (String predId : task.getPredecessorIds()) {
                        pstmtInsertPred.setString(1, task.getId());
                        pstmtInsertPred.setString(2, predId);
                        pstmtInsertPred.addBatch();
                    }
                    pstmtInsertPred.executeBatch();
                }
            }
            
            // 하위 작업들도 재귀적으로 저장
            if (task.getChildren() != null) {
                for (Task child : task.getChildren()) {
                    saveTaskRecursive(child, task.getId(), conn); // 재귀 호출 시 Connection 전달
                }
            }

            conn.commit(); // 트랜잭션 커밋
            System.out.println("Task " + task.getName() + " and its predecessors/children saved successfully.");

        } catch (SQLException e) {
            System.err.println("작업 저장 오류 (" + task.getName() + "): " + e.getMessage());
            e.printStackTrace();
            // 트랜잭션 롤백 로직 추가 필요 시 여기에
        }
    }
    
    /**
     * 재귀적으로 하위 작업을 저장하는 헬퍼 메서드.
     */
    private void saveTaskRecursive(Task task, String parentId, Connection conn) throws SQLException {
        String taskSql = "INSERT INTO tasks (id, parent_id, name, assignee, start_date, end_date, progress, is_category, is_locked) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE parent_id = VALUES(parent_id), name = VALUES(name), assignee = VALUES(assignee), " +
                         "start_date = VALUES(start_date), end_date = VALUES(end_date), progress = VALUES(progress), " +
                         "is_category = VALUES(is_category), is_locked = VALUES(is_locked)";
        
        try (PreparedStatement pstmtTask = conn.prepareStatement(taskSql)) {
            pstmtTask.setString(1, task.getId());
            pstmtTask.setString(2, parentId);
            pstmtTask.setString(3, task.getName());
            pstmtTask.setString(4, task.getAssignee());
            pstmtTask.setDate(5, task.getStartDate() != null ? java.sql.Date.valueOf(task.getStartDate()) : null);
            pstmtTask.setDate(6, task.getEndDate() != null ? java.sql.Date.valueOf(task.getEndDate()) : null);
            pstmtTask.setInt(7, task.getProgress());
            pstmtTask.setBoolean(8, task.isCategory());
            pstmtTask.setBoolean(9, task.isLocked());
            pstmtTask.executeUpdate();
        }

        // Predecessors
        String deletePredSql = "DELETE FROM task_predecessors WHERE task_id = ?";
        try (PreparedStatement pstmtDeletePred = conn.prepareStatement(deletePredSql)) {
            pstmtDeletePred.setString(1, task.getId());
            pstmtDeletePred.executeUpdate();
        }

        if (task.getPredecessorIds() != null && !task.getPredecessorIds().isEmpty()) {
            String insertPredSql = "INSERT INTO task_predecessors (task_id, predecessor_id) VALUES (?, ?)";
            try (PreparedStatement pstmtInsertPred = conn.prepareStatement(insertPredSql)) {
                for (String predId : task.getPredecessorIds()) {
                    pstmtInsertPred.setString(1, task.getId());
                    pstmtInsertPred.setString(2, predId);
                    pstmtInsertPred.addBatch();
                }
                pstmtInsertPred.executeBatch();
            }
        }
        
        // Children
        if (task.getChildren() != null) {
            for (Task child : task.getChildren()) {
                saveTaskRecursive(child, task.getId(), conn); // Pass connection
            }
        }
    }


    /**
     * 모든 최상위 작업(parent_id가 NULL인 작업)과 그 하위 작업들을 로드합니다.
     * @return 최상위 Task 객체들의 리스트
     */
    public List<Task> loadAllTasks() {
        List<Task> allTasksMap = new ArrayList<>(); // 임시로 모든 작업을 담을 리스트 (ID로 검색하기 위함)
        Map<String, Task> taskMapById = new HashMap<>(); // ID를 키로 Task 객체를 저장하는 맵
        List<Task> rootLevelTasks = new ArrayList<>();

        //String sql = "SELECT id, parent_id, name, assignee, start_date, end_date, progress, is_category, is_locked FROM tasks ORDER BY parent_id NULLS FIRST, name"; // parent_id가 null인 것을 먼저, 그 다음 이름순
        String sql = """
        SELECT id, parent_id, name, assignee, start_date, end_date, progress, is_category, is_locked
        FROM tasks
        ORDER BY ISNULL(parent_id) DESC, parent_id ASC, name ASC;
        """;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String id = rs.getString("id");
                String parentId = rs.getString("parent_id");
                String name = rs.getString("name");
                String assignee = rs.getString("assignee");
                LocalDate startDate = rs.getDate("start_date") != null ? rs.getDate("start_date").toLocalDate() : null;
                LocalDate endDate = rs.getDate("end_date") != null ? rs.getDate("end_date").toLocalDate() : null;
                int progress = rs.getInt("progress");
                boolean isCategory = rs.getBoolean("is_category");
                boolean isLocked = rs.getBoolean("is_locked");

                Task task;
                if (isCategory) {
                    task = new Task(name, true);
                } else {
                    task = new Task(name, assignee, startDate, endDate, progress);
                }
                task.setIdDataForImport(id); // DB에서 읽어온 ID 설정
                task.setLocked(isLocked);
                task.setParentId(parentId); // CSV 로직과 유사하게 parentId 임시 저장

                // 선행 작업 ID 로드
                List<String> predIds = loadPredecessorIdsForTask(id, conn);
                task.setPredecessorIds(predIds);
                
                taskMapById.put(id, task);
            }
            
            // 계층 구조 재구성
            for (Task task : taskMapById.values()) {
                String parentId = task.getParentId(); // 임시 저장된 parentId 사용
                if (parentId != null && taskMapById.containsKey(parentId)) {
                    taskMapById.get(parentId).addChild(task);
                } else {
                    rootLevelTasks.add(task); // 부모가 없거나, 부모 ID가 맵에 없으면 최상위로 간주
                }
            }

        } catch (SQLException e) {
            System.err.println("모든 작업 로드 오류: " + e.getMessage());
            e.printStackTrace();
        }
        return rootLevelTasks;
    }

    private List<String> loadPredecessorIdsForTask(String taskId, Connection conn) throws SQLException {
        List<String> predIds = new ArrayList<>();
        String sql = "SELECT predecessor_id FROM task_predecessors WHERE task_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, taskId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                predIds.add(rs.getString("predecessor_id"));
            }
        }
        return predIds;
    }

    /**
     * 특정 ID를 가진 작업을 삭제합니다.
     * tasks 테이블에서 ON DELETE CASCADE 옵션으로 인해 하위 작업 및 task_predecessors의 관련 레코드도 삭제될 수 있습니다.
     * @param taskId 삭제할 작업의 ID
     */
    public void deleteTask(String taskId) {
        String sql = "DELETE FROM tasks WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, taskId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("Task " + taskId + " and related data deleted successfully.");
            } else {
                System.out.println("Task " + taskId + " not found for deletion.");
            }
        } catch (SQLException e) {
            System.err.println("작업 삭제 오류 (" + taskId + "): " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 모든 작업을 삭제합니다 (주의해서 사용).
     * 메타데이터는 삭제하지 않습니다.
     */
    public void deleteAllTasks() {
        String sqlPredecessors = "DELETE FROM task_predecessors";
        String sqlTasks = "DELETE FROM tasks";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            conn.setAutoCommit(false);
            // 선행 작업 관계 먼저 삭제 (참조 무결성 위반 방지, tasks 테이블에 ON DELETE CASCADE가 있다면 불필요할 수 있음)
            stmt.executeUpdate(sqlPredecessors);
            // 작업 삭제
            stmt.executeUpdate(sqlTasks);
            conn.commit();
            System.out.println("All tasks and predecessor links deleted successfully.");
        } catch (SQLException e) {
            System.err.println("모든 작업 삭제 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 애플리케이션 시작 시 테이블 생성 (선택 사항)
    public boolean createTablesIfNotExist() {
        String[] ddlStatements = {
            "CREATE TABLE IF NOT EXISTS project_metadata (" +
            "    project_id VARCHAR(255) NOT NULL PRIMARY KEY DEFAULT 'default_project'," +
            "    project_name VARCHAR(255)," +
            "    project_start_date DATE," +
            "    project_end_date DATE," +
            "    author VARCHAR(255)," +
            "    last_modified_date DATE" +
            ")",
            "CREATE TABLE IF NOT EXISTS tasks (" +
            "    id VARCHAR(255) NOT NULL PRIMARY KEY," +
            "    parent_id VARCHAR(255)," +
            "    name VARCHAR(255) NOT NULL," +
            "    assignee VARCHAR(255)," +
            "    start_date DATE," +
            "    end_date DATE," +
            "    progress INT DEFAULT 0," +
            "    is_category BOOLEAN DEFAULT FALSE," +
            "    is_locked BOOLEAN DEFAULT FALSE," +
            "    FOREIGN KEY (parent_id) REFERENCES tasks(id) ON DELETE CASCADE" +
            ")",
            "CREATE TABLE IF NOT EXISTS task_predecessors (" +
            "    task_id VARCHAR(255) NOT NULL," +
            "    predecessor_id VARCHAR(255) NOT NULL," +
            "    PRIMARY KEY (task_id, predecessor_id)," +
            "    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE," +
            "    FOREIGN KEY (predecessor_id) REFERENCES tasks(id) ON DELETE CASCADE" +
            ")",
            "CREATE INDEX IF NOT EXISTS idx_tasks_parent_id ON tasks(parent_id)",
            "CREATE INDEX IF NOT EXISTS idx_task_predecessors_task_id ON task_predecessors(task_id)",
            "CREATE INDEX IF NOT EXISTS idx_task_predecessors_predecessor_id ON task_predecessors(predecessor_id)",
            "INSERT IGNORE INTO project_metadata (project_id, project_name, project_start_date, project_end_date, author, last_modified_date) " +
            "VALUES ('default_project', '새 프로젝트', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 2 MONTH), '사용자', CURDATE())"
        };

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : ddlStatements) {
                stmt.executeUpdate(sql);
            }
            System.out.println("데이터베이스 테이블이 준비되었습니다.");
            return true;
        } catch (SQLException e) {
            System.err.println("테이블 생성 오류: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
}
