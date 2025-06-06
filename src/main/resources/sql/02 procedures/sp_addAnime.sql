CREATE OR REPLACE PROCEDURE `mediamanager`.`addAnime`(
	IN _nKey int,
	IN _nLanguageId int,
	IN _szTitle varchar(200),
	IN _szURL varchar(200),
	IN _szCustomDirectory varchar(200),
	IN _szExcludedSeasons varchar(200),
	IN _bPaused boolean
)
BEGIN
	if not exists (select 1 from anime where nKey = _nKey) then 
		insert into anime (
			nKey,
			nLanguageId,
			szTitle,
			szURL,
			szCustomDirectory,
			szExcludedSeasons,
			bPaused
		) values (
			_nKey,
			_nLanguageId,
			_szTitle,
			_szURL,
			_szCustomDirectory,
			_szExcludedSeasons,
			_bPaused
		);
	else
		update anime
		set nLanguageId = _nLanguageId,
			szTitle = _szTitle,
			szURL = _szURL,
			szCustomDirectory = _szCustomDirectory,
			szExcludedSeasons = _szExcludedSeasons,
			bPaused = _bPaused
		where nKey = _nKey;
	end if;
END
