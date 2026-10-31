-- Só um evento em andamento (sem fim) por vez, garantido pelo banco: dois
-- cliques em "Iniciar evento" ao mesmo tempo passam pela validação do serviço
-- (nenhum vê o outro ainda), mas o segundo INSERT esbarra neste índice e o app
-- mostra "Já há um evento em andamento". Só no PostgreSQL (índice parcial); no
-- H2 fica a validação do EventoService.

-- Para com mensagem clara se já houver mais de um aberto: encerre um na tela Eventos.
SELECT CAST('V22 parada: há ' || CAST(n AS VARCHAR(20))
            || ' eventos em andamento (sem fim). Encerre todos menos um e rode de novo.' AS INTEGER)
FROM (SELECT COUNT(*) AS n FROM evento WHERE fim IS NULL) abertos
WHERE n > 1;

CREATE UNIQUE INDEX ux_evento_aberto ON evento ((fim IS NULL)) WHERE fim IS NULL;
