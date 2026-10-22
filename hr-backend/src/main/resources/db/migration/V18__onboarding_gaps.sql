-- ============================================================
-- V18 — onboarding gap fixes (F01 spec §12 D-24): checklist_template_item.item_kind
--
-- Data-only migration, no schema change. V2 seeded every template item as GENERIC
-- ("item_kind defaults to 'GENERIC' for every item"), so nothing in a seeded checklist
-- behaved as a policy acknowledgement, profile task, equipment confirmation, equipment
-- or account item. This sets item_kind only where the match is unambiguous:
--
--   1. IT-owned items, by section (FRD-02 §4.2: "checklist_item (EQUIPMENT and ACCOUNT,
--      owner IT_ADMIN)"): EQUIPMENT_ISSUED → EQUIPMENT, ACCOUNTS_INSTALLATIONS → ACCOUNT.
--   2. Employee-owned items, by name (FRD-02 §4.5): a name mentioning a policy → POLICY_ACK;
--      a name mentioning the profile → PROFILE; a name mentioning equipment together with
--      confirm/receive → EQUIPMENT_CONFIRM.
--
-- Assumptions (recorded, not guessed silently):
--   - V2 itself seeds NO employee-owned items: "Policies acknowledged" and "Profile complete"
--     are HR-owned tracking items (owner_role = 'HR'). They are left GENERIC — making them
--     POLICY_ACK/PROFILE would also require changing their owner to EMPLOYEE, which is a
--     business decision, not a data fix. Rule 2 therefore only affects employee-owned items
--     HR has added since through Settings (FRD-08), and HR can now set item_kind there.
--   - Only rows still GENERIC are touched, so a kind HR already chose is never overwritten.
--   - checklist_item rows already instantiated for running onboardings are NOT changed:
--     item_kind is a snapshot taken when the checklist is created (ADM-04).
--   - No policy_document_id is set: no POLICY document is seeded; HR links one per
--     POLICY_ACK item in Settings (D-07).
-- ============================================================

update checklist_template_item
set    item_kind = 'EQUIPMENT', updated_at = now(), version = version + 1
where  owner_role = 'IT_ADMIN'
  and  section = 'EQUIPMENT_ISSUED'
  and  item_kind = 'GENERIC';

update checklist_template_item
set    item_kind = 'ACCOUNT', updated_at = now(), version = version + 1
where  owner_role = 'IT_ADMIN'
  and  section = 'ACCOUNTS_INSTALLATIONS'
  and  item_kind = 'GENERIC';

update checklist_template_item
set    item_kind = 'POLICY_ACK', updated_at = now(), version = version + 1
where  owner_role = 'EMPLOYEE'
  and  item_kind = 'GENERIC'
  and  lower(name) like '%polic%';

update checklist_template_item
set    item_kind = 'PROFILE', updated_at = now(), version = version + 1
where  owner_role = 'EMPLOYEE'
  and  item_kind = 'GENERIC'
  and  lower(name) like '%profile%';

update checklist_template_item
set    item_kind = 'EQUIPMENT_CONFIRM', updated_at = now(), version = version + 1
where  owner_role = 'EMPLOYEE'
  and  item_kind = 'GENERIC'
  and  lower(name) like '%equipment%'
  and  (lower(name) like '%confirm%' or lower(name) like '%receiv%');
