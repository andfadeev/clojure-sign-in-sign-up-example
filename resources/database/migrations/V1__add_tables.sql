-- Table to store user accounts
create table accounts
(
    email text primary key,
    password text not null,
    created_at timestamp not null default current_timestamp
);

-- Table required for jdbc-session-store
CREATE TABLE session_store
(
    session_id VARCHAR(36) NOT NULL PRIMARY KEY,
    idle_timeout BIGINT,
    absolute_timeout BIGINT,
    value BYTEA
);

