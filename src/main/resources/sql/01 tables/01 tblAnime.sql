create table if not exists anime (
    nKey                integer primary key,
    nLanguageId         integer,
    szTitle             varchar(255),
    szURL               varchar(255),
    szCustomDirectory   varchar(255),
    szExcludedSeasons   varchar(255)
);

