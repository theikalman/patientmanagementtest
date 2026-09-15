-- ---------------------------------------------------------------------------
-- Demo data.
--
-- Deliberately kept OUT of db/migration. Flyway is configured with two
-- locations and this one is only added under the 'demo' profile (active by
-- default for local runs), so that a production deployment running
-- `--spring.profiles.active=prod` gets the schema without the fixtures.
-- Automated tests exclude it too, so that assertions never depend on seed rows.
--
-- The rows are realistic on purpose: every phone number is a valid Australian
-- number and every postcode belongs to the state next to it, so the data would
-- survive the same validation the API applies to user input.
-- ---------------------------------------------------------------------------

INSERT INTO patient (pid, first_name, last_name, date_of_birth, gender, phone_no,
                     street, suburb, state, postcode, version, created_at, updated_at) VALUES
    ('PAT-000001', 'Chloe', 'Taylor', DATE '1980-12-01', 'FEMALE', '+61342872001', '79 Elizabeth Street', 'Ballarat', 'VIC', '3350', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000002', 'Willow', 'Wood', DATE '1996-11-24', 'FEMALE', '+61416435842', '153 Queen Street', 'Toowoomba', 'QLD', '4350', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000003', 'Mason', 'Martin', DATE '1958-01-26', 'MALE', '+61425389189', '295 Chapel Street', 'Fremantle', 'WA', '6160', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000004', 'Thomas', 'Patel', DATE '2001-06-24', 'MALE', '+61404601803', '52 Elizabeth Street', 'Adelaide', 'SA', '5000', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000005', 'Hugo', 'Gray', DATE '1967-11-20', 'MALE', '+61394867629', '81 Sturt Highway', 'Launceston', 'TAS', '7250', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000006', 'Harper', 'Williams', DATE '1968-03-14', 'FEMALE', '+61412336458', '256 Murray Street', 'Canberra', 'ACT', '2600', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000007', 'William', 'Watson', DATE '2009-01-25', 'MALE', '+61440631860', '163 Station Street', 'Darwin City', 'NT', '0800', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000008', 'Matilda', 'Clarke', DATE '1998-11-26', 'FEMALE', '+61455138717', '51 Anzac Parade', 'Newcastle', 'NSW', '2300', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000009', 'Nikolai', 'Smith', DATE '1959-05-07', 'MALE', '+61402627633', '39 King William Street', 'Ballarat', 'VIC', '3350', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000010', 'Sofia', 'Nguyen', DATE '1967-11-06', 'FEMALE', '+61438572466', '232 Murray Street', 'Cairns City', 'QLD', '4870', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000011', 'Jack', 'Campbell', DATE '2000-08-12', 'MALE', '+61406164029', '108 Sturt Highway', 'Bunbury', 'WA', '6230', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000012', 'Ingrid', 'Nguyen', DATE '1963-10-24', 'FEMALE', '+61402401468', '302 Collins Street', 'Mount Gambier', 'SA', '5290', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000013', 'Jack', 'Wilson', DATE '2013-07-09', 'MALE', '+61318778119', '292 Oxford Street', 'Hobart', 'TAS', '7000', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000014', 'Wei', 'Young', DATE '2018-06-19', 'MALE', '+61428145480', '242 George Street', 'Belconnen', 'ACT', '2617', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000015', 'Hugo', 'Johnson', DATE '1959-11-07', 'MALE', '+61437276178', '163 Park Avenue', 'Alice Springs', 'NT', '0870', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000016', 'Aarav', 'Anderson', DATE '2016-04-02', 'MALE', '+61400496692', '241 Station Street', 'Newcastle', 'NSW', '2300', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000017', 'Nikolai', 'Thompson', DATE '2008-08-10', 'MALE', '+61329686865', '63 Park Avenue', 'Ballarat', 'VIC', '3350', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000018', 'Liam', 'Jones', DATE '1989-05-14', 'MALE', '+61743773236', '287 Bourke Street', 'Toowoomba', 'QLD', '4350', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000019', 'Priya', 'Brown', DATE '2019-06-12', 'FEMALE', '+61874930414', '23 Flinders Lane', 'Perth', 'WA', '6000', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000020', 'Omar', 'Hunt', DATE '1978-08-21', 'MALE', '+61410663018', '192 Queen Street', 'Adelaide', 'SA', '5000', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000021', 'Amelia', 'Gray', DATE '1947-12-17', 'FEMALE', '+61467874630', '196 Sturt Highway', 'Hobart', 'TAS', '7000', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000022', 'Oliver', 'Chen', DATE '1955-01-21', 'MALE', '+61499613570', '115 Wallaby Way', 'Canberra', 'ACT', '2600', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000023', 'Thomas', 'Kaur', DATE '2009-11-08', 'MALE', '+61476543076', '217 Hay Street', 'Alice Springs', 'NT', '0870', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000024', 'Hugo', 'Johnson', DATE '2013-07-24', 'MALE', '+61485217422', '283 Church Lane', 'Newcastle', 'NSW', '2300', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000025', 'Liam', 'Ali', DATE '2005-11-19', 'MALE', '+61368512738', '105 Elizabeth Street', 'Ballarat', 'VIC', '3350', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000026', 'Harper', 'Barnes', DATE '1961-06-12', 'FEMALE', '+61733544062', '255 Rundle Mall', 'Cairns City', 'QLD', '4870', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000027', 'Mia', 'Singh', DATE '2004-03-02', 'FEMALE', '+61485993537', '277 Elizabeth Street', 'Fremantle', 'WA', '6160', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000028', 'Mei', 'Barnes', DATE '2006-06-08', 'FEMALE', '+61419582648', '251 Rundle Mall', 'Adelaide', 'SA', '5000', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000029', 'William', 'Dixon', DATE '1965-09-28', 'MALE', '+61485061798', '329 Wallaby Way', 'Launceston', 'TAS', '7250', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000030', 'Ingrid', 'Taylor', DATE '2015-08-10', 'FEMALE', '+61410219336', '13 Church Lane', 'Belconnen', 'ACT', '2617', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000031', 'Liam', 'Clarke', DATE '1966-07-13', 'MALE', '+61408601571', '201 Sturt Highway', 'Darwin City', 'NT', '0800', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000032', 'Nikolai', 'Wilson', DATE '2013-02-22', 'MALE', '+61281243402', '58 Rundle Mall', 'Wollongong', 'NSW', '2500', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000033', 'Willow', 'Fraser', DATE '1973-11-09', 'FEMALE', '+61357976064', '18 Church Lane', 'Richmond', 'VIC', '3121', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000034', 'Oliver', 'Hall', DATE '1972-07-23', 'MALE', '+61431453190', '252 King William Street', 'Brisbane City', 'QLD', '4000', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000035', 'Anika', 'Nguyen', DATE '1947-11-03', 'FEMALE', '+61825848170', '238 Elizabeth Street', 'Bunbury', 'WA', '6230', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000036', 'Jordan', 'Dixon', DATE '2013-09-17', 'UNKNOWN', '+61476853092', '313 Brunswick Street', 'Norwood', 'SA', '5067', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000037', 'Ruby', 'Kaur', DATE '1969-12-26', 'FEMALE', '+61441245901', '159 Rundle Mall', 'Launceston', 'TAS', '7250', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000038', 'Jack', 'Chen', DATE '1938-07-09', 'MALE', '+61421233628', '162 Anzac Parade', 'Belconnen', 'ACT', '2617', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000039', 'Hamish', 'Baker', DATE '1961-05-14', 'MALE', '+61881203519', '323 Boundary Road', 'Darwin City', 'NT', '0800', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000040', 'Hamish', 'Robinson', DATE '1977-05-12', 'MALE', '+61220832719', '303 Boundary Road', 'Surry Hills', 'NSW', '2010', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000041', 'Callum', 'Barnes', DATE '1952-10-23', 'MALE', '+61475729737', '217 Oxford Street', 'Ballarat', 'VIC', '3350', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PAT-000042', 'Olivia', 'Kelly', DATE '1961-07-04', 'FEMALE', '+61712539525', '18 Church Lane', 'Brisbane City', 'QLD', '4000', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- The rows above carry hand written PIDs, so move the sequence past them; otherwise
-- the first patient created through the API would collide with PAT-000001.
ALTER SEQUENCE patient_pid_seq RESTART WITH 43;
