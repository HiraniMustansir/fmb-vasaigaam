-- All authoritative mutations go through execute_command. Decimal base quantities throughout.
create extension if not exists pgcrypto with schema extensions;
create table public.workspaces (
 id uuid primary key default gen_random_uuid(), name text not null,
 owner_id uuid not null references auth.users, timezone text not null default 'Asia/Kolkata',
 settings jsonb not null default '{"people_per_thaal":8,"default_tiffins":0,"onboarding":{}}', created_at timestamptz not null default now()
);
create table public.memberships (
 workspace_id uuid not null references public.workspaces, user_id uuid not null references auth.users,
 name text not null, position text not null, active boolean not null default true,
 primary key(workspace_id,user_id)
);
-- Only an operator using SQL can authorize bootstrap. A single global slot serializes claims.
create table public.bootstrap_authorizations (
 slot boolean primary key default true check(slot), email text not null, token_hash text not null,
 expires_at timestamptz not null, claimed_at timestamptz
);
create table public.invitations (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null references public.workspaces,
 email text not null, expires_at timestamptz not null default now()+interval '7 days', accepted_at timestamptz,
 unique(workspace_id,email)
);
create function public.is_member(w uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.memberships where workspace_id=w and user_id=auth.uid() and active)
$$;
create function public.is_owner(w uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.workspaces where id=w and owner_id=auth.uid()) and public.is_member(w)
$$;
create table public.items (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null references public.workspaces,
 name text not null, category text not null default 'Ingredient', unit text not null check(unit in ('kg','g','l','ml','each')),
 minimum numeric(18,6) not null default 0 check(minimum>=0), location text not null default '',
 version integer not null default 1, unique(workspace_id,id), unique(workspace_id,name)
);
create table public.item_conversions (
 workspace_id uuid not null, item_id uuid not null, label text not null, factor numeric(18,6) not null check(factor>0),
 primary key(item_id,label), foreign key(workspace_id,item_id) references public.items(workspace_id,id)
);
create table public.recipes (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null references public.workspaces,
 name text not null, base_yield numeric(18,6) not null check(base_yield>0), basis text not null check(basis in ('servings','tiffins','thaals')),
 instructions text not null default '', allergens text not null default '', version integer not null default 1,
 unique(workspace_id,id)
);
create table public.recipe_ingredients (
 workspace_id uuid not null, recipe_id uuid not null, item_id uuid not null, quantity numeric(18,6) not null check(quantity>0),
 primary key(recipe_id,item_id), foreign key(workspace_id,recipe_id) references public.recipes(workspace_id,id),
 foreign key(workspace_id,item_id) references public.items(workspace_id,id)
);
create table public.recipe_versions (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null, recipe_id uuid not null,
 version integer not null, snapshot jsonb not null, created_at timestamptz not null default now(),
 foreign key(workspace_id,recipe_id) references public.recipes(workspace_id,id), unique(recipe_id,version)
);
create table public.services (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null references public.workspaces,
 title text not null, service_date date not null, kind text not null check(kind in ('tiffin','gathering','observance')),
 status text not null default 'draft' check(status in ('draft','published','confirmed','cooking','completed','cancelled')),
 planned numeric(18,6) not null default 0 check(planned>=0), confirmed numeric(18,6) not null default 0 check(confirmed>=0),
 prepared numeric(18,6) not null default 0 check(prepared>=0), distributed numeric(18,6) not null default 0 check(distributed>=0),
 people_per_thaal integer not null default 8 check(people_per_thaal>0), buffer numeric(5,2) not null default 0 check(buffer between 0 and 100),
 venue text not null default '', notes text not null default '', budget numeric(18,2) not null default 0 check(budget>=0), version integer not null default 1,
 unique(workspace_id,id)
);
create table public.service_dishes (
 workspace_id uuid not null, service_id uuid not null, recipe_id uuid not null,
 primary key(service_id,recipe_id), foreign key(workspace_id,service_id) references public.services(workspace_id,id),
 foreign key(workspace_id,recipe_id) references public.recipes(workspace_id,id)
);
create table public.suppliers (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null references public.workspaces,
 name text not null, phone text not null default '', notes text not null default '', unique(workspace_id,id)
);
create table public.invoices (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null, supplier_id uuid not null,
 number text not null, invoice_date date not null, total numeric(18,2) not null check(total>0),
 foreign key(workspace_id,supplier_id) references public.suppliers(workspace_id,id),
 unique(workspace_id,id), unique(workspace_id,supplier_id,number)
);
create table public.invoice_lines (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null, invoice_id uuid not null, item_id uuid not null,
 quantity numeric(18,6) not null check(quantity>0), unit_cost numeric(18,6) not null check(unit_cost>=0),
 foreign key(workspace_id,invoice_id) references public.invoices(workspace_id,id),
 foreign key(workspace_id,item_id) references public.items(workspace_id,id), unique(workspace_id,id)
);
create table public.stock_movements (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null, item_id uuid not null,
 quantity numeric(18,6) not null check(quantity<>0), unit_cost numeric(18,6) not null default 0 check(unit_cost>=0),
 kind text not null check(kind in ('opening','receipt','consumption','return','waste','adjustment','reversal')),
 service_id uuid, invoice_line_id uuid, reverses uuid unique references public.stock_movements,
 batch text not null default '', expiry date, reason text not null, created_by uuid not null references auth.users,
 created_at timestamptz not null default now(),
 foreign key(workspace_id,item_id) references public.items(workspace_id,id),
 foreign key(workspace_id,service_id) references public.services(workspace_id,id),
 foreign key(workspace_id,invoice_line_id) references public.invoice_lines(workspace_id,id), unique(workspace_id,id)
);
create table public.stock_allocations (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null,
 issue_id uuid not null, receipt_id uuid not null, quantity numeric(18,6) not null check(quantity>0),
 foreign key(workspace_id,issue_id) references public.stock_movements(workspace_id,id),
 foreign key(workspace_id,receipt_id) references public.stock_movements(workspace_id,id), unique(issue_id,receipt_id)
);
create index on public.stock_allocations(receipt_id);
create table public.reservations (
 workspace_id uuid not null, service_id uuid not null, item_id uuid not null, quantity numeric(18,6) not null check(quantity>0),
 primary key(service_id,item_id), foreign key(workspace_id,service_id) references public.services(workspace_id,id),
 foreign key(workspace_id,item_id) references public.items(workspace_id,id)
);
create table public.accounts (
 workspace_id uuid not null references public.workspaces, code text not null, name text not null,
 primary key(workspace_id,code)
);
create table public.journal_entries (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null references public.workspaces,
 kind text not null, description text not null, invoice_id uuid, supplier_id uuid, member_id uuid,
 reverses uuid unique references public.journal_entries, created_by uuid not null references auth.users,
 created_at timestamptz not null default now(), unique(workspace_id,id),
 foreign key(workspace_id,member_id) references public.memberships(workspace_id,user_id),
 foreign key(workspace_id,invoice_id) references public.invoices(workspace_id,id),
 foreign key(workspace_id,supplier_id) references public.suppliers(workspace_id,id)
);
alter table public.stock_movements add column journal_entry_id uuid references public.journal_entries;
create table public.journal_lines (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null, entry_id uuid not null,
 account_code text not null, debit numeric(18,2) not null default 0 check(debit>=0), credit numeric(18,2) not null default 0 check(credit>=0),
 check((debit>0 and credit=0) or (credit>0 and debit=0)),
 foreign key(workspace_id,entry_id) references public.journal_entries(workspace_id,id),
 foreign key(workspace_id,account_code) references public.accounts(workspace_id,code)
);
create function public.check_balanced() returns trigger language plpgsql set search_path='' as $$
 begin
 if (select count(*)<2 or sum(debit)<>sum(credit) from public.journal_lines where entry_id=new.entry_id) then
 raise exception 'Journal must have at least two balanced lines'; end if; return new;
 end $$;
