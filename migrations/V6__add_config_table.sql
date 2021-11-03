CREATE TABLE configs (
    name VARCHAR(32) PRIMARY KEY NOT NULL,
    value VARCHAR(64) NOT NULL
);

-- Some defaults so the user has some idea what goes where
INSERT INTO configs (name, value) VALUES ('frontend_basepath', 'https://google.com');