CREATE OR REPLACE PROCEDURE `mediamanager`.`addEpisode`(
	IN _nKey int,
	IN _nSeasonLink int,
	IN _nEpisodeNumber int,
	IN _szTitle varchar(200),
	IN _szURL varchar(200),
	IN _bLoaded varchar(200)
)
BEGIN
	if not exists (select 1 from episode where nKey = _nKey) then
		select 'insert';
		insert into episode (
			nKey,
			nSeasonLink,
			nEpisodeNumber,
			szTitle,
			szURL,
			bLoaded
		) values (
			_nKey,
			_nSeasonLink,
			_nEpisodeNumber,
			_szTitle,
			_szURL,
			case when bLoaded = '1' then 1 else 0 end
		);
	else
	    select 'update';
		update episode
		set nSeasonLink = _nSeasonLink,
			nEpisodeNumber = _nEpisodeNumber,
			szTitle = _szTitle,
			szURL = _szURL,
			bLoaded = case when _bLoaded = '1' then 1 else 0 end
		where nKey = _nKey;
	end if;
END
