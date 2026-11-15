-- DART VISION: durable per-account match history and statistics
create table if not exists public.player_game_history (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  game_mode text not null default 'X01',
  result text not null default 'completed' check (result in ('completed','abandoned')),
  started_at timestamptz not null default now(),
  finished_at timestamptz not null default now(),
  summary jsonb not null default '{}'::jsonb,
  game_data jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists player_game_history_user_date_idx
  on public.player_game_history(user_id, finished_at desc);
alter table public.player_game_history enable row level security;
drop policy if exists "players read own game history" on public.player_game_history;
create policy "players read own game history" on public.player_game_history
  for select to authenticated using (user_id = auth.uid());
drop policy if exists "players insert own game history" on public.player_game_history;
create policy "players insert own game history" on public.player_game_history
  for insert to authenticated with check (user_id = auth.uid());
drop policy if exists "players update own game history" on public.player_game_history;
create policy "players update own game history" on public.player_game_history
  for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
