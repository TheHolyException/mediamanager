CREATE OR REPLACE PROCEDURE `mediamanager`.`addEpisode`(
	IN _nKey int,
	IN _nSeasonLink int,
	IN _nEpisodeNumber int,
	IN _szTitle varchar(200),
	IN _szAniworldURL varchar(200),
	IN _szVideoURL varchar(200),
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
			bLoaded,
			szAniworldURL,
			szVideoURL
		) values (
			_nKey,
			_nSeasonLink,
			_nEpisodeNumber,
			_szTitle,
			case when bLoaded = '1' then 1 else 0 end,
			_szAniworldURL,
			szVideoURL
		);
	else
	    select 'update';
		update episode
		set nSeasonLink = _nSeasonLink,
			nEpisodeNumber = _nEpisodeNumber,
			szTitle = _szTitle,
			bLoaded = case when _bLoaded = '1' then 1 else 0 end,
			szAniworldURL = _szAniworldURL,
			szVideoURL = _szVideoURL
		where nKey = _nKey;
	end if;
END
