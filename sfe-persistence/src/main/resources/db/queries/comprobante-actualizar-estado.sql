UPDATE comprobante
   SET estado            = :estado,
       cdr_codigo        = :cdr_codigo,
       cdr_descripcion   = :cdr_descripcion,
       cdr_observaciones = :cdr_observaciones,
       cdr_zip           = :cdr_zip,
       cdr_firma_integra = :cdr_firma_integra,
       cdr_recibido_en   = COALESCE(CAST(:recibido_en AS TIMESTAMPTZ), cdr_recibido_en),
       actualizado_en    = :ahora
 WHERE id = :id
