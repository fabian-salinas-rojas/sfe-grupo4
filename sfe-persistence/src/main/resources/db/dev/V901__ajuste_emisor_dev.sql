-- SOLO PERFIL dev. Ajusta los datos del emisor de desarrollo (Arequipa).
UPDATE emisor
   SET razon_social     = 'DISTRIBUIDORA & LOGISTICA COMERCIAL DEL SUR S.A.C.',
       nombre_comercial = 'LOGISTICA DEL SUR',
       ubigeo           = '040101',
       direccion        = 'AV. VENEZUELA 1500',
       departamento     = 'AREQUIPA',
       provincia        = 'AREQUIPA',
       distrito         = 'AREQUIPA'
 WHERE ruc = '20608945123';
