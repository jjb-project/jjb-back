-- V1 may already be applied in local/prod databases. The MVP no longer uses
-- phone verification, so remove the legacy columns with an additive migration.
alter table members
	drop column if exists phone_number;

alter table members
	drop column if exists phone_verified;
