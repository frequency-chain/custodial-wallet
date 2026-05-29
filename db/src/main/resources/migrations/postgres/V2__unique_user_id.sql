TRUNCATE TABLE user_key_data;

ALTER TABLE user_key_data ADD CONSTRAINT user_key_data_unique_user_id UNIQUE(user_id);
