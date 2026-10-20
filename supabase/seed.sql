-- Opt-in development data only. Requires a pre-existing password Auth user with confirmation disabled.
begin;
do $$
declare u uuid; w uuid; item uuid; recipe uuid; begin
 if coalesce(current_setting('fmb.allow_demo_seed',true),'')<>'yes' then raise exception 'Development seed disabled. Explicitly set fmb.allow_demo_seed=yes on a separate development database'; end if;
 select id into u from auth.users where email='fmb-owner@example.test' and email_confirmed_at is not null;
 if u is null then raise exception 'Create development password Auth user fmb-owner@example.test first'; end if;
 if exists(select 1 from public.workspaces) then raise exception 'Seed requires an empty development workspace'; end if;
 perform set_config('request.jwt.claim.sub',u::text,true);
 w:=(public.execute_command('onboarding','{"name":"DEMO Secretary","position":"Secretary"}',gen_random_uuid())->>'id')::uuid;
 update public.workspaces set name='FMB Vasaigaam • DEMO',settings='{"default_tiffins":100,"people_per_thaal":8,"kitchen_name":"Demonstration kitchen"}' where id=w;
 item:=(public.execute_command('item',jsonb_build_object('workspace_id',w,'name','Rice (DEMO)','unit','kg','minimum',10),gen_random_uuid())->>'id')::uuid;
 perform public.execute_command('stock',jsonb_build_object('workspace_id',w,'item_id',item,'kind','opening','quantity',50,'unit_cost',60,'reason','Development opening stock'),gen_random_uuid());
 perform public.execute_command('supplier',jsonb_build_object('workspace_id',w,'name','Demonstration supplier'),gen_random_uuid());
 recipe:=(public.execute_command('recipe',jsonb_build_object('workspace_id',w,'name','Rice meal (DEMO)','base_yield',100,'basis','servings','ingredients',jsonb_build_array(jsonb_build_object('item_id',item,'quantity',10))),gen_random_uuid())->>'id')::uuid;
 perform public.execute_command('service',jsonb_build_object('workspace_id',w,'title','23-thaal demonstration Jaman','service_date',current_date+1,'kind','gathering','planned',23,'buffer',10,'people_per_thaal',8,'recipe_ids',jsonb_build_array(recipe)),gen_random_uuid());
 perform public.execute_command('money',jsonb_build_object('workspace_id',w,'kind','opening','account','cash','amount',10000,'description','Development opening balance'),gen_random_uuid());
end $$;
commit;
