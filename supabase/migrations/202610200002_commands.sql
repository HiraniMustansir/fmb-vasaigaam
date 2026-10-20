create function public.post_pair(w uuid,k text,description text,amount numeric,dr text,cr text,invoice uuid default null,supplier uuid default null,member uuid default null) returns uuid
language plpgsql security definer set search_path='' as $$
declare e uuid; begin
 if amount<=0 then raise exception 'Amount must be positive'; end if;
 insert into public.journal_entries(workspace_id,kind,description,invoice_id,supplier_id,member_id,created_by)
 values(w,k,description,invoice,supplier,member,auth.uid()) returning id into e;
 insert into public.journal_lines(workspace_id,entry_id,account_code,debit,credit) values(w,e,dr,round(amount,2),0),(w,e,cr,0,round(amount,2)); return e;
end $$;
revoke all on function public.post_pair(uuid,text,text,numeric,text,text,uuid,uuid,uuid) from public,anon,authenticated;
-- FEFO allocation determines physical lots; valuation uses a moving weighted average.
create function public.allocate_stock(w uuid,item uuid,issue uuid,qty numeric,invoice_line uuid default null) returns void
language plpgsql security definer set search_path='' as $$
declare lot record; remaining numeric:=qty; take numeric; begin
 for lot in
  select sm.id,sm.quantity-coalesce((select sum(sa.quantity) from public.stock_allocations sa where sa.receipt_id=sm.id),0) available
  from public.stock_movements sm where sm.workspace_id=w and sm.item_id=item and sm.quantity>0
  and (invoice_line is null or sm.invoice_line_id=invoice_line)
  order by sm.expiry asc nulls last,sm.created_at,sm.id
 loop
  take:=least(remaining,lot.available);
  if take>0 then insert into public.stock_allocations(workspace_id,issue_id,receipt_id,quantity) values(w,issue,lot.id,take); remaining:=remaining-take; end if;
  exit when remaining=0;
 end loop;
 if remaining>0 then raise exception 'Insufficient stock in eligible batches'; end if;
