create table if not exists season ( nKey           integer primary key,
                                    nAnimeLink     integer,
                                    nSeasonNumber  integer,
                                    szURL          varchar(255),
                                    INDEX `FK_season_anime` (`nAnimeLink`) USING BTREE,
                                    CONSTRAINT `FK_season_anime` FOREIGN KEY (`nAnimeLink`) REFERENCES `anime` (`nKey`) ON UPDATE NO ACTION ON DELETE CASCADE
);