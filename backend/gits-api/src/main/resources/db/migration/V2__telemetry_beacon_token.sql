-- One-time token that authorizes a navigator.sendBeacon telemetry batch instead of the CSRF header (P08).
-- Only its SHA-256 is stored; the token itself is given to the candidate's page.
ALTER TABLE session_task ADD COLUMN beacon_token_hash varchar(64);