create constraint trigger balanced_journal after insert or update on public.journal_lines deferrable initially deferred for each row execute function public.check_balanced();
create table public.production_runs (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null, service_id uuid not null,
 recipe_snapshot jsonb not null, prepared numeric(18,6) not null check(prepared>=0), distributed numeric(18,6) not null check(distributed between 0 and prepared),
 leftovers numeric(18,6) not null check(leftovers>=0), waste numeric(18,6) not null check(waste>=0),
 redistribution numeric(18,6) not null default 0 check(redistribution>=0), notes text not null,
 created_by uuid not null references auth.users, created_at timestamptz not null default now(),
 foreign key(workspace_id,service_id) references public.services(workspace_id,id), unique(workspace_id,id),
 check(distributed+leftovers+waste+redistribution<=prepared)
);
create table public.calendar_mappings (
 workspace_id uuid not null references public.workspaces, gregorian date not null, hijri text not null,
 source text not null, verified boolean not null default false, version integer not null default 1,
 primary key(workspace_id,gregorian)
);
create table public.tasks (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null references public.workspaces,
 title text not null, due_date date not null, assigned_to uuid, done boolean not null default false, version integer not null default 1,
 foreign key(workspace_id,assigned_to) references public.memberships(workspace_id,user_id)
);
create table public.notes (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null references public.workspaces,
 subject text not null, body text not null, created_by uuid not null references auth.users, created_at timestamptz not null default now()
);
create table public.attachments (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null references public.workspaces,
 path text not null unique, subject text not null, created_by uuid not null references auth.users,
 check(split_part(path,'/',1)=workspace_id::text)
);
create table public.audit_events (
 id uuid primary key default gen_random_uuid(), workspace_id uuid not null references public.workspaces,
 actor uuid not null references auth.users, kind text not null, payload jsonb not null, created_at timestamptz not null default now()
);
create table public.command_results (
 workspace_id uuid not null references public.workspaces, key uuid not null, actor uuid not null references auth.users,
 kind text not null, payload jsonb not null, result jsonb not null, primary key(workspace_id,key)
);
-- No client can mutate ledger or audit tables directly. Server functions implement permission and transactions.
do $$ declare t text; begin
 foreach t in array array['workspaces','memberships','invitations','items','item_conversions','recipes','recipe_ingredients','recipe_versions','services','service_dishes','suppliers','invoices','invoice_lines','stock_movements','stock_allocations','reservations','accounts','journal_entries','journal_lines','production_runs','calendar_mappings','tasks','notes','attachments','audit_events','command_results'] loop
 execute format('alter table public.%I enable row level security',t);
 execute format('revoke all on public.%I from anon, authenticated',t);
 execute format('grant select on public.%I to authenticated',t);
 if t='workspaces' then
 execute 'create policy member_read on public.workspaces for select to authenticated using(public.is_member(id))';
 else
 execute format('create policy member_read on public.%I for select to authenticated using(public.is_member(workspace_id))',t);
 execute format('create index on public.%I(workspace_id)',t);
 end if;
 end loop;
