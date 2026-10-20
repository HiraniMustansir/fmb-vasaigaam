-- User-selected access model: any password account may complete profile setup and join the single committee.
-- Deploy AFTER migrations 001/002, before disabling Confirm email. No invitation/bootstrap code is used.
-- Preserve ledger/workspace data and existing memberships. Retire unused legacy authorizations.
update public.bootstrap_authorizations set expires_at=least(expires_at,now()) where claimed_at is null;
update public.invitations set expires_at=least(expires_at,now()) where accepted_at is null;

-- Keep the original operational transaction implementation private. Its auth.uid()/membership checks still run.
alter function public.execute_command(text,jsonb,uuid) rename to execute_operational_command;
revoke all on function public.execute_operational_command(text,jsonb,uuid) from public,anon,authenticated;

create function public.execute_command(p_kind text,p_data jsonb,p_key uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare u uuid:=auth.uid(); w uuid; member_name text; member_position text; begin
 if p_kind in ('bootstrap','invite','join') then raise exception 'Use account registration and profile setup'; end if;
 if p_kind<>'onboarding' then return public.execute_operational_command(p_kind,p_data,p_key); end if;
 if not exists(select 1 from auth.users where id=u and email_confirmed_at is not null) then raise exception 'Authenticated account required'; end if;
 member_name:=trim(coalesce(p_data->>'name',''));
 member_position:=trim(coalesce(p_data->>'position',''));
 if length(member_name) not between 1 and 100 or length(member_position) not between 1 and 100 then raise exception 'Enter your name and committee position'; end if;
 -- One registration can create the initial workspace; concurrent members join that same workspace.
 perform pg_catalog.pg_advisory_xact_lock(1846082026);
 select workspace_id into w from public.memberships where user_id=u and active order by workspace_id limit 1;
 if w is not null then return jsonb_build_object('id',w,'posted',true); end if;
 if exists(select 1 from public.memberships where user_id=u and not active) then raise exception 'Workspace access revoked'; end if;
 if (select count(*) from public.workspaces)>1 then raise exception 'Multiple workspaces require project administrator configuration'; end if;
 select id into w from public.workspaces order by created_at,id limit 1;
 if w is null then
  insert into public.workspaces(name,owner_id) values('FMB Vasaigaam',u) returning id into w;
  insert into public.accounts values(w,'cash','Cash'),(w,'bank','Bank'),(w,'inventory','Inventory'),(w,'purchases','Purchases awaiting receipt'),(w,'payable','Supplier payable'),(w,'expense','Operating expense'),(w,'food','Ingredient consumption'),(w,'equity','Opening balance'),(w,'income','Contributions'),(w,'advances','Member advances'),(w,'reimbursements','Member reimbursements payable');
 end if;
 insert into public.memberships(workspace_id,user_id,name,position,active) values(w,u,member_name,member_position,true);
 insert into public.audit_events(workspace_id,actor,kind,payload) values(w,u,'onboarding',jsonb_build_object('name',member_name,'position',member_position));
 return jsonb_build_object('id',w,'posted',true);
end $$;
revoke all on function public.execute_command(text,jsonb,uuid) from public,anon;
grant execute on function public.execute_command(text,jsonb,uuid) to authenticated;

create or replace function public.workspace_snapshot() returns jsonb language plpgsql security definer set search_path='' as $$
declare w uuid; output jsonb; t text; a jsonb; v_email text; begin
 select lower(u.email) into v_email from auth.users u where u.id=auth.uid() and u.email_confirmed_at is not null;
 if v_email is null then raise exception 'Authenticated account required'; end if;
 select m.workspace_id into w from public.memberships m where m.user_id=auth.uid() and m.active order by m.workspace_id limit 1;
 if w is null then
  return jsonb_build_object('needs_onboarding',not exists(select 1 from public.memberships where user_id=auth.uid() and not active),'access_revoked',exists(select 1 from public.memberships where user_id=auth.uid() and not active));
 end if;
 output:=jsonb_build_object('workspace',(select to_jsonb(ws) from public.workspaces ws where ws.id=w));
 foreach t in array array['memberships','items','recipes','recipe_ingredients','recipe_versions','services','service_dishes','suppliers','invoices','invoice_lines','stock_movements','stock_allocations','reservations','accounts','journal_entries','journal_lines','production_runs','calendar_mappings','tasks','notes','attachments'] loop
  execute format('select coalesce(jsonb_agg(to_jsonb(r)),''[]'') from public.%I r where workspace_id=$1',t) into a using w;
  output:=output||jsonb_build_object(t,a);
 end loop;
 select coalesce(jsonb_agg(to_jsonb(r)),'[]') into a from (select * from public.audit_events where workspace_id=w order by created_at desc limit 60) r;
 output:=output||jsonb_build_object('audit_events',a);
 select coalesce(jsonb_agg(to_jsonb(d)),'[]') into a from public.ingredient_demand d where workspace_id=w;
 output:=output||jsonb_build_object('demand',a);
 return output;
end $$;
revoke all on function public.workspace_snapshot() from public,anon;
grant execute on function public.workspace_snapshot() to authenticated;
