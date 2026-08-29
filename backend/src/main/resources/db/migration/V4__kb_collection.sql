ALTER TABLE knowledge_documents
    ADD COLUMN kb_collection VARCHAR(32) NOT NULL DEFAULT 'GENERAL' AFTER content_type;

UPDATE knowledge_documents
SET kb_collection = CASE
    WHEN filename LIKE '%产品%' THEN 'PRODUCT'
    WHEN filename LIKE '%退换%' OR filename LIKE '%售后%' OR filename LIKE '%政策%' THEN 'AFTER_SALES'
    WHEN filename LIKE '%FAQ%' OR filename LIKE '%常见问题%' THEN 'FAQ'
    ELSE 'GENERAL'
END;
