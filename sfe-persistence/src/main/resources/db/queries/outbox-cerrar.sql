-- Cierra o reprograma un envio SOLO si seguimos siendo el dueno del intento (fencing por intentos + estado).
-- Si el lease vencio y otro worker lo retomo, intentos ya no coincide y no se actualiza nada (0 filas).
UPDATE envio_outbox
   SET estado          = :estado,
       proximo_intento = COALESCE(CAST(:proximo AS TIMESTAMPTZ), proximo_intento),
       lease_hasta     = NULL,
       worker_id       = NULL,
       ultimo_error    = :error
 WHERE id = :id
   AND intentos = :intento
   AND estado = 'EN_PROCESO'
