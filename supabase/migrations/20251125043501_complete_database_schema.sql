-- Базовая схема Supabase для профилей, постов, комментариев и связанной аналитики

-- Расширения
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Таблица профилей (связана с auth.users)
CREATE TABLE IF NOT EXISTS public.profiles (
	id UUID REFERENCES auth.users(id) ON DELETE CASCADE PRIMARY KEY,
	username TEXT UNIQUE NOT NULL CHECK (length(username) >= 3 AND length(username) <= 20),
	full_name TEXT,
	avatar_url TEXT,
	bio TEXT CHECK (length(bio) <= 500),
	website TEXT,
	created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
	updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

-- Таблица постов
CREATE TABLE IF NOT EXISTS public.posts (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
	title TEXT NOT NULL CHECK (length(title) >= 1 AND length(title) <= 200),
	content TEXT NOT NULL CHECK (length(content) >= 1),
	slug TEXT UNIQUE NOT NULL,
	published BOOLEAN DEFAULT FALSE NOT NULL,
	view_count INTEGER DEFAULT 0 NOT NULL,
	created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
	updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

-- Таблица комментариев
CREATE TABLE IF NOT EXISTS public.comments (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	post_id UUID REFERENCES public.posts(id) ON DELETE CASCADE NOT NULL,
	user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
	content TEXT NOT NULL CHECK (length(content) >= 1 AND length(content) <= 1000),
	parent_id UUID REFERENCES public.comments(id) ON DELETE CASCADE,
	created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
	updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL
);

-- Таблица лайков
CREATE TABLE IF NOT EXISTS public.likes (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
	post_id UUID REFERENCES public.posts(id) ON DELETE CASCADE NOT NULL,
	created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
	UNIQUE (user_id, post_id)
);

-- Таблица подписок
CREATE TABLE IF NOT EXISTS public.follows (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	follower_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
	following_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
	created_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
	UNIQUE (follower_id, following_id),
	CHECK (follower_id != following_id)
);

