ALTER TABLE profile_evidence
    DROP CONSTRAINT profile_evidence_paragraph_id_fkey,
    DROP CONSTRAINT profile_evidence_image_id_fkey;

ALTER TABLE profile_evidence
    ADD CONSTRAINT profile_evidence_paragraph_id_fkey
        FOREIGN KEY (paragraph_id) REFERENCES book_paragraph(id) ON DELETE CASCADE,
    ADD CONSTRAINT profile_evidence_image_id_fkey
        FOREIGN KEY (image_id) REFERENCES book_image(id) ON DELETE CASCADE;
