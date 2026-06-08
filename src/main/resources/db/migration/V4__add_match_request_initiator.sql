alter table match_requests
	add column if not exists requested_by varchar(32);

update match_requests
set requested_by = 'OWNER'
where requested_by is null;

alter table match_requests
	alter column requested_by set not null;
