CREATE SCHEMA IF NOT EXISTS public;

CREATE TYPE store_mode AS ENUM ('MANUAL', 'AUTOMATIC');

CREATE TABLE house_candidate
(
    source_id varchar not null primary key,
    title varchar not null,
    link varchar not null,
    address varchar not null,
    telephone varchar not null,
    price varchar not null,
    department varchar not null,
    neighbourhood varchar not null,
    description varchar not null,
    features jsonb,
    "warranties" varchar[],
    "pictureLinks" varchar[] not null,
    "geoReference" varchar[],
    "videoLink" varchar,
    "store_mode" store_mode not null default 'AUTOMATIC'::store_mode
);

ALTER TABLE house_candidate OWNER TO postgres;

CREATE UNIQUE INDEX house_candidate_source_id_uindex
    on house_candidate (source_id);