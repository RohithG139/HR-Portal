-- =============================================================================
-- DEV-ONLY dummy data for the hr schema (hr_app database).
--
-- NOT a Flyway migration on purpose: Flyway migrations ship to every environment,
-- demo data must not. Run it by hand against the dev Postgres:
--
--   docker exec -i hr-db-postgres psql -U postgres -d hr_app -v ON_ERROR_STOP=1 \
--     < infra/dev-seed/seed-dummy-data.sql
--
-- Then create the placeholder files for the seeded documents (storage keys seed-doc-*):
--   bash infra/dev-seed/seed-upload-files.sh
--
-- Covers every business table with every column filled where the row's state allows it
-- (columns that must be NULL for a given state — e.g. completed_at on an open item —
-- stay NULL). Employees are EMP101..EMP115. Dates are relative to current_date so the
-- data stays "current" whenever it is loaded.
--
-- Deliberately NOT seeded:
--   * employee.case_id / engine_task / manager_change_task — these must come from a real
--     CaseFabric case. Fake engine ids would show tasks in My Tasks that the engine
--     rejects on completion. Create real cases through the onboarding wizard instead.
--   * Keycloak accounts — idp_subject values are placeholders; these employees cannot
--     log in. Use the realm's demo users (hr1, manager1, employee1, itadmin1) for that.
--
-- One transaction: either everything loads or nothing does. Re-running fails fast on
-- the guard below instead of creating duplicates.
-- =============================================================================

begin;

do $$
begin
  if exists (select 1 from hr.employee where employee_code = 'EMP101') then
    raise exception 'Dummy data already loaded (EMP101 exists) — nothing changed.';
  end if;
end $$;

-- Lookup helpers (session-local, dropped automatically at disconnect).
create function pg_temp.emp(c text)  returns bigint language sql stable as $f$ select id from hr.employee where employee_code = c $f$;
create function pg_temp.job(n text)  returns bigint language sql stable as $f$ select id from hr.job_title where name = n $f$;
create function pg_temp.dept(n text) returns bigint language sql stable as $f$ select id from hr.department where name = n $f$;
create function pg_temp.doc(k text)  returns bigint language sql stable as $f$ select id from hr.document where storage_key = k $f$;
create function pg_temp.proj(c text) returns bigint language sql stable as $f$ select id from hr.project where project_code = c $f$;
create function pg_temp.tmpl(n text) returns bigint language sql stable as $f$ select id from hr.checklist_template where name = n $f$;
create function pg_temp.form(n text) returns bigint language sql stable as $f$ select id from hr.appraisal_form where name = n $f$;

-- ── Lookups missing for HR / IT / management roles ──────────────────────────────
insert into hr.job_title (name) values
  ('HR Business Partner'), ('IT Administrator'), ('Engineering Manager')
on conflict (name) do nothing;

-- ── Employees ───────────────────────────────────────────────────────────────────
-- Inserted in dependency order (managers first) so manager_id/buddy_id can resolve.
insert into hr.employee (employee_code, first_name, last_name, private_email, work_email, mobile_phone,
  date_of_birth, nationality, street, postcode, city, emergency_name, emergency_relationship, emergency_phone,
  job_title_id, department_id, grade, manager_id, buddy_id, app_role, idp_subject, account_status,
  first_login_at, stage, stage_since, archived_at)
values
  ('EMP101','Sophie','de Vries','sophie.devries@gmail.example','sophie.devries@cubeworks.local','+31611000101',
   '1981-04-12','Dutch','Keizersgracht 101','1015CJ','Amsterdam','Pieter de Vries','Spouse','+31611900101',
   pg_temp.job('Engineering Manager'), pg_temp.dept('Development'),'SENIOR_II', null, null,'MANAGER',
   'seed-0000-0000-0000-000000000101','ACTIVE', now() - interval '900 days','ACTIVE', current_date - 900, null),
  ('EMP103','Priya','Raman','priya.raman@gmail.example','priya.raman@cubeworks.local','+31611000103',
   '1987-09-23','Indian','Prinsengracht 203','1016HL','Amsterdam','Arjun Raman','Brother','+31611900103',
   pg_temp.job('HR Business Partner'), pg_temp.dept('Management & HR'),'SENIOR', null, null,'HR',
   'seed-0000-0000-0000-000000000103','ACTIVE', now() - interval '700 days','ACTIVE', current_date - 700, null);

insert into hr.employee (employee_code, first_name, last_name, private_email, work_email, mobile_phone,
  date_of_birth, nationality, street, postcode, city, emergency_name, emergency_relationship, emergency_phone,
  job_title_id, department_id, grade, manager_id, buddy_id, app_role, idp_subject, account_status,
  first_login_at, stage, stage_since, archived_at, created_by, updated_by)
