-- One published release per collection. Both indexes are searched with this release only.
CREATE TABLE corpus_releases (
    collection   VARCHAR(100) NOT NULL PRIMARY KEY,
    release_id   VARCHAR(64)  NOT NULL,
    chunk_count  INTEGER      NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL
);