end $$;
revoke all on function public.allocate_stock(uuid,uuid,uuid,numeric,uuid) from public,anon,authenticated;
create function public.execute_command(p_kind text,p_data jsonb,p_key uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare
 u uuid:=auth.uid(); w uuid; v_id uuid; old public.command_results; b public.bootstrap_authorizations;
 v_email text; x jsonb; q numeric; c numeric; total numeric; dr text; cr text; ref uuid; line public.invoice_lines;
 service public.services; item public.items; movement public.stock_movements; v integer; result jsonb; name text;
begin
 select lower(a.email) into v_email from auth.users a where a.id=u and a.email_confirmed_at is not null;
 if v_email is null then raise exception 'Verified email required'; end if;
 if p_kind='bootstrap' then
  select * into b from public.bootstrap_authorizations where slot for update;
  if not found or b.claimed_at is not null or b.expires_at<now() or lower(b.email)<>v_email or
    b.token_hash<>coalesce(encode(extensions.digest(p_data->>'token','sha256'),'hex'),'') then raise exception 'Bootstrap authorization invalid or expired'; end if;
  if exists(select 1 from public.workspaces) then raise exception 'Workspace already claimed'; end if;
  insert into public.workspaces(name,owner_id) values('FMB Vasaigaam',u) returning public.workspaces.id into w;
  insert into public.memberships values(w,u,p_data->>'name','Secretary',true);
  insert into public.accounts values(w,'cash','Cash'),(w,'bank','Bank'),(w,'inventory','Inventory'),(w,'purchases','Purchases awaiting receipt'),(w,'payable','Supplier payable'),(w,'expense','Operating expense'),(w,'food','Ingredient consumption'),(w,'equity','Opening balance'),(w,'income','Contributions'),(w,'advances','Member advances'),(w,'reimbursements','Member reimbursements payable');
  update public.bootstrap_authorizations set claimed_at=now() where slot;
  insert into public.audit_events(workspace_id,actor,kind,payload) values(w,u,p_kind,jsonb_build_object('name',p_data->>'name'));
  return jsonb_build_object('id',w);
 elsif p_kind='join' then
  select i.workspace_id into w from public.invitations i where i.id=(p_data->>'invitation_id')::uuid and lower(i.email)=v_email and i.expires_at>now() and i.accepted_at is null for update;
  if w is null then raise exception 'Invitation invalid or expired'; end if;
  insert into public.memberships values(w,u,p_data->>'name',p_data->>'position',true) on conflict(workspace_id,user_id) do update set active=true,name=excluded.name,position=excluded.position;
  update public.invitations set accepted_at=now() where id=(p_data->>'invitation_id')::uuid;
  insert into public.audit_events(workspace_id,actor,kind,payload) values(w,u,'join',jsonb_build_object('name',p_data->>'name'));
  return jsonb_build_object('id',w);
 end if;
 w:=(p_data->>'workspace_id')::uuid;
 -- Serialize mutations within this small committee workspace. Membership is checked after acquiring lock.
 perform 1 from public.workspaces where public.workspaces.id=w for update;
 if not public.is_member(w) then raise exception 'Workspace access revoked or unavailable'; end if;
 select * into old from public.command_results where workspace_id=w and key=p_key;
 if found then
  if old.actor<>u or old.kind<>p_kind or old.payload<>p_data then raise exception 'Idempotency key reused with different data'; end if;
  return old.result;
 end if;
 v_id:=nullif(p_data->>'id','')::uuid;
 if p_kind='invite' and coalesce(p_data->>'email','') !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$' then raise exception 'Valid email required'; end if;
 if p_kind in ('invite','revoke','transfer','settings','calendar') and not public.is_owner(w) then raise exception 'Workspace owner required'; end if;
 case p_kind
 when 'invite' then
  insert into public.invitations(workspace_id,email) values(w,lower(trim(p_data->>'email')))
  on conflict(workspace_id,email) do update set expires_at=now()+interval '7 days',accepted_at=null returning public.invitations.id into v_id;
 when 'revoke' then
  if (p_data->>'user_id')::uuid=(select owner_id from public.workspaces where public.workspaces.id=w) then raise exception 'Transfer ownership first'; end if;
  update public.memberships set active=false where workspace_id=w and user_id=(p_data->>'user_id')::uuid;
  update public.invitations set expires_at=now() where workspace_id=w and lower(public.invitations.email)=(select lower(a.email) from auth.users a where a.id=(p_data->>'user_id')::uuid);
 when 'transfer' then
  if not exists(select 1 from public.memberships where workspace_id=w and user_id=(p_data->>'user_id')::uuid and active) then raise exception 'Active member required'; end if;
  update public.workspaces set owner_id=(p_data->>'user_id')::uuid where public.workspaces.id=w;
 when 'settings' then
  if coalesce((p_data->'settings'->>'default_tiffins')::numeric,-1)<0 or coalesce((p_data->'settings'->>'people_per_thaal')::integer,0)<=0 then raise exception 'Valid tiffin and thaal defaults required'; end if;
  update public.workspaces set settings=p_data->'settings' where public.workspaces.id=w;
 when 'profile' then
  update public.memberships set name=p_data->>'name',position=p_data->>'position' where workspace_id=w and user_id=u;
 when 'item' then
  insert into public.items(workspace_id,name,unit,minimum,category,location) values(w,p_data->>'name',p_data->>'unit',coalesce((p_data->>'minimum')::numeric,0),coalesce(p_data->>'category','Ingredient'),coalesce(p_data->>'location','')) returning public.items.id into v_id;
 when 'supplier' then
  insert into public.suppliers(workspace_id,name,phone,notes) values(w,p_data->>'name',coalesce(p_data->>'phone',''),coalesce(p_data->>'notes','')) returning public.suppliers.id into v_id;
 when 'recipe' then
  if v_id is null then
   insert into public.recipes(workspace_id,name,base_yield,basis,instructions,allergens) values(w,p_data->>'name',(p_data->>'base_yield')::numeric,p_data->>'basis',coalesce(p_data->>'instructions',''),coalesce(p_data->>'allergens','')) returning public.recipes.id into v_id;
  else
   update public.recipes set name=p_data->>'name',base_yield=(p_data->>'base_yield')::numeric,basis=p_data->>'basis',instructions=coalesce(p_data->>'instructions',''),allergens=coalesce(p_data->>'allergens',''),version=version+1 where public.recipes.id=v_id and workspace_id=w and version=(p_data->>'version')::integer;
   if not found then raise exception 'Conflict: recipe changed. Refresh and review'; end if;
   if exists(select 1 from public.reservations rs join public.service_dishes sd on sd.service_id=rs.service_id where sd.recipe_id=v_id) then raise exception 'Release reservations before changing this recipe'; end if;
   delete from public.recipe_ingredients where recipe_id=v_id and workspace_id=w;
  end if;
  for x in select value from jsonb_array_elements(p_data->'ingredients') loop
   insert into public.recipe_ingredients values(w,v_id,(x->>'item_id')::uuid,(x->>'quantity')::numeric);
  end loop;
  insert into public.recipe_versions(workspace_id,recipe_id,version,snapshot) select w,v_id,r.version,p_data from public.recipes r where r.id=v_id;
 when 'service' then
  if v_id is null then
   insert into public.services(workspace_id,title,service_date,kind,planned,confirmed,buffer,people_per_thaal,venue,notes,budget)
   values(w,p_data->>'title',(p_data->>'service_date')::date,p_data->>'kind',(p_data->>'planned')::numeric,coalesce((p_data->>'confirmed')::numeric,0),(p_data->>'buffer')::numeric,(p_data->>'people_per_thaal')::integer,coalesce(p_data->>'venue',''),coalesce(p_data->>'notes',''),coalesce((p_data->>'budget')::numeric,0)) returning public.services.id into v_id;
  else
   select * into service from public.services where public.services.id=v_id and workspace_id=w;
   if service.status in ('cooking','completed','cancelled') then raise exception 'Finalized or cooking service cannot be edited'; end if;
   if exists(select 1 from public.reservations where service_id=v_id) then raise exception 'Release reservations before editing the menu'; end if;
   update public.services set kind=p_data->>'kind',venue=coalesce(p_data->>'venue',''),budget=coalesce((p_data->>'budget')::numeric,0),title=p_data->>'title',service_date=(p_data->>'service_date')::date,planned=(p_data->>'planned')::numeric,confirmed=(p_data->>'confirmed')::numeric,buffer=(p_data->>'buffer')::numeric,people_per_thaal=(p_data->>'people_per_thaal')::integer,notes=coalesce(p_data->>'notes',''),version=version+1 where public.services.id=v_id and workspace_id=w and version=(p_data->>'version')::integer;
   if not found then raise exception 'Conflict: service changed. Refresh and review'; end if;
   delete from public.service_dishes where service_id=v_id;
  end if;
  for x in select value from jsonb_array_elements(p_data->'recipe_ids') loop
   insert into public.service_dishes values(w,v_id,(x#>>'{}')::uuid);
  end loop;
 when 'status' then
  select * into service from public.services where public.services.id=v_id and workspace_id=w for update;
  if not found or service.version<>(p_data->>'version')::integer then raise exception 'Conflict: refresh service'; end if;
  if service.status in ('completed','cancelled') then raise exception 'Finalized service'; end if;
  if service.status='cooking' and p_data->>'status'<>'cancelled' then raise exception 'Complete cooking before changing this status'; end if;
  if p_data->>'status'='published' and service.kind<>'observance' and not exists(select 1 from public.service_dishes where service_id=v_id) then raise exception 'Add at least one dish before publishing'; end if;
  if p_data->>'status' not in ('published','confirmed','cancelled','draft') then raise exception 'Invalid transition'; end if;
  if p_data->>'status' in ('cancelled','draft') then delete from public.reservations where service_id=v_id; end if;
  update public.services set status=p_data->>'status',version=version+1 where public.services.id=v_id;
 when 'reserve' then
  select * into service from public.services where public.services.id=v_id and workspace_id=w;
  if not found or service.status in ('completed','cancelled') then raise exception 'Service unavailable'; end if;
  delete from public.reservations where service_id=v_id;
  for x in select to_jsonb(d) from public.ingredient_demand d where d.service_id=v_id loop
   q:=(x->>'required')::numeric;
   if q>(select coalesce(sum(quantity),0) from public.stock_movements where item_id=(x->>'item_id')::uuid) - (select coalesce(sum(quantity),0) from public.reservations where item_id=(x->>'item_id')::uuid) then raise exception 'Insufficient available stock'; end if;
   insert into public.reservations values(w,v_id,(x->>'item_id')::uuid,q);
  end loop;
 when 'invoice' then
  total:=0;
  for x in select value from jsonb_array_elements(p_data->'lines') loop total:=total+round((x->>'quantity')::numeric*(x->>'unit_cost')::numeric,2); end loop;
  insert into public.invoices(workspace_id,supplier_id,number,invoice_date,total) values(w,(p_data->>'supplier_id')::uuid,p_data->>'number',(p_data->>'invoice_date')::date,total) returning public.invoices.id into v_id;
  for x in select value from jsonb_array_elements(p_data->'lines') loop
   insert into public.invoice_lines(workspace_id,invoice_id,item_id,quantity,unit_cost) values(w,v_id,(x->>'item_id')::uuid,(x->>'quantity')::numeric,(x->>'unit_cost')::numeric);
  end loop;
  perform public.post_pair(w,'invoice','Supplier bill '||(p_data->>'number'),total,'purchases','payable',v_id,(p_data->>'supplier_id')::uuid);
 when 'stock' then
  select * into item from public.items where public.items.id=(p_data->>'item_id')::uuid and workspace_id=w for update;
  if not found then raise exception 'Item unavailable'; end if;
  q:=(p_data->>'quantity')::numeric; c:=coalesce((p_data->>'unit_cost')::numeric,0);
  if length(trim(coalesce(p_data->>'reason','')))<3 then raise exception 'A reason is required'; end if;
  if p_data->>'kind' in ('receipt','return') then
   select * into line from public.invoice_lines where public.invoice_lines.id=(p_data->>'invoice_line_id')::uuid and workspace_id=w;
   if not found or line.item_id<>item.id then raise exception 'Valid invoice line required'; end if;
   if p_data->>'kind'='receipt' and q<=0 or p_data->>'kind'='return' and q>=0 then raise exception 'Receipt must be positive; return must be negative'; end if;
   if p_data->>'kind'='return' and -q>(select coalesce(sum(quantity),0) from public.stock_movements where invoice_line_id=line.id) then raise exception 'Return exceeds received quantity'; end if;
   if q+(select coalesce(sum(quantity),0) from public.stock_movements where invoice_line_id=line.id)>line.quantity then raise exception 'Receipt exceeds remaining invoice quantity'; end if;
   c:=line.unit_cost;
  elsif p_data->>'kind' in ('opening','adjustment','waste','return') then
   if p_data->>'kind'='opening' and q<=0 then raise exception 'Opening stock must be positive'; end if;
   if p_data->>'kind' in ('waste','return') and q>=0 then raise exception 'Waste and return quantities must be negative'; end if;
  else raise exception 'Use production workflow for consumption'; end if;
  if q+(select coalesce(sum(quantity),0) from public.stock_movements where item_id=item.id)<(select coalesce(sum(quantity),0) from public.reservations where item_id=item.id) then raise exception 'Insufficient unreserved stock'; end if;
  insert into public.stock_movements(workspace_id,item_id,quantity,unit_cost,kind,invoice_line_id,batch,expiry,reason,created_by) values(w,item.id,q,c,p_data->>'kind',case when p_data->>'kind' in ('receipt','return') then line.id end,coalesce(p_data->>'batch',''),nullif(p_data->>'expiry','')::date,p_data->>'reason',u) returning public.stock_movements.id into v_id;
  if q<0 then perform public.allocate_stock(w,item.id,v_id,-q,case when p_data->>'kind'='return' then line.id end); end if;
  if round(abs(q)*c,2)>0 then
   if p_data->>'kind'='receipt' then dr:='inventory'; cr:='purchases';
   elsif q>0 then dr:='inventory'; cr:='equity';
   elsif p_data->>'kind'='return' then dr:='payable'; cr:='inventory';
   else dr:='expense'; cr:='inventory'; end if;
   ref:=public.post_pair(w,p_data->>'kind',p_data->>'reason',abs(q)*c,dr,cr,case when p_data->>'kind' in ('receipt','return') then line.invoice_id end,case when p_data->>'kind' in ('receipt','return') then (select supplier_id from public.invoices where public.invoices.id=line.invoice_id) end);
   update public.stock_movements set journal_entry_id=ref where id=v_id;
  end if;
 when 'reverse_stock' then
  select * into movement from public.stock_movements where id=v_id and workspace_id=w for update;
  if not found or movement.kind in ('consumption','reversal') then raise exception 'Select a reversible stock movement'; end if;
  if length(trim(coalesce(p_data->>'reason','')))<3 then raise exception 'Correction reason required'; end if;
  if -movement.quantity+(select coalesce(sum(quantity),0) from public.stock_movements where item_id=movement.item_id)<(select coalesce(sum(quantity),0) from public.reservations where item_id=movement.item_id) then raise exception 'Reversal would reduce available stock below zero'; end if;
  insert into public.stock_movements(workspace_id,item_id,quantity,unit_cost,kind,invoice_line_id,reverses,batch,expiry,reason,created_by)
  values(w,movement.item_id,-movement.quantity,movement.unit_cost,'reversal',movement.invoice_line_id,movement.id,movement.batch,movement.expiry,p_data->>'reason',u) returning id into v_id;
  if movement.quantity>0 then perform public.allocate_stock(w,movement.item_id,v_id,movement.quantity); end if;
  if movement.journal_entry_id is not null then
   insert into public.journal_entries(workspace_id,kind,description,invoice_id,supplier_id,reverses,created_by)
   select w,'reversal',p_data->>'reason',je.invoice_id,je.supplier_id,je.id,u from public.journal_entries je where je.id=movement.journal_entry_id returning id into ref;
   insert into public.journal_lines(workspace_id,entry_id,account_code,debit,credit) select w,ref,account_code,credit,debit from public.journal_lines where entry_id=movement.journal_entry_id;
   update public.stock_movements set journal_entry_id=ref where id=v_id;
  end if;
 when 'reconcile' then
  select * into item from public.items where id=(p_data->>'item_id')::uuid and workspace_id=w for update;
  if not found then raise exception 'Item unavailable'; end if;
  q:=(p_data->>'counted')::numeric;
  if q<0 or length(trim(coalesce(p_data->>'reason','')))<3 then raise exception 'Count must be nonnegative and include a reason'; end if;
  if q<(select coalesce(sum(quantity),0) from public.reservations where item_id=item.id) then raise exception 'Release reservations before reconciling below reserved quantity'; end if;
  select q-coalesce(sum(quantity),0),greatest(0,coalesce(sum(quantity*unit_cost)/nullif(sum(quantity),0),0)) into q,c from public.stock_movements where item_id=item.id;
  if q=0 then raise exception 'Count matches the ledger; no adjustment required'; end if;
  insert into public.stock_movements(workspace_id,item_id,quantity,unit_cost,kind,reason,created_by) values(w,item.id,q,c,'adjustment',p_data->>'reason',u) returning id into v_id;
  if q<0 then perform public.allocate_stock(w,item.id,v_id,-q); end if;
  if round(abs(q)*c,2)>0 then
   ref:=public.post_pair(w,'adjustment',p_data->>'reason',abs(q)*c,case when q>0 then 'inventory' else 'expense' end,case when q>0 then 'equity' else 'inventory' end);
   update public.stock_movements set journal_entry_id=ref where id=v_id;
  end if;
 when 'production' then
  select * into service from public.services where public.services.id=(p_data->>'service_id')::uuid and workspace_id=w for update;
  if not found or service.kind='observance' or (service.status='cancelled' or service.status='completed' and coalesce(p_data->>'amendment','false')<>'true') or service.version<>(p_data->>'version')::integer then raise exception 'Service changed, completed or unavailable'; end if;
  if coalesce(p_data->>'amendment','false')='true' and length(trim(coalesce(p_data->>'notes','')))<3 then raise exception 'Amendments require a reason'; end if;
  if jsonb_array_length(p_data->'consumption')=0 then raise exception 'Confirm actual ingredient consumption'; end if;
  delete from public.reservations where service_id=service.id;
  total:=0;
  for x in select value from jsonb_array_elements(p_data->'consumption') loop
   q:=(x->>'quantity')::numeric;
   select * into item from public.items where public.items.id=(x->>'item_id')::uuid and workspace_id=w for update;
   if not found or q<=0 then raise exception 'Invalid consumption'; end if;
   if q>(select coalesce(sum(quantity),0) from public.stock_movements where item_id=item.id)-(select coalesce(sum(quantity),0) from public.reservations where item_id=item.id) then raise exception 'Insufficient stock for %',item.name; end if;
   select greatest(0,coalesce(sum(quantity*unit_cost)/nullif(sum(quantity),0),0)) into c from public.stock_movements where item_id=item.id;
   insert into public.stock_movements(workspace_id,item_id,quantity,unit_cost,kind,service_id,reason,created_by) values(w,item.id,-q,c,'consumption',service.id,'Confirmed production',u) returning id into ref;
   perform public.allocate_stock(w,item.id,ref,q);
   total:=total+round(q*c,2);
  end loop;
  insert into public.production_runs(workspace_id,service_id,recipe_snapshot,prepared,distributed,leftovers,waste,redistribution,notes,created_by)
  values(w,service.id,(select coalesce(jsonb_agg(jsonb_build_object('recipe',to_jsonb(r),'ingredients',(select jsonb_agg(to_jsonb(ri)) from public.recipe_ingredients ri where ri.recipe_id=r.id))),'[]') from public.recipes r join public.service_dishes sd on sd.recipe_id=r.id where sd.service_id=service.id),(p_data->>'prepared')::numeric,(p_data->>'distributed')::numeric,(p_data->>'leftovers')::numeric,(p_data->>'waste')::numeric,coalesce((p_data->>'redistribution')::numeric,0),coalesce(p_data->>'notes',''),u) returning public.production_runs.id into v_id;
  update public.services set status=case when coalesce(p_data->>'complete','true')='true' then 'completed' else 'cooking' end,prepared=prepared+(p_data->>'prepared')::numeric,distributed=distributed+(p_data->>'distributed')::numeric,version=version+1 where public.services.id=service.id;
  if total>0 then perform public.post_pair(w,'consumption',service.title,total,'food','inventory'); end if;
 when 'payment' then
  select * into line from public.invoice_lines where false; -- reset nullable record
  ref:=(p_data->>'invoice_id')::uuid;
  select i.supplier_id into v_id from public.invoices i where i.id=ref and i.workspace_id=w;
  if not found then raise exception 'Invoice unavailable'; end if;
  q:=(p_data->>'amount')::numeric;
  select coalesce(sum(jl.credit-jl.debit),0) into total from public.journal_lines jl join public.journal_entries je on je.id=jl.entry_id where je.invoice_id=ref and jl.account_code='payable';
  if q>total then raise exception 'Payment exceeds outstanding invoice balance'; end if;
  cr:=p_data->>'account'; if cr not in ('cash','bank') then raise exception 'Select cash or bank'; end if;
  v_id:=public.post_pair(w,'payment','Supplier payment',q,'payable',cr,ref,v_id);
 when 'money' then
  q:=(p_data->>'amount')::numeric; cr:=p_data->>'account'; if cr not in ('cash','bank') then raise exception 'Select cash or bank'; end if;
  case p_data->>'kind'
   when 'expense' then dr:='expense';
   when 'advance' then dr:='advances';
   when 'settlement' then dr:='expense'; cr:='advances';
   when 'member_expense' then dr:='expense'; cr:='reimbursements';
   when 'reimbursement' then dr:='reimbursements';
   when 'contribution' then dr:=cr; cr:='income';
   when 'opening' then dr:=cr; cr:='equity';
   else raise exception 'Unsupported money record'; end case;
  if p_data->>'kind' in ('advance','settlement','member_expense','reimbursement') and nullif(p_data->>'member_id','') is null then raise exception 'Choose the member for this record'; end if;
  if p_data->>'kind' in ('settlement','reimbursement') then
   select coalesce(sum(case when p_data->>'kind'='settlement' then jl.debit-jl.credit else jl.credit-jl.debit end),0) into total from public.journal_lines jl join public.journal_entries je on je.id=jl.entry_id where je.workspace_id=w and je.member_id=(p_data->>'member_id')::uuid and jl.account_code=case when p_data->>'kind'='settlement' then 'advances' else 'reimbursements' end;
   if q>total then raise exception 'Amount exceeds unsettled member balance'; end if;
  end if;
  v_id:=public.post_pair(w,p_data->>'kind',p_data->>'description',q,dr,cr,null,null,nullif(p_data->>'member_id','')::uuid);
 when 'reverse_journal' then
  if length(trim(p_data->>'reason'))<3 then raise exception 'Correction reason required'; end if;
  select je.kind into name from public.journal_entries je where je.id=v_id and je.workspace_id=w;
  if name not in ('expense','advance','settlement','contribution','opening','payment','member_expense','reimbursement') then raise exception 'Reverse source stock or invoice workflow instead'; end if;
  insert into public.journal_entries(workspace_id,kind,description,invoice_id,supplier_id,member_id,reverses,created_by)
  select w,'reversal',p_data->>'reason',je.invoice_id,je.supplier_id,je.member_id,je.id,u from public.journal_entries je where je.id=v_id and je.workspace_id=w returning public.journal_entries.id into ref;
  insert into public.journal_lines(workspace_id,entry_id,account_code,debit,credit) select w,ref,account_code,credit,debit from public.journal_lines where entry_id=v_id;
  v_id:=ref;
 when 'calendar' then
  insert into public.calendar_mappings values(w,(p_data->>'gregorian')::date,p_data->>'hijri',p_data->>'source',(p_data->>'verified')::boolean,1)
  on conflict(workspace_id,gregorian) do update set hijri=excluded.hijri,source=excluded.source,verified=excluded.verified,version=public.calendar_mappings.version+1 where public.calendar_mappings.version=(p_data->>'version')::integer;
  if not found then raise exception 'Conflict: calendar mapping changed'; end if;
 when 'task' then
  if v_id is null then
   insert into public.tasks(workspace_id,title,due_date,assigned_to) values(w,p_data->>'title',(p_data->>'due_date')::date,nullif(p_data->>'assigned_to','')::uuid) returning public.tasks.id into v_id;
  else
   update public.tasks set done=(p_data->>'done')::boolean,version=version+1 where public.tasks.id=v_id and workspace_id=w and version=(p_data->>'version')::integer;
   if not found then raise exception 'Conflict: task changed'; end if;
  end if;
 when 'attachment' then
  if split_part(p_data->>'path','/',1)<>w::text or not exists(select 1 from storage.objects where bucket_id='committee-attachments' and name=p_data->>'path') then raise exception 'Upload workspace evidence before registering it'; end if;
  insert into public.attachments(workspace_id,path,subject,created_by) values(w,p_data->>'path',p_data->>'subject',u) returning id into v_id;
 when 'note' then
  insert into public.notes(workspace_id,subject,body,created_by) values(w,p_data->>'subject',p_data->>'body',u) returning public.notes.id into v_id;
 else raise exception 'Unsupported command: %',p_kind;
 end case;
 result:=jsonb_build_object('id',v_id,'posted',true);
 insert into public.audit_events(workspace_id,actor,kind,payload) values(w,u,p_kind,p_data);
 insert into public.command_results values(w,p_key,u,p_kind,p_data,result);
 return result;
end $$;
revoke all on function public.execute_command(text,jsonb,uuid) from public,anon;
grant execute on function public.execute_command(text,jsonb,uuid) to authenticated;

create function public.workspace_snapshot() returns jsonb language plpgsql security definer set search_path='' as $$
declare w uuid; output jsonb; t text; a jsonb; v_email text; begin
 select lower(u.email) into v_email from auth.users u where u.id=auth.uid() and u.email_confirmed_at is not null;
 if v_email is null then raise exception 'Verified email required'; end if;
 select m.workspace_id into w from public.memberships m where m.user_id=auth.uid() and m.active order by m.workspace_id limit 1;
 if w is null then
  return jsonb_build_object('invitations',(select coalesce(jsonb_agg(to_jsonb(i)),'[]') from public.invitations i where lower(i.email)=v_email and accepted_at is null and expires_at>now()));
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
revoke all on function public.is_member(uuid),public.is_owner(uuid) from public,anon;
grant execute on function public.is_member(uuid),public.is_owner(uuid) to authenticated;
