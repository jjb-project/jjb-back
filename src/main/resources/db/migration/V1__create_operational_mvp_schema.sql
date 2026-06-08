create table members (
	id uuid primary key,
	social_provider varchar(40) not null,
	social_subject varchar(160) not null,
	display_name varchar(80) not null,
	password_hash varchar(100),
	phone_number varchar(32),
	phone_verified boolean not null,
	business_verified boolean not null,
	business_operating_status varchar(32),
	roles varchar(80) not null,
	active_role varchar(32),
	available_time varchar(120),
	preferred_area varchar(120),
	desired_hourly_wage integer,
	experienced_industries varchar(300),
	urgent_substitute_available boolean not null,
	job_seeker_introduction varchar(300),
	store_name varchar(120),
	store_address varchar(240),
	business_category varchar(80),
	store_introduction varchar(300),
	constraint uk_members_social_identity unique (social_provider, social_subject)
);

create table recruitments (
	id uuid primary key,
	owner_id uuid not null,
	title varchar(120) not null,
	work_date date not null,
	start_time time not null,
	end_time time not null,
	workplace_address varchar(240) not null,
	hourly_wage integer not null,
	status varchar(32) not null,
	created_at timestamp with time zone not null
);

create index idx_recruitments_owner_created_at on recruitments (owner_id, created_at);

create table match_requests (
	id uuid primary key,
	owner_id uuid not null,
	job_seeker_id uuid not null,
	recruitment_id uuid,
	message varchar(300) not null,
	status varchar(32) not null,
	created_at timestamp with time zone not null,
	responded_at timestamp with time zone
);

create index idx_match_requests_owner_status on match_requests (owner_id, status);
create index idx_match_requests_job_seeker_status on match_requests (job_seeker_id, status);
create index idx_match_requests_recruitment on match_requests (recruitment_id);

create table reviews (
	id uuid primary key,
	match_request_id uuid not null,
	evaluator_id uuid not null,
	target_id uuid not null,
	rating integer not null,
	review varchar(500) not null,
	created_at timestamp with time zone not null,
	constraint uk_reviews_match_evaluator unique (match_request_id, evaluator_id)
);

create index idx_reviews_target on reviews (target_id);
