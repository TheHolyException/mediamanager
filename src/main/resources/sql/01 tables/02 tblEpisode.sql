create table if not exists episode (
    nKey           integer primary key,
    nSeasonLink    integer,
    nEpisodeNumber integer,
    szTitle        varchar(255),
    szAniworldURL  varchar(255),
    szVideoURL     varchar(255),
    bLoaded        int,
    szLanguageIDs  varchar(255),
    INDEX `FK_episode_season` (`nSeasonLink`) USING BTREE,
    CONSTRAINT `FK_episode_season` FOREIGN KEY (`nSeasonLink`) REFERENCES `season` (`nKey`) ON UPDATE NO ACTION ON DELETE CASCADE
);