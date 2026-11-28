INSERT INTO users(id,display_name,username,password,email,phone_number,account_status,gender,date_of_birth,joined_at)
SELECT r.id,r.display_name,r.username,r.password,r.email,r.phone_number,r.account_status,r.gender,r.date_of_birth,r.joined_at
FROM bootstrap_rows s CROSS JOIN LATERAL jsonb_populate_record(NULL::users,s.body) r
WHERE s.kind='users' ORDER BY r.id;
INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u CROSS JOIN roles r WHERE r.name='USER';
INSERT INTO addresses(id,recipient_name,recipient_phone,address_text,is_default,user_id)
SELECT r.id,r.recipient_name,r.recipient_phone,r.address_text,r.is_default,r.user_id
FROM bootstrap_rows s CROSS JOIN LATERAL jsonb_populate_record(NULL::addresses,s.body) r
WHERE s.kind='addresses' ORDER BY r.id;
