\set ON_ERROR_STOP on
begin;
create schema calendar_tests;
create function calendar_tests.assert(ok boolean,message text) returns void language plpgsql as $$begin if ok is not true then raise exception 'FAIL: %',message;end if;raise notice 'PASS: %',message;end$$;
insert into auth.users values('00000000-0000-0000-0000-000000000001','calendar-owner@example.test',now()),('00000000-0000-0000-0000-000000000002','calendar-member@example.test',now());
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
do $$declare w uuid; d jsonb; failed boolean; begin
 w:=(public.execute_command('onboarding','{"name":"Calendar owner","position":"Secretary"}',gen_random_uuid())->>'id')::uuid;
 d:=jsonb_build_object('workspace_id',w,'gregorian','2026-02-17','hijri','1 Ramadan al-Muazzam 1447 AH','source','https://dawoodibohracalendar.in/','verified',false);
 perform public.execute_command('calendar',d,gen_random_uuid());
 perform calendar_tests.assert((select version=1 and not verified from public.calendar_mappings where workspace_id=w),'Source calendar dates can be saved without falsely marking them verified');
 perform public.execute_command('calendar',d||'{"version":1,"verified":true,"source":"Reviewed committee register"}',gen_random_uuid());
 perform calendar_tests.assert((select version=2 and verified and source='Reviewed committee register' from public.calendar_mappings where workspace_id=w),'Reviewed correction preserves source and increments its version');
 failed:=false;begin perform public.execute_command('calendar',d||'{"version":1}',gen_random_uuid());exception when others then failed:=true;end;
 perform calendar_tests.assert(failed,'A stale calendar correction cannot silently overwrite a reviewed date');
 perform calendar_tests.assert((select count(*)=2 from public.audit_events where workspace_id=w and kind='calendar'),'Calendar change history survives corrections and rejected conflicts');
 perform set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
 perform public.execute_command('onboarding','{"name":"Other member","position":"Secretary"}',gen_random_uuid());
 failed:=false;begin perform public.execute_command('calendar',d||'{"version":2}',gen_random_uuid());exception when others then failed:=true;end;
 perform calendar_tests.assert(failed,'A descriptive Secretary label does not grant date-verification administration');
 perform calendar_tests.assert(public.workspace_snapshot()->'calendar_mappings'->0->>'source'='Reviewed committee register','Active members read the same reviewed calendar correction');
end$$;
rollback;
