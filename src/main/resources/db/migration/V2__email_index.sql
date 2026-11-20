CREATE INDEX IF NOT EXISTS app_users_lower_email_idx ON app_users (lower(email));
