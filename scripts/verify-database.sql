SELECT current_database() AS database_name, current_user AS database_user;

SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT COUNT(*) AS player_count FROM app_user;
SELECT COUNT(*) AS farm_save_count FROM farm_save;

SELECT u.email, f.revision, f.schema_version, f.generator_version, f.saved_at
FROM app_user u
LEFT JOIN farm_save f ON f.user_id = u.id
ORDER BY u.created_at DESC;
