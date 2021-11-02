CREATE TABLE messages_processed (
    id VARCHAR(32) PRIMARY KEY NOT NULL,
    user VARCHAR(32) NOT NULL
);

CREATE TABLE messages (
    id VARCHAR(32) PRIMARY KEY NOT NULL,
    sender TEXT,
    receiver TEXT,
    subject TEXT,
    cc TEXT,
    bcc TEXT,
    body TEXT
);

CREATE TABLE users (
    id VARCHAR(32) PRIMARY KEY NOT NULL
)