values
  ('EMP102','Daniel','Okafor','daniel.okafor@gmail.example','daniel.okafor@cubeworks.local','+31611000102',
   '1984-11-02','Nigerian','Herengracht 52','1015BN','Amsterdam','Ngozi Okafor','Spouse','+31611900102',
   pg_temp.job('Tech Lead'), pg_temp.dept('QA'),'SENIOR_I', pg_temp.emp('EMP101'), null,'MANAGER',
   'seed-0000-0000-0000-000000000102','ACTIVE', now() - interval '800 days','ACTIVE', current_date - 800, null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('EMP104','Tom','Becker','tom.becker@gmail.example','tom.becker@cubeworks.local','+31611000104',
   '1990-02-17','German','Stationsplein 4','3511ED','Utrecht','Anna Becker','Mother','+49151900104',
   pg_temp.job('IT Administrator'), pg_temp.dept('Management & HR'),'MEDIOR', pg_temp.emp('EMP101'), null,'IT_ADMIN',
   'seed-0000-0000-0000-000000000104','ACTIVE', now() - interval '600 days','ACTIVE', current_date - 600, null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('EMP106','Emma','Visser','emma.visser@gmail.example','emma.visser@cubeworks.local','+31611000106',
   '1989-07-30','Dutch','Oudegracht 66','3511AT','Utrecht','Lars Visser','Partner','+31611900106',
   pg_temp.job('Senior Developer'), pg_temp.dept('Development'),'SENIOR', pg_temp.emp('EMP101'), null,'EMPLOYEE',
   'seed-0000-0000-0000-000000000106','ACTIVE', now() - interval '1100 days','ACTIVE', current_date - 1100, null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('EMP114','Kenji','Tanaka','kenji.tanaka@gmail.example','kenji.tanaka@cubeworks.local','+31611000114',
   '1983-05-08','Japanese','Coolsingel 114','3012AG','Rotterdam','Yuki Tanaka','Spouse','+31611900114',
   pg_temp.job('Tech Lead'), pg_temp.dept('Development'),'SENIOR_II', pg_temp.emp('EMP101'), null,'EMPLOYEE',
   'seed-0000-0000-0000-000000000114','ACTIVE', now() - interval '1300 days','ACTIVE', current_date - 1300, null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103'));

insert into hr.employee (employee_code, first_name, last_name, private_email, work_email, mobile_phone,
  date_of_birth, nationality, street, postcode, city, emergency_name, emergency_relationship, emergency_phone,
  job_title_id, department_id, grade, manager_id, buddy_id, app_role, idp_subject, account_status,
  first_login_at, stage, stage_since, archived_at, created_by, updated_by)
values
  ('EMP105','Lucas','Jansen','lucas.jansen@gmail.example','lucas.jansen@cubeworks.local','+31611000105',
   '1994-01-19','Dutch','Witte de Withstraat 5','3012BL','Rotterdam','Marieke Jansen','Mother','+31611900105',
   pg_temp.job('Developer'), pg_temp.dept('Development'),'MEDIOR', pg_temp.emp('EMP101'), pg_temp.emp('EMP106'),'EMPLOYEE',
   'seed-0000-0000-0000-000000000105','ACTIVE', now() - interval '420 days','ACTIVE', current_date - 400, null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('EMP107','Noah','Bakker','noah.bakker@gmail.example','noah.bakker@cubeworks.local','+31611000107',
   '1999-03-03','Dutch','Damrak 7','1012LG','Amsterdam','Ingrid Bakker','Mother','+31611900107',
   pg_temp.job('Developer'), pg_temp.dept('Development'),'JUNIOR', pg_temp.emp('EMP101'), null,'EMPLOYEE',
   'seed-0000-0000-0000-000000000107','INVITED', null,'ONBOARDING', current_date - 5, null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('EMP108','Mila','Smit','mila.smit@gmail.example','mila.smit@cubeworks.local','+31611000108',
   '1992-12-11','Belgian','Grote Markt 8','2000','Antwerp','Jonas Smit','Brother','+32470900108',
   pg_temp.job('UX Designer'), pg_temp.dept('Design'),'MEDIOR', pg_temp.emp('EMP101'), pg_temp.emp('EMP106'),'EMPLOYEE',
   'seed-0000-0000-0000-000000000108','ACTIVE', now() - interval '650 days','ACTIVE', current_date - 120, null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('EMP109','Ahmed','El Amrani','ahmed.elamrani@gmail.example','ahmed.elamrani@cubeworks.local','+31611000109',
   '1991-08-25','Moroccan','Hofweg 9','2511AA','The Hague','Samira El Amrani','Spouse','+31611900109',
   pg_temp.job('QA Engineer'), pg_temp.dept('QA'),'MEDIOR', pg_temp.emp('EMP102'), pg_temp.emp('EMP114'),'EMPLOYEE',
   'seed-0000-0000-0000-000000000109','ACTIVE', now() - interval '500 days','PIP', current_date - 30, null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('EMP110','Sara','Lindqvist','sara.lindqvist@gmail.example','sara.lindqvist@cubeworks.local','+31611000110',
   '1988-06-14','Swedish','Lange Voorhout 10','2514EA','The Hague','Erik Lindqvist','Father','+46701900110',
   pg_temp.job('QA Engineer'), pg_temp.dept('QA'),'SENIOR', pg_temp.emp('EMP102'), null,'EMPLOYEE',
   'seed-0000-0000-0000-000000000110','ACTIVE', now() - interval '950 days','OFFBOARDING', current_date - 10, null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('EMP111','Julia','Kowalski','julia.kowalski@gmail.example','julia.kowalski@cubeworks.local','+31611000111',
   '1997-10-05','Polish','Vrijthof 11','6211LD','Maastricht','Marek Kowalski','Father','+48601900111',
   pg_temp.job('UX Designer'), pg_temp.dept('Design'),'JUNIOR', pg_temp.emp('EMP101'), null,'EMPLOYEE',
   'seed-0000-0000-0000-000000000111','ACTIVE', now() - interval '60 days','ACTIVE', current_date - 30, null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('EMP112','Ravi','Patel','ravi.patel@gmail.example','ravi.patel@cubeworks.local','+31611000112',
   '1998-02-28','Indian','Stratumseind 12','5611ES','Eindhoven','Meera Patel','Mother','+31611900112',
   pg_temp.job('Developer'), pg_temp.dept('Development'),'JUNIOR', pg_temp.emp('EMP102'), null,'EMPLOYEE',
   'seed-0000-0000-0000-000000000112','ACTIVE', now() - interval '75 days','ACTIVE', current_date - 45, null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('EMP113','Chloe','Martin','chloe.martin@gmail.example','chloe.martin@cubeworks.local','+31611000113',
   '1986-09-09','French','Neude 13','3512AE','Utrecht','Hugo Martin','Spouse','+33612900113',
   pg_temp.job('Senior Developer'), pg_temp.dept('Development'),'SENIOR_I', pg_temp.emp('EMP101'), null,'EMPLOYEE',
   'seed-0000-0000-0000-000000000113','DISABLED', now() - interval '1500 days','ARCHIVED', current_date - 40, now() - interval '40 days',
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('EMP115','Fatima','Haddad','fatima.haddad@gmail.example','fatima.haddad@cubeworks.local','+31611000115',
   '2000-07-21','Lebanese','Markt 15','2611GP','Delft','Omar Haddad','Brother','+31611900115',
   pg_temp.job('QA Engineer'), pg_temp.dept('QA'),'JUNIOR', pg_temp.emp('EMP102'), null,'EMPLOYEE',
   null,'NOT_INVITED', null,'ONBOARDING', current_date - 1, null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103'));

-- Buddies within the batch above: a VALUES row cannot see rows inserted by the same statement.
update hr.employee e set buddy_id = pg_temp.emp(v.b)
from (values ('EMP107','EMP105'), ('EMP110','EMP109'), ('EMP111','EMP108'), ('EMP112','EMP105'), ('EMP115','EMP110')) as v(e, b)
where e.employee_code = v.e;

-- ── Documents (files created by seed-upload-files.sh) ───────────────────────────
insert into hr.document (employee_id, doc_type, file_name, content_type, size_bytes, storage_key, uploaded_by,
  uploaded_at, created_by, updated_by)
select pg_temp.emp(e), t, f, ct, sz, k, pg_temp.emp('EMP103'), now() - (d || ' days')::interval,
       pg_temp.emp('EMP103'), pg_temp.emp('EMP103')
from (values
  ('EMP105','CONTRACT','contract-lucas-jansen.pdf','application/pdf',48213,'seed-doc-01',420),
  ('EMP106','CONTRACT','contract-emma-visser-2023.pdf','application/pdf',51022,'seed-doc-02',1100),
  ('EMP106','CONTRACT','contract-emma-visser-permanent.pdf','application/pdf',50114,'seed-doc-03',735),
  ('EMP107','CONTRACT','contract-noah-bakker.pdf','application/pdf',47390,'seed-doc-04',12),
  ('EMP108','CONTRACT','contract-mila-smit.pdf','application/pdf',46871,'seed-doc-05',650),
  ('EMP109','CONTRACT','contract-ahmed-elamrani.pdf','application/pdf',49002,'seed-doc-06',500),
  ('EMP110','CONTRACT','contract-sara-lindqvist.pdf','application/pdf',47755,'seed-doc-07',950),
  ('EMP111','CONTRACT','contract-julia-kowalski.pdf','application/pdf',45309,'seed-doc-08',60),
  ('EMP112','CONTRACT','contract-ravi-patel.pdf','application/pdf',46120,'seed-doc-09',75),
  ('EMP113','CONTRACT','contract-chloe-martin.pdf','application/pdf',50873,'seed-doc-10',1500),
  ('EMP114','CONTRACT','contract-kenji-tanaka.pdf','application/pdf',52260,'seed-doc-11',1300),
  ('EMP115','CONTRACT','contract-fatima-haddad.pdf','application/pdf',44987,'seed-doc-12',2),
  (null,    'POLICY','code-of-conduct-2026.pdf','application/pdf',182340,'seed-doc-13',300),
  ('EMP111','POLICY_ACK','code-of-conduct-ack-julia-kowalski.pdf','application/pdf',12044,'seed-doc-14',55),
  ('EMP110','RESIGNATION_LETTER','resignation-sara-lindqvist.pdf','application/pdf',23411,'seed-doc-15',10),
  ('EMP109','PIP_ATTACHMENT','pip-goals-ahmed-elamrani.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document',31877,'seed-doc-16',28),
  ('EMP109','DISPUTE_ATTACHMENT','pip-dispute-ahmed-elamrani.pdf','application/pdf',19340,'seed-doc-17',26),
  ('EMP105','CLIENT_FEEDBACK_ATTACHMENT','feedback-northwind-q3.pdf','application/pdf',27500,'seed-doc-18',40),
  ('EMP113','FINAL_DOCUMENT','final-settlement-chloe-martin.pdf','application/pdf',33980,'seed-doc-19',40),
  ('EMP106','OTHER','certificate-aws-architect-emma-visser.png','image/png',204800,'seed-doc-20',90)
) as v(e, t, f, ct, sz, k, d);

-- ── Contracts (one current per employee; Emma also has an expired fixed-term one) ─
insert into hr.contract (employee_id, contract_type, start_date, end_date, hours_per_week, probation_months,
  is_current, created_by, updated_by)
select pg_temp.emp(e), ct, current_date - s, case when en is null then null else current_date + en end, h, p, cur,
       pg_temp.emp('EMP103'), pg_temp.emp('EMP103')
from (values
  ('EMP101','PERMANENT', 900, null, 40, 0, true),
  ('EMP102','PERMANENT', 800, null, 40, 0, true),
  ('EMP103','PERMANENT', 700, null, 36, 1, true),
  ('EMP104','PERMANENT', 600, null, 40, 1, true),
  ('EMP105','PERMANENT', 400, null, 40, 2, true),
  ('EMP106','FIXED_TERM',1100, -735, 40, 2, false),
  ('EMP106','PERMANENT', 735, null, 32, 0, true),
  ('EMP107','FIXED_TERM',   5,  360, 40, 2, true),
  ('EMP108','PERMANENT', 650, null, 32, 1, true),
  ('EMP109','PERMANENT', 500, null, 40, 2, true),
  ('EMP110','PERMANENT', 950, null, 40, 2, true),
  ('EMP111','PERMANENT',  30, null, 40, 2, true),
  ('EMP112','FIXED_TERM',  45,  320, 40, 2, true),
  ('EMP113','PERMANENT',1500, null, 40, 2, true),
  ('EMP114','PERMANENT',1300, null, 40, 0, true),
  ('EMP115','FIXED_TERM',  -14, 351, 24, 1, true)
) as v(e, ct, s, en, h, p, cur);

-- ── Onboarding drafts (HR wizard "Save draft") ──────────────────────────────────
insert into hr.onboarding_draft (first_name, last_name, form, contract_document_id, created_by, updated_by)
values
  ('Liam','Mulder', jsonb_build_object(
      'firstName','Liam','lastName','Mulder','privateEmail','liam.mulder@gmail.example','mobilePhone','+31611000201',
      'dateOfBirth','1996-04-04','nationality','Dutch','street','Nieuwstraat 21','postcode','1012NM','city','Amsterdam',
      'jobTitle','Developer','department','Development','grade','JUNIOR','contractType','FIXED_TERM',
      'startDate', to_char(current_date + 21,'YYYY-MM-DD'),'endDate', to_char(current_date + 386,'YYYY-MM-DD'),
      'hoursPerWeek',40,'probationMonths',2,'managerCode','EMP101','buddyCode','EMP105'),
   null, pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('Zoe','van Dijk', jsonb_build_object(
      'firstName','Zoe','lastName','van Dijk','privateEmail','zoe.vandijk@gmail.example','mobilePhone','+31611000202',
      'jobTitle','UX Designer','department','Design','grade','MEDIOR','contractType','PERMANENT',
      'startDate', to_char(current_date + 35,'YYYY-MM-DD'),'hoursPerWeek',32,'probationMonths',1),
   null, pg_temp.emp('EMP103'), pg_temp.emp('EMP103'));

-- ── Onboarding records ──────────────────────────────────────────────────────────
insert into hr.onboarding (employee_id, template_id, id_checked, contract_signed_received, right_to_work_verified,
  status, completed_at, completed_by, it_laptop_model, it_keyboard_layout, it_ready_by, it_delivery_method,
  it_notes, it_status, it_sent_at, created_by, updated_by)
values
  (pg_temp.emp('EMP105'), pg_temp.tmpl('Development'), true, true, true, 'COMPLETED', now() - interval '370 days', pg_temp.emp('EMP103'),
   'Dell XPS 15 9530','US-International', current_date - 401,'OFFICE_DAY1','Docker Desktop + IntelliJ Ultimate licence','COMPLETED', now() - interval '410 days',
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  (pg_temp.emp('EMP107'), pg_temp.tmpl('Development'), true, true, true, 'IN_PROGRESS', null, null,
   'Lenovo ThinkPad X1 Carbon Gen 12','US-International', current_date - 6,'SHIP_HOME','Ship to home address; needs 2 external monitors','IN_PROGRESS', now() - interval '10 days',
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  (pg_temp.emp('EMP111'), pg_temp.tmpl('Design'), true, true, true, 'COMPLETED', now() - interval '5 days', pg_temp.emp('EMP103'),
   'MacBook Pro 14 M3','Dutch (QWERTY)', current_date - 31,'OFFICE_DAY1','Figma org seat + Adobe CC','COMPLETED', now() - interval '45 days',
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  (pg_temp.emp('EMP115'), pg_temp.tmpl('QA'), true, true, false, 'IN_PROGRESS', null, null,
   'Dell Latitude 7440','US-International', current_date + 12,'OFFICE_DAY1','Needs Android + iOS test devices','NOT_SENT', null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103'));

-- ── Assets: every type and every status ─────────────────────────────────────────
insert into hr.asset (asset_number, asset_type, model, serial_number, status, employee_id, issued_on,
  return_due_date, returned_on, return_condition, created_by, updated_by)
select n, t, m, sn, st, pg_temp.emp(e),
       case when i is null then null else current_date - i end,
       case when rd is null then null else current_date + rd end,
       case when ro is null then null else current_date - ro end,
       rc, pg_temp.emp('EMP104'), pg_temp.emp('EMP104')
from (values
  ('AST-1001','LAPTOP','Dell XPS 15 9530','DXPS-7731-A','ISSUED','EMP105',401,null,null,null),
  ('AST-1002','MONITOR','Dell U2723QE 27"','CN-0U27-1002','ISSUED','EMP105',401,null,null,null),
  ('AST-1003','LAPTOP','Lenovo ThinkPad X1 Carbon Gen 12','PF-4XK2-1003','ISSUED','EMP107',3,null,null,null),
  ('AST-1004','DOCKING_STATION','Lenovo ThinkPad Thunderbolt 4 Dock','TB4-1004','ISSUED','EMP107',3,null,null,null),
  ('AST-1005','PHONE','iPhone 15 (QA device)','F2LZ-1005','RETURN_DUE','EMP110',900,9,null,null),
  ('AST-1006','LAPTOP','Dell Latitude 7440','DL74-1006','RETURN_DUE','EMP110',900,9,null,null),
  ('AST-1007','ACCESS_BADGE','HID iCLASS badge',null,'RETURNED','EMP113',1490,null,40,'GOOD'),
  ('AST-1008','LAPTOP','MacBook Pro 16 M1','C02F-1008','NOT_RETURNED','EMP113',1490,null,null,null),
  ('AST-1009','HEADSET','Jabra Evolve2 75','JB75-1009','RETURNED','EMP113',1490,null,40,'DAMAGED'),
  ('AST-1010','KEYBOARD_MOUSE','Logitech MX Keys + MX Master 3S','LG-MX-1010','ISSUED','EMP108',640,null,null,null),
  ('AST-1011','LAPTOP','MacBook Pro 14 M3','C02M-1011','ISSUED','EMP111',30,null,null,null),
  ('AST-1012','OTHER','Wacom Intuos Pro M','WCM-1012','ISSUED','EMP111',30,null,null,null),
  ('AST-1013','LAPTOP','Dell Latitude 7440','DL74-1013','IN_STORAGE',null,null,null,null,null),
  ('AST-1014','MONITOR','LG 27UP850 27"','LG27-1014','IN_STORAGE',null,null,null,null,null)
) as v(n, t, m, sn, st, e, i, rd, ro, rc);

-- ── Checklist items ─────────────────────────────────────────────────────────────
-- Noah (EMP107, onboarding in progress): instantiated from the Development template,
-- first half done, rest open.
insert into hr.checklist_item (employee_id, phase, template_item_id, name, section, owner_role, item_kind,
  mandatory, due_date, status, done_at, done_by, created_by, updated_by)
select pg_temp.emp('EMP107'), 'ONBOARDING', ti.id, ti.name, ti.section, ti.owner_role, ti.item_kind, ti.mandatory,
       current_date - 5 + ti.due_offset_days,
       case when ti.rn <= ti.cnt / 2 then 'DONE' else 'OPEN' end,
       case when ti.rn <= ti.cnt / 2 then now() - interval '2 days' end,
       case when ti.rn <= ti.cnt / 2 then pg_temp.emp(case ti.owner_role when 'IT_ADMIN' then 'EMP104'
                                                                        when 'MANAGER' then 'EMP101'
                                                                        when 'EMPLOYEE' then 'EMP107'
                                                                        else 'EMP103' end) end,
       pg_temp.emp('EMP103'), pg_temp.emp('EMP103')
from (select i.*, row_number() over (order by i.sort_order, i.id) rn, count(*) over () cnt
      from hr.checklist_template_item i where i.template_id = pg_temp.tmpl('Development')) ti;

-- Julia (EMP111, onboarding completed): Design template, all done.
insert into hr.checklist_item (employee_id, phase, template_item_id, name, section, owner_role, item_kind,
  mandatory, due_date, status, done_at, done_by, created_by, updated_by)
select pg_temp.emp('EMP111'), 'ONBOARDING', ti.id, ti.name, ti.section, ti.owner_role, ti.item_kind, ti.mandatory,
       current_date - 30 + ti.due_offset_days, 'DONE', now() - interval '6 days',
       pg_temp.emp(case ti.owner_role when 'IT_ADMIN' then 'EMP104' when 'MANAGER' then 'EMP101'
                                      when 'EMPLOYEE' then 'EMP111' else 'EMP103' end),
       pg_temp.emp('EMP103'), pg_temp.emp('EMP103')
from hr.checklist_template_item ti where ti.template_id = pg_temp.tmpl('Design');

-- Fatima (EMP115, onboarding just started): QA template, all open, one cancelled ad-hoc item.
insert into hr.checklist_item (employee_id, phase, template_item_id, name, section, owner_role, item_kind,
  mandatory, due_date, status, created_by, updated_by)
select pg_temp.emp('EMP115'), 'ONBOARDING', ti.id, ti.name, ti.section, ti.owner_role, ti.item_kind, ti.mandatory,
       current_date + 14 + ti.due_offset_days, 'OPEN', pg_temp.emp('EMP103'), pg_temp.emp('EMP103')
from hr.checklist_template_item ti where ti.template_id = pg_temp.tmpl('QA');

insert into hr.checklist_item (employee_id, phase, template_item_id, name, section, owner_role, item_kind,
  mandatory, due_date, status, asset_id, created_by, updated_by)
values
  (pg_temp.emp('EMP115'), 'ONBOARDING', null, 'Order BrowserStack licence', 'ACCOUNTS_INSTALLATIONS', 'IT_ADMIN', 'GENERIC',
   false, current_date + 10, 'CANCELLED', null, pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  (pg_temp.emp('EMP107'), 'ONBOARDING', null, 'Issue docking station', 'EQUIPMENT_ISSUED', 'IT_ADMIN', 'EQUIPMENT',
   true, current_date - 2, 'OPEN', (select id from hr.asset where asset_number = 'AST-1004'), pg_temp.emp('EMP103'), pg_temp.emp('EMP103'));

-- Sara (EMP110, offboarding): ad-hoc offboarding items, one overdue + reminded.
insert into hr.checklist_item (employee_id, phase, template_item_id, name, section, owner_role, item_kind,
  mandatory, due_date, status, done_at, done_by, asset_id, overdue_reminder_at, created_by, updated_by)
values
  (pg_temp.emp('EMP110'), 'OFFBOARDING', null, 'Return iPhone 15 (QA device)', 'RETURN_ASSETS', 'EMPLOYEE', 'EQUIPMENT',
   true, current_date + 9, 'OPEN', null, null, (select id from hr.asset where asset_number = 'AST-1005'), null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  (pg_temp.emp('EMP110'), 'OFFBOARDING', null, 'Return Dell Latitude 7440', 'RETURN_ASSETS', 'EMPLOYEE', 'EQUIPMENT',
   true, current_date + 9, 'OPEN', null, null, (select id from hr.asset where asset_number = 'AST-1006'), null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  (pg_temp.emp('EMP110'), 'OFFBOARDING', null, 'Exit interview', 'HR_HANDOVER', 'HR', 'EXIT_INTERVIEW',
   true, current_date - 2, 'OPEN', null, null, null, now() - interval '1 day',
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  (pg_temp.emp('EMP110'), 'OFFBOARDING', null, 'Hand over test automation suite ownership', 'REASSIGN_TEAM', 'MANAGER', 'ASSIGNMENTS_END',
   true, current_date - 4, 'DONE', now() - interval '3 days', pg_temp.emp('EMP102'), null, null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  (pg_temp.emp('EMP110'), 'OFFBOARDING', null, 'Disable Jira / Confluence account', 'REMOVE_ACCESS', 'IT_ADMIN', 'ACCOUNT',
   true, current_date + 9, 'OPEN', null, null, null, null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103'));

-- ── Clients, projects, assignments ──────────────────────────────────────────────
insert into hr.client (name, industry, address, created_by, updated_by) values
  ('Northwind Logistics','Logistics','Europaweg 200, 3199LC Rotterdam', pg_temp.emp('EMP101'), pg_temp.emp('EMP101')),
  ('Contoso Bank','Financial services','Zuidplein 36, 1077XV Amsterdam', pg_temp.emp('EMP101'), pg_temp.emp('EMP101')),
  ('Fabrikam Health','Healthcare','Heidelberglaan 100, 3584CX Utrecht', pg_temp.emp('EMP101'), pg_temp.emp('EMP101')),
  ('CubeWorks Internal','Internal','Keizersgracht 101, 1015CJ Amsterdam', pg_temp.emp('EMP101'), pg_temp.emp('EMP101'));

insert into hr.project (project_code, name, client_id, project_manager_id, project_type, status, start_date, end_date,
  work_mode, tech_stack, description, contract_reference, withdrawal_date, withdrawal_reason, closed_at, created_by, updated_by)
select c, n, (select id from hr.client where name = cl), pg_temp.emp(pm), pt, st, current_date - s,
       case when en is null then null else current_date + en end, wm, ts, ds, cr,
       case when st = 'WITHDRAWN' then current_date - 20 end,
       case when st = 'WITHDRAWN' then 'Client cancelled the programme after budget freeze.' end,
       case when st in ('COMPLETED','WITHDRAWN') then now() - interval '20 days' end,
       pg_temp.emp('EMP101'), pg_temp.emp('EMP101')
from (values
  ('PRJ-101','Route Optimiser v2','Northwind Logistics','EMP114','TIME_MATERIAL','ACTIVE',200,160,'Hybrid (2 days on site)','Java 21, Spring Boot, Kafka, PostgreSQL','Rebuild of the route optimisation engine with live traffic feeds.','NWL-2026-014'),
  ('PRJ-102','Mobile Banking Redesign','Contoso Bank','EMP101','FIXED_PRICE','ACTIVE',120,90,'Remote','React Native, TypeScript, Figma','New mobile app UX and component library.','CTB-SOW-77'),
  ('PRJ-103','Patient Portal QA','Fabrikam Health','EMP102','TIME_MATERIAL','OVERDUE',300,-15,'On site','Playwright, Cypress, k6','End-to-end and load test coverage for the patient portal.','FBH-QA-03'),
  ('PRJ-104','Data Warehouse Migration','Northwind Logistics','EMP114','FIXED_PRICE','COMPLETED',500,-60,'Remote','dbt, Snowflake, Airflow','Migration of on-prem DWH to Snowflake.','NWL-2025-002'),
  ('PRJ-105','Fraud Detection PoC','Contoso Bank','EMP101','TIME_MATERIAL','WITHDRAWN',90,60,'Remote','Python, scikit-learn','Proof of concept for real-time card fraud scoring.','CTB-POC-12'),
  ('PRJ-106','Internal HR Portal','CubeWorks Internal','EMP101','INTERNAL','ON_HOLD',60,240,'Hybrid','Spring Boot, React, CaseFabric','This application.','INT-HR-01'),
  ('PRJ-107','Accessibility Audit','Fabrikam Health','EMP102','FIXED_PRICE','PLANNED',-30,120,'Remote','axe-core, WCAG 2.2','WCAG 2.2 AA audit of all public pages.','FBH-A11Y-01')
) as v(c, n, cl, pm, pt, st, s, en, wm, ts, ds, cr);

insert into hr.project_assignment (project_id, employee_id, role_on_project, allocation_pct, start_date, end_date,
  end_reason, created_by, updated_by)
select pg_temp.proj(p), pg_temp.emp(e), r, a, current_date - s,
       case when en is null then null else current_date + en end, er, pg_temp.emp('EMP101'), pg_temp.emp('EMP101')
from (values
  ('PRJ-101','EMP105','Backend developer',100,200,null,null),
  ('PRJ-101','EMP106','Lead developer',60,200,null,null),
  ('PRJ-102','EMP106','Frontend developer',40,120,null,null),
  ('PRJ-102','EMP108','UX designer',80,120,null,null),
  ('PRJ-102','EMP111','Junior UX designer',100,25,null,null),
  ('PRJ-103','EMP109','QA engineer',100,300,null,null),
  ('PRJ-103','EMP110','Senior QA engineer',100,300,9,null),
  ('PRJ-104','EMP105','Data engineer',50,500,-60,'PROJECT_COMPLETED'),
  ('PRJ-104','EMP113','Senior developer',100,500,-60,'PROJECT_COMPLETED'),
  ('PRJ-105','EMP112','Developer',100,40,-20,'PROJECT_WITHDRAWN'),
  ('PRJ-106','EMP107','Developer',100,3,null,null),
  ('PRJ-103','EMP112','QA support',50,15,-1,'ENDED_EARLY'),
  ('PRJ-101','EMP113','Senior developer',50,150,-40,'EMPLOYEE_LEAVING')
) as v(p, e, r, a, s, en, er);

insert into hr.client_feedback (project_id, applies_to, employee_id, contact_name, contact_role, received_date,
  collected_via, rating, comments, attachment_document_id, recorded_by, created_by, updated_by)
values
  (pg_temp.proj('PRJ-101'),'TEAM',null,'Hanneke Vos','Head of Operations', current_date - 40,'MEETING',4,
   'Team is delivering steadily; release cadence improved since Q2.', pg_temp.doc('seed-doc-18'), pg_temp.emp('EMP114'), pg_temp.emp('EMP114'), pg_temp.emp('EMP114')),
  (pg_temp.proj('PRJ-101'),'PERSON',pg_temp.emp('EMP105'),'Hanneke Vos','Head of Operations', current_date - 40,'EMAIL',5,
   'Lucas proactively fixed the Kafka consumer lag issue before go-live.', null, pg_temp.emp('EMP114'), pg_temp.emp('EMP114'), pg_temp.emp('EMP114')),
  (pg_temp.proj('PRJ-103'),'PERSON',pg_temp.emp('EMP109'),'Dr. Anouk Peeters','Product Owner', current_date - 35,'CALL',2,
   'Regression runs often incomplete; several critical bugs reached acceptance.', null, pg_temp.emp('EMP102'), pg_temp.emp('EMP102'), pg_temp.emp('EMP102')),
  (pg_temp.proj('PRJ-104'),'TEAM',null,'Bram Hendriks','CIO', current_date - 55,'SURVEY',5,
   'Migration finished two weeks early with zero data loss.', null, pg_temp.emp('EMP101'), pg_temp.emp('EMP101'), pg_temp.emp('EMP101'));

-- ── Exits ───────────────────────────────────────────────────────────────────────
insert into hr.employee_exit (employee_id, exit_type, initiated_by, initiated_by_role, notice_date, requested_last_day,
  last_working_day, message, letter_document_id, notes, status, approved_by, approved_at, closed_at,
  interview_needed, interview_planned_on, interview_held_on, interview_held_by, interview_main_reason,
  interview_experience_rating, interview_improvements, interview_would_recommend, created_by, updated_by)
values
  -- Sara: resignation approved, offboarding in progress, interview planned.
  (pg_temp.emp('EMP110'),'RESIGNATION',pg_temp.emp('EMP110'),'EMPLOYEE', current_date - 10, current_date + 20,
   current_date + 9,'I have accepted a position abroad and would like to leave by the end of the month.',
   pg_temp.doc('seed-doc-15'),'Agreed earlier last day; remaining holiday paid out.','IN_PROGRESS',
   pg_temp.emp('EMP103'), now() - interval '9 days', null,
   true, current_date + 5, null, null, null, null, null, null, pg_temp.emp('EMP110'), pg_temp.emp('EMP103')),
  -- Chloe: resignation closed, interview held, archived.
  (pg_temp.emp('EMP113'),'RESIGNATION',pg_temp.emp('EMP113'),'EMPLOYEE', current_date - 75, current_date - 40,
   current_date - 40,'Thank you for five great years — I am starting my own consultancy.',
   null,'Knowledge transfer to Kenji completed.','CLOSED',
   pg_temp.emp('EMP103'), now() - interval '74 days', now() - interval '40 days',
   true, current_date - 45, current_date - 44, pg_temp.emp('EMP103'),'CAREER_GROWTH',
   4,'More structured career paths for senior engineers.','Yes — great team and interesting clients.',
   pg_temp.emp('EMP113'), pg_temp.emp('EMP103')),
  -- Emma: earlier resignation request, rejected (withdrawn after counter-offer).
  (pg_temp.emp('EMP106'),'RESIGNATION',pg_temp.emp('EMP106'),'EMPLOYEE', current_date - 200, current_date - 140,
   current_date - 140,'Considering an offer elsewhere.', null,'Employee withdrew after counter-offer.','REJECTED',
   null, null, null, false, null, null, null, null, null, null, null, pg_temp.emp('EMP106'), pg_temp.emp('EMP103'));

-- ── Development plans + progress check-ins ──────────────────────────────────────
insert into hr.development_plan (employee_id, period_label, status, submitted_at, evaluation_text, evaluation_outcome,
  evaluated_by, evaluated_at, previous_plan_id, created_by, updated_by)
values
  (pg_temp.emp('EMP106'),'2025 H2','CLOSED', now() - interval '330 days',
   'Completed AWS Solutions Architect certification and mentored two juniors.','PLAN_CLOSED',
   pg_temp.emp('EMP101'), now() - interval '150 days', null, pg_temp.emp('EMP106'), pg_temp.emp('EMP101'));
insert into hr.development_plan (employee_id, period_label, status, submitted_at, evaluation_text, evaluation_outcome,
  evaluated_by, evaluated_at, previous_plan_id, created_by, updated_by)
values
  (pg_temp.emp('EMP106'),'2026 H2','ACTIVE', now() - interval '100 days', null, null, null, null,
   (select id from hr.development_plan where employee_id = pg_temp.emp('EMP106') and period_label = '2025 H2'),
   pg_temp.emp('EMP106'), pg_temp.emp('EMP101')),
  (pg_temp.emp('EMP105'),'2026','ACTIVE', now() - interval '200 days', null, null, null, null, null,
   pg_temp.emp('EMP105'), pg_temp.emp('EMP101')),
  (pg_temp.emp('EMP108'),'2026 H2','SUBMITTED', now() - interval '3 days', null, null, null, null, null,
   pg_temp.emp('EMP108'), pg_temp.emp('EMP108')),
  (pg_temp.emp('EMP114'),'2026 H2','CHANGES_REQUESTED', now() - interval '12 days',
   'Please add a measurable goal for the architecture guild.', null, pg_temp.emp('EMP101'), now() - interval '10 days', null,
   pg_temp.emp('EMP114'), pg_temp.emp('EMP101')),
  (pg_temp.emp('EMP111'),'Probation 2026','DRAFT', null, null, null, null, null, null,
   pg_temp.emp('EMP111'), pg_temp.emp('EMP111'));

insert into hr.checkin (checkin_type, plan_id, seq_no, notes, employee_comment, outcome, done_by, done_at, created_by, updated_by)
select 'PLAN_PROGRESS', p.id, v.seq, v.notes, v.ec, v.outc, case when v.outc is not null then pg_temp.emp('EMP101') end,
       case when v.outc is not null then now() - (v.d || ' days')::interval end, pg_temp.emp('EMP101'), pg_temp.emp('EMP101')
from hr.development_plan p
join (values
  ('EMP105','2026',1,'Kafka training done; good progress on system design.','Would like more on-call exposure.','CONTINUE',140),
  ('EMP105','2026',2,'Leading the consumer-lag fix showed strong ownership.','Enjoying the backend work.','CONTINUE',50),
  ('EMP105','2026',3,null,null,null,0),
  ('EMP106','2025 H2',1,'Certification on track.','Exam booked.','CONTINUE',250),
  ('EMP106','2025 H2',2,'All goals reached.','Thanks for the support!','PERFORMED',160),
  ('EMP106','2026 H2',1,'Started the frontend architecture track.','Need a React mentor.','SKILL_GAP',30)
) as v(e, per, seq, notes, ec, outc, d) on p.employee_id = pg_temp.emp(v.e) and p.period_label = v.per;

-- ── Appraisal rounds, appraisals, reviews ───────────────────────────────────────
insert into hr.appraisal_round (name, review_period_start, review_period_end, standard_form_id, leadership_form_id,
  self_peer_due, manager_due, hr_due, eligibility_date, peer_mode, peer_anonymous, status,
  started_at, started_by, closed_at, closed_by, created_by, updated_by)
values
  ('Annual review 2025', date_trunc('year', current_date - interval '1 year')::date,
   (date_trunc('year', current_date) - interval '1 day')::date, pg_temp.form('Standard'), pg_temp.form('Leadership'),
   current_date - 240, current_date - 225, current_date - 210, current_date - 300,'REQUIRED', true,'CLOSED',
   now() - interval '260 days', pg_temp.emp('EMP103'), now() - interval '205 days', pg_temp.emp('EMP103'),
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('Mid-year review 2026', date_trunc('year', current_date)::date, current_date - 10,
   pg_temp.form('Standard'), pg_temp.form('Leadership'),
   current_date + 7, current_date + 21, current_date + 35, current_date - 60,'OPTIONAL', false,'OPEN',
   now() - interval '5 days', pg_temp.emp('EMP103'), null, null, pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('Annual review 2026', date_trunc('year', current_date)::date,
   (date_trunc('year', current_date) + interval '1 year - 1 day')::date, pg_temp.form('Standard'), null,
   current_date + 70, current_date + 85, current_date + 100, current_date + 40,'OFF', true,'DRAFT',
   null, null, null, null, pg_temp.emp('EMP103'), pg_temp.emp('EMP103'));

insert into hr.appraisal (round_id, employee_id, status, outcome, final_summary, finalised_by, finalised_at,
  promo_job_title_id, promo_grade, promo_effective_date, promo_notify_mode, promo_applied_at, created_by, updated_by)
select (select id from hr.appraisal_round where name = r), pg_temp.emp(e), st, oc, fs,
       case when st = 'FINALISED' then pg_temp.emp('EMP103') end,
       case when st = 'FINALISED' then now() - interval '208 days' end,
       case when oc = 'PROMOTED' then pg_temp.job(pj) end,
       case when oc = 'PROMOTED' then pg end,
       case when oc = 'PROMOTED' then current_date - 200 end,
       case when oc = 'PROMOTED' then 'AFTER_MEETING' end,
       case when oc = 'PROMOTED' then now() - interval '200 days' end,
       pg_temp.emp('EMP103'), pg_temp.emp('EMP103')
from (values
  ('Annual review 2025','EMP105','FINALISED','PROMOTED','Strong year; promoted to Medior.','Developer','MEDIOR'),
  ('Annual review 2025','EMP106','FINALISED','NO_CHANGE','Solid performance, continues as Senior.',null,null),
  ('Annual review 2025','EMP109','FINALISED','UNDER_PERFORMING','Quality issues on PRJ-103; PIP recommended.',null,null),
  ('Annual review 2025','EMP113','CANCELLED',null,null,null,null),
  ('Mid-year review 2026','EMP105','OPEN',null,null,null,null),
  ('Mid-year review 2026','EMP106','OPEN',null,null,null,null),
  ('Mid-year review 2026','EMP108','OPEN',null,null,null,null),
  ('Mid-year review 2026','EMP114','OPEN',null,null,null,null)
) as v(r, e, st, oc, fs, pj, pg);

-- Appraisal reviews (SELF / PEER / MANAGER).
insert into hr.review (review_type, form_id, reviewer_id, appraisal_id, due_date, status, nominated_by,
  decline_reason, outcome, submitted_at, created_by, updated_by)
select v.rt, pg_temp.form(case when v.rt = 'MANAGER' and v.e = 'EMP114' then 'Leadership' else 'Standard' end),
       pg_temp.emp(v.rv), a.id, ar.self_peer_due + case when v.rt = 'MANAGER' then 0 else 0 end,
       v.st, case when v.rt = 'PEER' then pg_temp.emp(v.e) end, v.dr, v.oc,
       case when v.st = 'SUBMITTED' then now() - interval '230 days' end,
       pg_temp.emp('EMP103'), pg_temp.emp('EMP103')
from (values
  ('Annual review 2025','EMP105','SELF','EMP105','SUBMITTED',null,null),
  ('Annual review 2025','EMP105','PEER','EMP106','SUBMITTED',null,null),
  ('Annual review 2025','EMP105','MANAGER','EMP101','SUBMITTED',null,'PROMOTE'),
  ('Annual review 2025','EMP106','SELF','EMP106','SUBMITTED',null,null),
  ('Annual review 2025','EMP106','PEER','EMP114','SUBMITTED',null,null),
  ('Annual review 2025','EMP106','MANAGER','EMP101','SUBMITTED',null,'MEETS'),
  ('Annual review 2025','EMP109','SELF','EMP109','SUBMITTED',null,null),
  ('Annual review 2025','EMP109','PEER','EMP110','DECLINED','Worked on a different project all year.',null),
  ('Annual review 2025','EMP109','PEER','EMP112','SUBMITTED',null,null),
  ('Annual review 2025','EMP109','MANAGER','EMP102','SUBMITTED',null,'BELOW'),
  ('Mid-year review 2026','EMP105','SELF','EMP105','DRAFT',null,null),
  ('Mid-year review 2026','EMP105','PEER','EMP107','REQUESTED',null,null),
  ('Mid-year review 2026','EMP105','MANAGER','EMP101','DRAFT',null,null),
  ('Mid-year review 2026','EMP106','SELF','EMP106','SUBMITTED',null,null),
  ('Mid-year review 2026','EMP106','PEER','EMP105','DRAFT',null,null),
  ('Mid-year review 2026','EMP108','SELF','EMP108','DRAFT',null,null),
  ('Mid-year review 2026','EMP108','PEER','EMP111','REQUESTED',null,null),
  ('Mid-year review 2026','EMP114','SELF','EMP114','DRAFT',null,null),
  ('Mid-year review 2026','EMP114','MANAGER','EMP101','DRAFT',null,null)
) as v(r, e, rt, rv, st, dr, oc)
join hr.appraisal_round ar on ar.name = v.r
join hr.appraisal a on a.round_id = ar.id and a.employee_id = pg_temp.emp(v.e);

-- Probation reviews (one confirmed, one ending in HR rejection of the "end" proposal) and project review.
insert into hr.review (review_type, form_id, reviewer_id, contract_id, due_date, status, outcome, submitted_at,
  hr_decision, hr_decided_by, hr_decided_at, created_by, updated_by)
values
  ('PROBATION', pg_temp.form('Probation'), pg_temp.emp('EMP101'),
   (select id from hr.contract where employee_id = pg_temp.emp('EMP111') and is_current), current_date + 30,'DRAFT',
   null, null, null, null, null, pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('PROBATION', pg_temp.form('Probation'), pg_temp.emp('EMP101'),
   (select id from hr.contract where employee_id = pg_temp.emp('EMP105') and is_current), current_date - 345,'SUBMITTED',
   'CONFIRM', now() - interval '346 days', null, null, null, pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('PROBATION', pg_temp.form('Probation'), pg_temp.emp('EMP102'),
   (select id from hr.contract where employee_id = pg_temp.emp('EMP112') and is_current), current_date + 15,'SUBMITTED',
   'END', now() - interval '2 days','REJECTED', pg_temp.emp('EMP103'), now() - interval '1 day',
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103'));

insert into hr.review (review_type, form_id, reviewer_id, assignment_id, due_date, status, outcome, submitted_at,
  created_by, updated_by)
select 'PROJECT', pg_temp.form('Standard'), pg_temp.emp('EMP114'), pa.id, current_date - 50, 'SUBMITTED', 'DELIVERED',
       now() - interval '52 days', pg_temp.emp('EMP114'), pg_temp.emp('EMP114')
from hr.project_assignment pa
where pa.project_id = pg_temp.proj('PRJ-104') and pa.employee_id = pg_temp.emp('EMP105');

-- ── PIPs + PIP check-ins ────────────────────────────────────────────────────────
-- Mila: historic PIP, passed (closed). Ahmed: active PIP sourced from his 2025 manager review, disputed then started.
insert into hr.pip (employee_id, manager_id, reason, proposed_start_date, start_date, checkin_days, duration_days,
  extension_days, status, hr_goals_measurable, hr_support_documented, hr_informal_conversation, notice_sent_at,
  dispute_reason, disputed_at, acknowledged_at, outcome, closed_at, created_by, updated_by)
values
  (pg_temp.emp('EMP108'), pg_temp.emp('EMP101'),'Missed design hand-off deadlines on three consecutive sprints.',
   current_date - 220, current_date - 218, 14, 60, 30,'CLOSED', true, true, true, now() - interval '225 days',
   null, null, now() - interval '222 days','PASSED', now() - interval '155 days', pg_temp.emp('EMP101'), pg_temp.emp('EMP103'));

insert into hr.pip (employee_id, manager_id, source_review_id, reason, proposed_start_date, start_date, checkin_days,
  duration_days, extension_days, status, hr_goals_measurable, hr_support_documented, hr_informal_conversation,
  notice_sent_at, dispute_reason, disputed_at, acknowledged_at, created_by, updated_by)
select pg_temp.emp('EMP109'), pg_temp.emp('EMP102'), r.id,
       'Regression coverage below 60% and critical defects escaping to acceptance (client feedback PRJ-103).',
       current_date - 32, current_date - 28, 14, 60, 30,'ACTIVE', true, true, true, now() - interval '33 days',
       'Test environment was unavailable for two of the sprints in question.', now() - interval '31 days',
       now() - interval '29 days', pg_temp.emp('EMP102'), pg_temp.emp('EMP103')
from hr.review r join hr.appraisal a on a.id = r.appraisal_id
where r.review_type = 'MANAGER' and a.employee_id = pg_temp.emp('EMP109');

insert into hr.checkin (checkin_type, pip_id, seq_no, notes, employee_comment, outcome, done_by, done_at,
  hr_signoff_by, hr_signoff_at, created_by, updated_by)
select v.ct, p.id, v.seq, v.notes, v.ec, v.oc, pg_temp.emp(v.by), now() - (v.d || ' days')::interval,
       case when v.ct = 'PIP_FINAL' then pg_temp.emp('EMP103') end,
       case when v.ct = 'PIP_FINAL' then now() - ((v.d - 1) || ' days')::interval end,
       pg_temp.emp(v.by), pg_temp.emp(v.by)
from hr.pip p
join (values
  ('EMP108','PIP_CHECKIN',1,'Hand-offs on time this sprint.','Clearer specs helped.',null,'EMP101',204),
  ('EMP108','PIP_CHECKIN',2,'Consistent delivery.','Happy with progress.',null,'EMP101',190),
  ('EMP108','PIP_FINAL',1,'All goals met; PIP passed.','Thank you.','PASSED','EMP101',158),
  ('EMP109','PIP_CHECKIN',1,'Coverage up to 68%; one escaped defect.','Need access to staging data.',null,'EMP102',14)
) as v(e, ct, seq, notes, ec, oc, by, d) on p.employee_id = pg_temp.emp(v.e);

-- ── Manager changes ─────────────────────────────────────────────────────────────
insert into hr.manager_change (scope, employee_id, project_id, parent_id, from_manager_id, to_manager_id,
  effective_date, reason, move_open_tasks, status, applied_at, requested_by, created_by, updated_by)
values
  ('LINE', pg_temp.emp('EMP108'), null, null, pg_temp.emp('EMP102'), pg_temp.emp('EMP101'), current_date - 120,
   'Moved to Design under Sophie after department split.', true,'APPLIED', now() - interval '120 days',
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('LINE', pg_temp.emp('EMP112'), null, null, pg_temp.emp('EMP102'), pg_temp.emp('EMP114'), current_date + 14,
   'Ravi moves to the backend chapter led by Kenji.', true,'SCHEDULED', null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103'), pg_temp.emp('EMP103')),
  ('PROJECT', null, pg_temp.proj('PRJ-103'), null, pg_temp.emp('EMP102'), pg_temp.emp('EMP114'), current_date + 7,
   'Daniel on parental leave; Kenji covers PRJ-103.', false,'SCHEDULED', null,
   pg_temp.emp('EMP101'), pg_temp.emp('EMP101'), pg_temp.emp('EMP101')),
  ('LINE', pg_temp.emp('EMP109'), null, null, pg_temp.emp('EMP102'), pg_temp.emp('EMP101'), current_date - 5,
   'Cancelled: keep current manager during PIP.', true,'CANCELLED', null,
   pg_temp.emp('EMP103'), pg_temp.emp('EMP103'), pg_temp.emp('EMP103'));

-- ── Notifications ───────────────────────────────────────────────────────────────
insert into hr.notification (recipient_id, title, message, link_route, employee_id, read_at, created_at, created_by, updated_by)
select pg_temp.emp(r), t, m, l, pg_temp.emp(e), case when rd then now() - interval '1 day' end,
       now() - (d || ' hours')::interval, null, null
from (values
  ('EMP104','IT request: Noah Bakker','Prepare a laptop and accounts for Noah Bakker (start in 5 days ago).','/it/equipment-requests/','EMP107',true,240),
  ('EMP101','New team member: Noah Bakker','Noah joins your team as Developer.','/team','EMP107',false,230),
  ('EMP103','Resignation received: Sara Lindqvist','Sara submitted her resignation. Review and approve the last working day.','/employees/','EMP110',true,240),
  ('EMP102','PIP disputed: Ahmed El Amrani','Ahmed disputed the PIP notice. HR review needed.','/employees/','EMP109',false,740),
  ('EMP105','Peer review requested','Please review Noah Bakker''s mid-year self assessment.','/my-tasks','EMP105',false,100),
  ('EMP110','Return your equipment','2 items must be returned before your last day.','/my-tasks','EMP110',false,48),
  ('EMP103','Probation outcome: Ravi Patel','Manager proposed to end probation — HR decision recorded.','/employees/','EMP112',true,30),
  ('EMP101','Development plan submitted','Mila Smit submitted her 2026 H2 development plan.','/team','EMP108',false,72)
) as v(r, t, m, l, e, rd, d);

-- Point employee links at the real employee ids.
update hr.notification n set link_route = n.link_route || n.employee_id
where n.link_route in ('/employees/','/it/equipment-requests/') and n.recipient_id in (select id from hr.employee where employee_code like 'EMP1__');

-- ── Email outbox (SENT / FAILED only: PENDING rows would be sent by the live job) ─
insert into hr.email_outbox (template_code, to_address, cc, subject, body, status, attempts, sent_at, employee_id,
  created_by, updated_by)
values
  ('WELCOME_NEW_EMPLOYEE','noah.bakker@gmail.example','hr@cubeworks.local','Welcome to CubeWorks, Noah!',
   E'Hi Noah,\n\nWelcome aboard! Your first day details are attached.\n\nHR team','SENT',1, now() - interval '9 days', pg_temp.emp('EMP107'), null, null),
  ('IT_EQUIPMENT_REQUEST','tom.becker@cubeworks.local',null,'Equipment request: Noah Bakker',
   E'Hi Tom,\n\nPlease prepare: Lenovo ThinkPad X1 Carbon Gen 12, ship home.\n','SENT',1, now() - interval '10 days', pg_temp.emp('EMP107'), null, null),
  ('RESIGNATION_RECEIVED','priya.raman@cubeworks.local','daniel.okafor@cubeworks.local','Resignation received: Sara Lindqvist',
   E'Sara Lindqvist has submitted her resignation.\n','SENT',1, now() - interval '10 days', pg_temp.emp('EMP110'), null, null),
  ('PIP_NOTICE','ahmed.elamrani@cubeworks.local','priya.raman@cubeworks.local','Performance improvement plan',
   E'Hi Ahmed,\n\nPlease find your PIP details in the HR portal.\n','SENT',1, now() - interval '33 days', pg_temp.emp('EMP109'), null, null),
  ('SET_PASSWORD','fatima.haddad@gmail.example',null,'Set your CubeWorks password',
   E'Hi Fatima,\n\nUse the link below to set your password.\n','FAILED',3, null, pg_temp.emp('EMP115'), null, null),
  ('TASK_OVERDUE','priya.raman@cubeworks.local',null,'Overdue: Exit interview',
   E'Hi Priya,\n\nThe task "Exit interview" is now overdue.\n','SENT',1, now() - interval '1 day', pg_temp.emp('EMP110'), null, null);

-- ── Audit log ───────────────────────────────────────────────────────────────────
insert into hr.audit_log (employee_id, entity_type, entity_id, action, changed_by, changed_at, before_value,
  after_value, details, created_by, updated_by)
values
  (pg_temp.emp('EMP107'),'employee', pg_temp.emp('EMP107'),'CREATE', pg_temp.emp('EMP103'), now() - interval '12 days',
   null, jsonb_build_object('employeeCode','EMP107','stage','ONBOARDING'),'Onboarding started by HR', pg_temp.emp('EMP103'), null),
  (pg_temp.emp('EMP108'),'employee', pg_temp.emp('EMP108'),'MANAGER_CHANGE', pg_temp.emp('EMP103'), now() - interval '120 days',
   jsonb_build_object('managerId', pg_temp.emp('EMP102')), jsonb_build_object('managerId', pg_temp.emp('EMP101')),
   'Line manager transfer applied', pg_temp.emp('EMP103'), null),
  (pg_temp.emp('EMP109'),'pip', (select id from hr.pip where employee_id = pg_temp.emp('EMP109')),'STATUS_CHANGE', pg_temp.emp('EMP103'), now() - interval '28 days',
   jsonb_build_object('status','DISPUTED'), jsonb_build_object('status','ACTIVE'),'Dispute reviewed; PIP started', pg_temp.emp('EMP103'), null),
  (pg_temp.emp('EMP113'),'employee', pg_temp.emp('EMP113'),'ARCHIVE', null, now() - interval '40 days',
   jsonb_build_object('stage','OFFBOARDING'), jsonb_build_object('stage','ARCHIVED'),'Archived by system after last working day', null, null),
  (null,'setting', null,'UPDATE', pg_temp.emp('EMP103'), now() - interval '60 days',
   jsonb_build_object('code','UNDO_WINDOW_SECONDS','valueInt',10), jsonb_build_object('code','UNDO_WINDOW_SECONDS','valueInt',15),
   'Undo window lengthened', pg_temp.emp('EMP103'), null),
  (pg_temp.emp('EMP105'),'appraisal', (select a.id from hr.appraisal a join hr.appraisal_round r on r.id = a.round_id
                                        where r.name = 'Annual review 2025' and a.employee_id = pg_temp.emp('EMP105')),
   'PROMOTION_APPLIED', pg_temp.emp('EMP103'), now() - interval '200 days',
   jsonb_build_object('grade','JUNIOR'), jsonb_build_object('grade','MEDIOR'),'Promotion from annual review 2025', pg_temp.emp('EMP103'), null);

commit;

-- Summary
select 'employees' as what, count(*) from hr.employee where employee_code like 'EMP1__'
union all select 'documents', count(*) from hr.document where storage_key like 'seed-doc-%'
union all select 'contracts', count(*) from hr.contract
union all select 'checklist items', count(*) from hr.checklist_item
union all select 'assets', count(*) from hr.asset
union all select 'projects', count(*) from hr.project
union all select 'assignments', count(*) from hr.project_assignment
union all select 'reviews', count(*) from hr.review
union all select 'pips', count(*) from hr.pip
union all select 'checkins', count(*) from hr.checkin;
