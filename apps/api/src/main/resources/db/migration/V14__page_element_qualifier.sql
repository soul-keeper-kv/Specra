-- The second half of a `role` locator.
--
-- A role is only unique together with its accessible name — "the button called Sign in" — and the
-- two are separate arguments in the generated call. V5 gave page_elements one `value` column,
-- which was enough while nothing wrote to the table; inspection writes to it now, and joining the
-- pair into one string would mean guessing where to split it back apart.
ALTER TABLE page_elements
    ADD COLUMN qualifier varchar(500);

COMMENT ON COLUMN page_elements.qualifier IS
    'The accessible name a role strategy needs; null for every other strategy.';
