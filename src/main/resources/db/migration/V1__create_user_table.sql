CREATE TABLE tb_user (
    id BINARY(16) NOT NULL,
    username VARCHAR(255),
    email VARCHAR(255),
    password VARCHAR(255),
    creation_timestamp TIMESTAMP(6),
    update_timestamp TIMESTAMP(6),
    CONSTRAINT pk_tb_user PRIMARY KEY (id)
);
