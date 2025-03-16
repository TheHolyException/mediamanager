create table if not exists episode (    nKey           integer primary key,
                                        nSeasonLink    integer,
                                        nEpisodeNumber integer,
                                        szTitle        varchar(255),
                                        szURL          varchar(255),
                                        bLoaded        int,
                                        INDEX `FK_episode_season` (`nSeasonLink`) USING BTREE,
                                        CONSTRAINT `FK_episode_season` FOREIGN KEY (`nSeasonLink`) REFERENCES `season` (`nKey`) ON UPDATE NO ACTION ON DELETE CASCADE
);