-- 프로젝트 메타데이터를 저장하는 테이블
CREATE TABLE IF NOT EXISTS project_metadata (
    project_id VARCHAR(255) NOT NULL PRIMARY KEY DEFAULT 'default_project', -- 기본값으로 단일 프로젝트 지원 또는 UUID 사용 가능
    project_name VARCHAR(255),
    project_start_date DATE,
    project_end_date DATE,
    author VARCHAR(255),
    last_modified_date DATE
);

-- 작업(Task) 정보를 저장하는 기본 테이블
CREATE TABLE IF NOT EXISTS tasks (
    id VARCHAR(255) NOT NULL PRIMARY KEY,       -- 작업의 고유 ID (예: UUID 또는 Task 클래스에서 생성된 ID)
    parent_id VARCHAR(255),                     -- 부모 작업의 ID (최상위 작업의 경우 NULL)
    name VARCHAR(255) NOT NULL,                 -- 작업명
    assignee VARCHAR(255),                      -- 담당자
    start_date DATE,                            -- 시작일
    end_date DATE,                              -- 종료일
    progress INT DEFAULT 0,                     -- 진행률 (0-100)
    is_category BOOLEAN DEFAULT FALSE,          -- 카테고리 여부
    is_locked BOOLEAN DEFAULT FALSE,            -- 잠금 여부
    FOREIGN KEY (parent_id) REFERENCES tasks(id) ON DELETE CASCADE -- 부모 작업 삭제 시 하위 작업도 삭제 (옵션)
);wbs

-- 작업 간의 선행 관계를 저장하는 테이블 (다대다 관계)
CREATE TABLE IF NOT EXISTS task_predecessors (
    task_id VARCHAR(255) NOT NULL,              -- 현재 작업의 ID
    predecessor_id VARCHAR(255) NOT NULL,       -- 선행 작업의 ID
    PRIMARY KEY (task_id, predecessor_id),
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (predecessor_id) REFERENCES tasks(id) ON DELETE CASCADE
);

-- 인덱스 추가 (선택 사항이지만 성능 향상에 도움)
CREATE INDEX IF NOT EXISTS idx_tasks_parent_id ON tasks(parent_id);
CREATE INDEX IF NOT EXISTS idx_task_predecessors_task_id ON task_predecessors(task_id);
CREATE INDEX IF NOT EXISTS idx_task_predecessors_predecessor_id ON task_predecessors(predecessor_id);

-- 초기 프로젝트 메타데이터 삽입 (애플리케이션에서 한 번만 실행하거나, 존재하지 않을 때만 실행)
INSERT IGNORE INTO project_metadata (project_id, project_name, project_start_date, project_end_date, author, last_modified_date)
VALUES ('default_project', '새 프로젝트', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 2 MONTH), '사용자', CURDATE());
