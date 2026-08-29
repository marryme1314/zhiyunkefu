CREATE TABLE IF NOT EXISTS users (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    email         VARCHAR(128) NULL,
    phone         VARCHAR(32)  NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME     NOT NULL,
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_sessions (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(200) NOT NULL DEFAULT '新会话',
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    KEY idx_sessions_user (user_id),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS messages (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id  BIGINT       NOT NULL,
    role        VARCHAR(16)  NOT NULL,
    content     MEDIUMTEXT   NOT NULL,
    sources_json TEXT        NULL,
    created_at  DATETIME     NOT NULL,
    KEY idx_messages_session (session_id),
    CONSTRAINT fk_messages_session FOREIGN KEY (session_id) REFERENCES chat_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS feedbacks (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(16)  NOT NULL,
    comment    VARCHAR(500) NULL,
    created_at DATETIME     NOT NULL,
    UNIQUE KEY uk_feedback_msg_user (message_id, user_id),
    CONSTRAINT fk_feedback_message FOREIGN KEY (message_id) REFERENCES messages (id),
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_documents (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    filename      VARCHAR(255) NOT NULL,
    content_type  VARCHAR(32)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    error_message VARCHAR(500) NULL,
    created_at    DATETIME     NOT NULL,
    KEY idx_kb_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id   BIGINT       NOT NULL,
    chunk_index   INT          NOT NULL,
    content       MEDIUMTEXT   NOT NULL,
    embedding_json LONGTEXT    NOT NULL,
    created_at    DATETIME     NOT NULL,
    KEY idx_chunks_doc (document_id),
    CONSTRAINT fk_chunks_doc FOREIGN KEY (document_id) REFERENCES knowledge_documents (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
