-- Schema for the subjects and features tables used by the Supabase vector store.

create extension if not exists vector;

-- Enrolled subjects.
create table if not exists subjects (
    subject_id text primary key,
    name text not null,
    created_at bigint not null
);

-- Feature vectors with 1280 dimensions.
create table if not exists features_mediapipe (
    feature_id text primary key,
    subject_id text not null references subjects (subject_id) on delete cascade,
    vector vector(1280) not null
);

create index if not exists idx_features_mediapipe_subject_id on features_mediapipe (subject_id);

-- Feature vectors with 1536 dimensions.
create table if not exists features_gemini (
    feature_id text primary key,
    subject_id text not null references subjects (subject_id) on delete cascade,
    vector vector(1536) not null
);

create index if not exists idx_features_gemini_subject_id on features_gemini (subject_id);

-- Ranks subjects by their single best-matching feature via cosine similarity (0..1).
-- Returns at most match_count rows.
create or replace function match_features_mediapipe(query_vector vector(1280), match_count int)
returns table (subject_id text, name text, similarity float8)
language sql stable
as $$
    select f.subject_id,
           s.name,
           max(1 - (f.vector <=> query_vector)) as similarity
    from features_mediapipe f
    join subjects s on s.subject_id = f.subject_id
    group by f.subject_id, s.name
    order by similarity desc
    limit match_count;
$$;

-- Ranks subjects by their single best-matching feature via cosine similarity (0..1).
-- Returns at most match_count rows.
create or replace function match_features_gemini(query_vector vector(1536), match_count int)
returns table (subject_id text, name text, similarity float8)
language sql stable
as $$
    select f.subject_id,
           s.name,
           max(1 - (f.vector <=> query_vector)) as similarity
    from features_gemini f
    join subjects s on s.subject_id = f.subject_id
    group by f.subject_id, s.name
    order by similarity desc
    limit match_count;
$$;

