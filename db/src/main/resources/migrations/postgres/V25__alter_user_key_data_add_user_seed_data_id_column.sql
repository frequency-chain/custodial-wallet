ALTER TABLE user_key_data
    ADD COLUMN user_seed_data_id BIGINT REFERENCES user_seed_data;
