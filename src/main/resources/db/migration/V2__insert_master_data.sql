-- ============================================================
-- JLPT LEVELS
-- ============================================================

INSERT INTO jlpt_levels (
    code,
    name,
    display_order
)
VALUES
    ('N5', 'JLPT N5', 1),
    ('N4', 'JLPT N4', 2),
    ('N3', 'JLPT N3', 3),
    ('N2', 'JLPT N2', 4),
    ('N1', 'JLPT N1', 5);


-- ============================================================
-- PARTS OF SPEECH
-- ============================================================

INSERT INTO parts_of_speech (
    code,
    name_vi,
    name_en
)
VALUES
    ('NOUN', 'Danh từ', 'Noun'),
    ('VERB', 'Động từ', 'Verb'),
    ('I_ADJECTIVE', 'Tính từ đuôi い', 'い-Adjective'),
    ('NA_ADJECTIVE', 'Tính từ đuôi な', 'な-Adjective'),
    ('ADVERB', 'Trạng từ', 'Adverb'),
    ('PRONOUN', 'Đại từ', 'Pronoun'),
    ('PARTICLE', 'Trợ từ', 'Particle'),
    ('AUXILIARY_VERB', 'Trợ động từ', 'Auxiliary Verb'),
    ('CONJUNCTION', 'Liên từ', 'Conjunction'),
    ('INTERJECTION', 'Thán từ', 'Interjection'),
    ('COUNTER', 'Trợ số từ', 'Counter');