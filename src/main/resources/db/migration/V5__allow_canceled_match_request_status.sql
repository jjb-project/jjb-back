alter table match_requests
	drop constraint if exists match_requests_status_check;

alter table match_requests
	add constraint match_requests_status_check
	check (status in ('REQUESTED', 'ACCEPTED', 'DECLINED', 'CANCELED'));
