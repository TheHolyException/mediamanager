CREATE OR REPLACE PROCEDURE `mediamanager`.`addSeason`(
	IN _nKey int,
	IN _nSeasonNumber int,
	IN _nAnimeLink int,
	IN _szURL varchar(200)
)
BEGIN
	if not exists (select 1 from season where nKey = _nKey) then
		insert into season (
			nKey,
			nSeasonNumber,
			nAnimeLink,
			szURL
		) values (
			_nKey,
			_nSeasonNumber,
			_nAnimeLink,
			_szURL
		);
	else
		update season
		set nSeasonNumber = _nSeasonNumber,
			nAnimeLink    = _nAnimeLink,
			szURL         = _szURL
		where nKey = _nKey;
	end if;
END
