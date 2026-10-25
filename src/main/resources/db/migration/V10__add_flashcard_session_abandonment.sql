ALTER TABLE flashcard_sessions
    ADD COLUMN abandoned_at DATETIME(6) NULL,
    DROP CHECK ck_flashcard_session_status,
    ADD CONSTRAINT ck_flashcard_session_status CHECK (
        (status = 'IN_PROGRESS' AND completed_count < item_count AND completed_at IS NULL AND abandoned_at IS NULL) OR
        (status = 'COMPLETED' AND completed_count = item_count AND completed_at IS NOT NULL AND abandoned_at IS NULL) OR
        (status = 'ABANDONED' AND completed_count < item_count AND completed_at IS NULL AND abandoned_at IS NOT NULL)
    );