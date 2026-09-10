-- What has to happen before a page can be seen at all.
--
-- Inspection opens a URL in a fresh browser and reads what is there. For anything an anonymous
-- visitor cannot reach, what is there is the sign-in screen — so a page object named after the
-- screen behind it filled up with the login form's elements, and nothing downstream could tell.
-- The runner now refuses that reading; this column is how a project stops producing it.
--
-- A test case rather than a login setting, because sign-in is only the commonest case. A screen
-- that lists an order needs an order, and a wizard's fourth step needs the first three. Whatever a
-- person had to do by hand before the screen appeared, they already wrote down as a test case, and
-- it is replayed in the same browser context immediately before the reading.
--
-- On the environment, because the answer differs per deployment: DEV and STAGING have different
-- credentials, and those already live in this environment's variables. ON DELETE SET NULL rather
-- than CASCADE — deleting the test case used as a prelude must not delete the environment with it.
ALTER TABLE environments
    ADD COLUMN prelude_test_case_id uuid REFERENCES test_cases (id) ON DELETE SET NULL;

COMMENT ON COLUMN environments.prelude_test_case_id IS
    'Test case replayed before an inspection of this environment, to reach a page behind it.';
