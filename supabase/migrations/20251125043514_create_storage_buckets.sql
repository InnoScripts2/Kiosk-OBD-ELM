-- Создание bucket-ов Supabase Storage (политики RLS будут добавлены отдельно
-- через supabase_storage_admin, т.к. эта роль зарезервирована и недоступна в
-- миграциях postgres).
INSERT INTO storage.buckets (id, name, public)
VALUES
	('avatars', 'avatars', TRUE),
	('posts', 'posts', TRUE),
	('private', 'private', FALSE)
ON CONFLICT (id) DO NOTHING;

