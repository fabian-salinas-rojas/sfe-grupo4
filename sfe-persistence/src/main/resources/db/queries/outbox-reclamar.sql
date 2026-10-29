-- Reclama hasta :max envios vencidos de forma atomica y sin bloquear a otros workers (FOR UPDATE SKIP LOCKED).
-- Incluye los EN_PROCESO con lease vencido (worker caido). intentos se incrementa aqui: sirve de token de fencing.
WITH reclamados AS (
    UPDATE envio_outbox o
       SET estado            = 'EN_PROCESO',
           intentos          = o.intentos + 1,
           lease_hasta       = :ahora + :lease_segundos * INTERVAL '1 second',
           worker_id         = :worker,
           ultimo_intento_en = :ahora
     WHERE o.id IN (SELECT q.id
                      FROM envio_outbox q
                     WHERE (q.estado = 'PENDIENTE'  AND q.proximo_intento <= :ahora)
                        OR (q.estado = 'EN_PROCESO' AND q.lease_hasta     <  :ahora)
                     ORDER BY q.proximo_intento
                     LIMIT :max
                       FOR UPDATE SKIP LOCKED)
 RETURNING o.id, o.comprobante_id, o.intentos)
SELECT r.id AS envio_id, r.comprobante_id, r.intentos, c.zip_nombre, c.zip_contenido, c.plazo_limite
  FROM reclamados r
  JOIN comprobante c ON c.id = r.comprobante_id
 ORDER BY r.id
