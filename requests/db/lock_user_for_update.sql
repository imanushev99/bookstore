-- execute the script (without END) => the user row in DB gets locked
-- run HTTP request 3. Update user profile from user.http (after login)
-- response should be 409: This account is currently being modified. Please try again.
-- run ROLLBACK after finish
BEGIN;

SELECT *
FROM users
WHERE email = 'john@example.com'
    FOR UPDATE;

END;

ROLLBACK;