SELECT * FROM pg_extension WHERE extname = 'vector';
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS travel_vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(1536)
    );