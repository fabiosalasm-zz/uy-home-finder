create table house_info
(
    id             bigint constraint house_info_pk primary key,
    title          varchar not null,
    address        varchar not null,
    telephone      varchar not null,
    price          bigint  not null,
    currency       char(1) not null,
    "pictureLinks" varchar[],
    location       point,
    "videoLink"    varchar,
    department     varchar not null,
    column_11      int,
    neighbourhood  varchar,
    description    text,
    features       varchar[],
    warranties     varchar[]
);

comment on column house_info.price is 'store money in smallest unit';
