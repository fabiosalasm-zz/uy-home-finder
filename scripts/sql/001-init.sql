CREATE SCHEMA IF NOT EXISTS public;
CREATE TABLE web_config
(
    alias varchar not null
        constraint web_config_pk
            primary key,
    url_template varchar not null,
    url_template_params jsonb
);

ALTER TABLE web_config OWNER TO postgres;

CREATE UNIQUE INDEX web_config_alias_uindex
    ON web_config (alias);

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

INSERT INTO public.web_config (alias, url_template, url_template_params)
VALUES ('gallito',
        'https://www.gallito.com.uy/inmuebles/casas/alquiler/pre-0-{maxPrice}-dolares/sup-{minSquareMeter}-500-metros!cant={pageSize}',
        '{"maxPrice": 1000, "pageSize": 80, "minSquareMeter": 75}');
INSERT INTO public.web_config (alias, url_template, url_template_params)
VALUES ('infocasas',
        'https://www.infocasas.com.uy/alquiler/casas/{department}/hasta-{maxPrice}/dolares/m2-desde-{minSquareMeter}/edificados',
        '{"maxPrice": 1000, "department": "montevideo", "minSquareMeter": 70}');