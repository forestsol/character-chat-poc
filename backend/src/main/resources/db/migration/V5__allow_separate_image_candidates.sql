ALTER TABLE entity_candidate DROP CONSTRAINT uk_entity_candidate_name;

CREATE UNIQUE INDEX uk_text_entity_candidate_name
    ON entity_candidate(book_id, entity_type, canonical_name)
    WHERE origin_source = 'TEXT';
