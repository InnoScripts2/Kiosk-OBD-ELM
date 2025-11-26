-- Дополнительная логика для пользователей и профилей

COMMENT ON TABLE public.profiles IS 'Профили пользователей с дополнительными полями и ссылкой на auth.users';

COMMENT ON COLUMN public.profiles.username IS 'Уникальный ник в сервисе, используется во всех ссылках';
COMMENT ON COLUMN public.profiles.bio IS 'Краткое описание (до 500 символов)';
COMMENT ON COLUMN public.profiles.avatar_url IS 'Ссылка на аватар, может вести в Supabase Storage';

-- Функция и триггер создания профиля на основании auth.users
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
	INSERT INTO public.profiles (id, username, full_name, avatar_url)
	VALUES (
		NEW.id,
		COALESCE(NEW.raw_user_meta_data->>'username', 'user_' || substr(NEW.id::text, 1, 8)),
		COALESCE(NEW.raw_user_meta_data->>'full_name', ''),
		COALESCE(NEW.raw_user_meta_data->>'avatar_url', '')
	)
	ON CONFLICT (id) DO NOTHING;
	RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

