create table if not exists episode (    nKey           integer primary key,
                                        nSeasonLink    integer,
                                        nEpisodeNumber integer,
                                        szTitle        varchar(255),
                                        szURL          varchar(255),
                                        bLoaded        int
);