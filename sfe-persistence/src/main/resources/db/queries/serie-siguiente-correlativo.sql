-- Siguiente correlativo SIN SALTOS: se incrementa dentro de la misma transaccion que crea el comprobante;
-- si esa transaccion hace rollback, el numero vuelve. (Una SEQUENCE de PostgreSQL deja huecos y no sirve aqui.)
UPDATE serie s
   SET ultimo_correlativo = s.ultimo_correlativo + 1
 WHERE s.id = (SELECT x.id
                 FROM serie x
                WHERE x.pos_id = :pos
                  AND x.tipo_comprobante = :tipo
                  AND substr(x.serie, 1, 1) = :letra
                ORDER BY x.id
                LIMIT 1)
RETURNING s.serie, s.ultimo_correlativo
