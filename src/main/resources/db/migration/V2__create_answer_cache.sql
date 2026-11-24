-- First-question answers, reused for the same question asked the same way (see AnswerCache).
CREATE TABLE answer_cache (
    cache_key   VARCHAR(64)              NOT NULL PRIMARY KEY,
    response    CLOB                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    hits        INTEGER                  DEFAULT 0 NOT NULL
);
