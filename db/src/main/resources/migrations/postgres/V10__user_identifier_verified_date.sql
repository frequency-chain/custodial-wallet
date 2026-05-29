ALTER TABLE user_identifier
ADD COLUMN verified_date BIGINT DEFAULT NULL; -- time in ms from epoch
