ALTER TABLE messages
    ADD COLUMN intent VARCHAR(32) NULL AFTER role;

CREATE INDEX idx_messages_intent ON messages (intent);
CREATE INDEX idx_messages_created ON messages (created_at);
