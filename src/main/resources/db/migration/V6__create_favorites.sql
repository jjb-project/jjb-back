create table if not exists favorites (
	id uuid primary key,
	member_id uuid not null,
	target_id uuid not null,
	target_type varchar(32) not null,
	created_at timestamp with time zone not null,
	constraint uk_favorites_member_target unique (member_id, target_id, target_type),
	constraint favorites_target_type_check check (target_type in ('OWNER', 'JOB_SEEKER'))
);

create index if not exists idx_favorites_member_type
	on favorites (member_id, target_type);
