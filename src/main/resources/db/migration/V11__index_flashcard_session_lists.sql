-- Owner-only and lesson-filtered lists order by creation time without a status filter.
CREATE INDEX idx_flashcard_session_created ON flashcard_sessions (user_subject, created_at, id);
CREATE INDEX idx_flashcard_session_lesson_created ON flashcard_sessions (user_subject, lesson_id, created_at, id);