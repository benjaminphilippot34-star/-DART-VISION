-- DART VISION / Supabase initial schema
-- Run once in Supabase SQL Editor. Row Level Security is enabled on every user table.

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique,
  display_name text not null default 'Joueur',
  stats_visibility text not null default 'friends' check (stats_visibility in ('private','friends','public')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.friendships (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references public.profiles(id) on delete cascade,
  addressee_id uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending','accepted','blocked')),
  created_at timestamptz not null default now(),
  unique (requester_id, addressee_id),
  check (requester_id <> addressee_id)
);

create table if not exists public.matches (
  id uuid primary key default gen_random_uuid(),
  created_by uuid not null references public.profiles(id),
  game_mode text not null default 'X01',
  status text not null default 'waiting' check (status in ('waiting','playing','finished','cancelled')),
  visibility text not null default 'private' check (visibility in ('private','friends','public')),
  room_code text unique,
  settings jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  finished_at timestamptz
);

create table if not exists public.match_players (
  match_id uuid not null references public.matches(id) on delete cascade,
  user_id uuid not null references public.profiles(id),
  seat integer not null,
  team integer,
  joined_at timestamptz not null default now(),
  primary key (match_id, user_id),
  unique (match_id, seat)
);

create table if not exists public.match_turns (
  id bigint generated always as identity primary key,
  match_id uuid not null references public.matches(id) on delete cascade,
  user_id uuid not null references public.profiles(id),
  turn_number integer not null,
  score integer not null check (score between 0 and 180),
  darts jsonb not null default '[]'::jsonb,
  scoring_source text not null default 'manual' check (scoring_source in ('manual','camera')),
  confirmed boolean not null default true,
  created_at timestamptz not null default now(),
  unique (match_id, user_id, turn_number)
);

create index if not exists friendships_addressee_status_idx on public.friendships(addressee_id,status);
create index if not exists match_players_user_idx on public.match_players(user_id);
create index if not exists match_turns_match_idx on public.match_turns(match_id,turn_number);

alter table public.profiles enable row level security;
alter table public.friendships enable row level security;
alter table public.matches enable row level security;
alter table public.match_players enable row level security;
alter table public.match_turns enable row level security;

-- Profiles: owner can manage their own profile; public/friend visibility is read-only.
create policy "profile owner full access" on public.profiles
for all to authenticated using (id = auth.uid()) with check (id = auth.uid());

create policy "visible profiles readable" on public.profiles
for select to authenticated using (
  stats_visibility = 'public'
  or id = auth.uid()
  or (stats_visibility = 'friends' and exists (
    select 1 from public.friendships f
    where f.status = 'accepted'
      and ((f.requester_id = auth.uid() and f.addressee_id = profiles.id)
        or (f.addressee_id = auth.uid() and f.requester_id = profiles.id))
  ))
);

create policy "friendship participants read" on public.friendships
for select to authenticated using (requester_id = auth.uid() or addressee_id = auth.uid());
create policy "friendship requester create" on public.friendships
for insert to authenticated with check (requester_id = auth.uid());
create policy "friendship participants update" on public.friendships
for update to authenticated using (requester_id = auth.uid() or addressee_id = auth.uid())
with check (requester_id = auth.uid() or addressee_id = auth.uid());

create policy "match participants read" on public.matches
for select to authenticated using (
  visibility = 'public' or created_by = auth.uid()
  or exists (select 1 from public.match_players mp where mp.match_id = id and mp.user_id = auth.uid())
);
create policy "creator creates match" on public.matches
for insert to authenticated with check (created_by = auth.uid());
create policy "creator updates match" on public.matches
for update to authenticated using (created_by = auth.uid()) with check (created_by = auth.uid());

create policy "participants read roster" on public.match_players
for select to authenticated using (
  user_id = auth.uid()
  or exists (select 1 from public.matches m where m.id = match_id and m.created_by = auth.uid())
  or exists (select 1 from public.match_players own where own.match_id = match_id and own.user_id = auth.uid())
);
create policy "creator adds roster" on public.match_players
for insert to authenticated with check (
  exists (select 1 from public.matches m where m.id = match_id and m.created_by = auth.uid())
  or user_id = auth.uid()
);

create policy "match turns visible to participants" on public.match_turns
for select to authenticated using (
  exists (select 1 from public.match_players mp where mp.match_id = match_id and mp.user_id = auth.uid())
  or exists (select 1 from public.matches m where m.id = match_id and m.visibility = 'public')
);
create policy "players add own turns" on public.match_turns
for insert to authenticated with check (
  user_id = auth.uid()
  and exists (select 1 from public.match_players mp where mp.match_id = match_id and mp.user_id = auth.uid())
);
