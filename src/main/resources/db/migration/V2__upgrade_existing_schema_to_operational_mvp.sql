alter table recruitments
	add column if not exists work_date date;

alter table recruitments
	add column if not exists start_time time;

alter table recruitments
	add column if not exists end_time time;

alter table recruitments
	add column if not exists status varchar(32);

alter table recruitments
	add column if not exists created_at timestamp with time zone;

update recruitments
set work_date = current_date
where work_date is null;

update recruitments
set start_time = time '09:00:00'
where start_time is null;

update recruitments
set end_time = time '18:00:00'
where end_time is null;

update recruitments
set status = 'OPEN'
where status is null;

update recruitments
set created_at = now()
where created_at is null;

alter table recruitments
	alter column work_date set not null;

alter table recruitments
	alter column start_time set not null;

alter table recruitments
	alter column end_time set not null;

alter table recruitments
	alter column status set not null;

alter table recruitments
	alter column created_at set not null;

create index if not exists idx_recruitments_owner_created_at
	on recruitments (owner_id, created_at);

alter table match_requests
	add column if not exists created_at timestamp with time zone;

alter table match_requests
	add column if not exists responded_at timestamp with time zone;

update match_requests
set created_at = now()
where created_at is null;

alter table match_requests
	alter column created_at set not null;

create index if not exists idx_match_requests_owner_status
	on match_requests (owner_id, status);

create index if not exists idx_match_requests_job_seeker_status
	on match_requests (job_seeker_id, status);

create index if not exists idx_match_requests_recruitment
	on match_requests (recruitment_id);

create table if not exists reviews (
	id uuid primary key,
	match_request_id uuid not null,
	evaluator_id uuid not null,
	target_id uuid not null,
	rating integer not null,
	review varchar(500) not null,
	created_at timestamp with time zone not null,
	constraint uk_reviews_match_evaluator unique (match_request_id, evaluator_id)
);

create index if not exists idx_reviews_target on reviews (target_id);
