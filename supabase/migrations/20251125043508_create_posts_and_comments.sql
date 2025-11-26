-- Дополнительные комментарии и описания для контента

COMMENT ON TABLE public.posts IS 'Посты пользователей';
COMMENT ON COLUMN public.posts.slug IS 'Уникальный человеко-читаемый идентификатор поста';
COMMENT ON COLUMN public.posts.published IS 'Флаг публикации; черновики недоступны без авторизации';

COMMENT ON TABLE public.comments IS 'Комментарии к постам с поддержкой вложенности';
COMMENT ON COLUMN public.comments.parent_id IS 'Опциональная ссылка на родительский комментарий';

COMMENT ON TABLE public.likes IS 'Лайки постов';
COMMENT ON TABLE public.follows IS 'Система подписок между пользователями';