end $$;
alter table public.bootstrap_authorizations enable row level security;
revoke all on public.bootstrap_authorizations from anon, authenticated;
create index on public.services(workspace_id,service_date,status);
create index on public.stock_movements(workspace_id,item_id,created_at);
create index on public.audit_events(workspace_id,created_at desc);
create index on public.invoice_lines(invoice_id);
create index on public.journal_lines(entry_id);
create index on public.memberships(user_id) where active;
create index on public.invitations(lower(email));
-- Aggregate demand excludes observances and finalized services; buffer is applied once.
create view public.ingredient_demand with(security_invoker=true) as
select s.workspace_id,s.id service_id,ri.item_id,
 sum(ri.quantity*greatest((case
 when r.basis='thaals' and s.kind='gathering' then greatest(s.planned,s.confirmed)
 when r.basis='thaals' then greatest(s.planned,s.confirmed)/s.people_per_thaal
 when r.basis='servings' and s.kind='gathering' then greatest(s.planned,s.confirmed)*s.people_per_thaal
 else greatest(s.planned,s.confirmed) end)*(1+s.buffer/100) - (case
 when r.basis='thaals' and s.kind='gathering' then s.prepared
 when r.basis='thaals' then s.prepared/s.people_per_thaal
 when r.basis='servings' and s.kind='gathering' then s.prepared*s.people_per_thaal
 else s.prepared end),0)/r.base_yield) required
from public.services s join public.service_dishes sd on sd.service_id=s.id
join public.recipes r on r.id=sd.recipe_id join public.recipe_ingredients ri on ri.recipe_id=r.id
where s.kind<>'observance' and s.status not in ('completed','cancelled')
group by s.workspace_id,s.id,ri.item_id;
grant select on public.ingredient_demand to authenticated;
-- Storage uses workspace UUID as the first path component. Membership checked on every read.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values ('committee-attachments','committee-attachments',false,10485760,array['image/jpeg','image/png','application/pdf']) on conflict(id) do nothing;
create policy attachment_read on storage.objects for select to authenticated using(bucket_id='committee-attachments' and public.is_member((storage.foldername(name))[1]::uuid));
create policy attachment_insert on storage.objects for insert to authenticated with check(bucket_id='committee-attachments' and public.is_member((storage.foldername(name))[1]::uuid));
-- No update/delete: posted evidence is append-only.
alter publication supabase_realtime add table public.audit_events;
