-- DEV-ONLY: employee rows for the Keycloak demo logins (hr1, manager1, employee1, itadmin1).
-- Without them every login loops back to the login page. Run once on a fresh database.
-- Safe to re-run.

insert into hr.employee (employee_code, first_name, last_name, private_email, mobile_phone,
                         date_of_birth, street, postcode, city, job_title_id, department_id,
                         grade, app_role, idp_subject, account_status, stage)
values
  ('EMP001', 'Hannah', 'Ross',   'hr1@example.com',       '+31600000001', '1985-03-01', 'Main St 1', '1000AA', 'Amsterdam', 1, 1, 'SENIOR', 'HR',       '00000000-0000-4000-8000-000000000001', 'ACTIVE', 'ACTIVE'),
  ('EMP002', 'Mark',   'Mills',  'manager1@example.com',  '+31600000002', '1982-06-15', 'Main St 2', '1000AA', 'Amsterdam', 1, 1, 'SENIOR', 'MANAGER',  '00000000-0000-4000-8000-000000000002', 'ACTIVE', 'ACTIVE'),
  ('EMP003', 'Eva',    'Jansen', 'employee1@example.com', '+31600000003', '1995-09-20', 'Main St 3', '1000AA', 'Amsterdam', 1, 1, 'JUNIOR', 'EMPLOYEE', '00000000-0000-4000-8000-000000000003', 'ACTIVE', 'ACTIVE'),
  ('EMP004', 'Ivan',   'Admin',  'itadmin1@example.com',  '+31600000004', '1990-01-10', 'Main St 4', '1000AA', 'Amsterdam', 1, 1, 'MEDIOR', 'IT_ADMIN', '00000000-0000-4000-8000-000000000004', 'ACTIVE', 'ACTIVE')
on conflict do nothing;
