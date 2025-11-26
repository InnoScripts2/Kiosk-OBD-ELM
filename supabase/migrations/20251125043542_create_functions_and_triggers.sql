-- Общие функции и триггеры

CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
	NEW.updated_at = NOW();
	RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_profiles_updated_at ON public.profiles;
CREATE TRIGGER update_profiles_updated_at
	BEFORE UPDATE ON public.profiles
	FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

DROP TRIGGER IF EXISTS update_posts_updated_at ON public.posts;
CREATE TRIGGER update_posts_updated_at
	BEFORE UPDATE ON public.posts
	FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

DROP TRIGGER IF EXISTS update_comments_updated_at ON public.comments;
CREATE TRIGGER update_comments_updated_at
	BEFORE UPDATE ON public.comments
	FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

-- Функции аналитики
CREATE OR REPLACE FUNCTION public.get_post_stats(post_uuid UUID)
RETURNS TABLE (
	likes_count BIGINT,
	comments_count BIGINT
) AS $$
BEGIN
	RETURN QUERY
	SELECT
		(SELECT COUNT(*) FROM public.likes WHERE post_id = post_uuid),
		(SELECT COUNT(*) FROM public.comments WHERE post_id = post_uuid);
END;
$$ LANGUAGE plpgsql STABLE;

CREATE OR REPLACE FUNCTION public.get_user_stats(user_uuid UUID)
RETURNS TABLE (
	posts_count BIGINT,
	followers_count BIGINT,
	following_count BIGINT
) AS $$
BEGIN
	RETURN QUERY
	SELECT
		(SELECT COUNT(*) FROM public.posts WHERE user_id = user_uuid AND published = TRUE),
		(SELECT COUNT(*) FROM public.follows WHERE following_id = user_uuid),
		(SELECT COUNT(*) FROM public.follows WHERE follower_id = user_uuid);
END;
$$ LANGUAGE plpgsql STABLE;

