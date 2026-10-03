-- Miniatura do produto; nula quando a fonte não tem foto.
ALTER TABLE cached_offer ADD COLUMN image_url TEXT;

-- O cache existente não tem fotos: vencer todas as categorias faz o refresh
-- rebaixá-las ao subir. Só o cache é afetado; o histórico de preços fica.
DELETE FROM category_refresh;
