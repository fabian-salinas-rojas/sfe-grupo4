#!/bin/bash
# NOTA: guion usado para validar estos SQL en el sandbox (usa "su postgres" y la BD sfe_test). Adaptalo a tu PostgreSQL 16 local.
# Valida con PostgreSQL REAL los SQL de sfe-persistence: SKIP LOCKED, lease, fencing, correlativos sin saltos e inmutabilidad.
Q=/home/claude/sfe-grupo4/sfe-persistence/src/main/resources/db/queries
PG() { su postgres -c "psql -X -q -t -A -d sfe_test $*"; }

reset() {
  su postgres -c "psql -X -q -d sfe_test" <<'SQL' >/dev/null
TRUNCATE envio_outbox;
ALTER TABLE comprobante DISABLE TRIGGER trg_comprobante_inmutable;
DELETE FROM comprobante;
ALTER TABLE comprobante ENABLE TRIGGER trg_comprobante_inmutable;
ALTER TABLE bitacora_auditoria DISABLE TRIGGER trg_bitacora_solo_agregar;
DELETE FROM bitacora_auditoria;
ALTER TABLE bitacora_auditoria ENABLE TRIGGER trg_bitacora_solo_agregar;
UPDATE serie SET ultimo_correlativo = 0;
SQL
}

sembrar() { # sembrar N: N comprobantes y N filas de outbox PENDIENTES (vencidas)
  su postgres -c "psql -X -q -d sfe_test" <<SQL >/dev/null
INSERT INTO comprobante (id, emisor_id, pos_id, id_venta_externo, tipo_comprobante, serie, correlativo, fecha_emision, moneda, importe_total, xml_firmado, xml_sha256, zip_nombre, zip_contenido, plazo_limite)
SELECT gen_random_uuid(), e.id, p.id, 'V-' || g, '01', 'F001', g, current_date, 'PEN', 100, '\x00', repeat('0',64), 'x-' || g || '.zip', '\x504b', current_date + 3
FROM generate_series(1, $1) g, emisor e, pos p WHERE e.ruc='20608945123' AND p.nombre='CAJA-01';
INSERT INTO envio_outbox (id, comprobante_id, proximo_intento) SELECT gen_random_uuid(), id, now() - interval '1 minute' FROM comprobante;
SQL
}

claim_sql() { # claim_sql MAX LEASE WORKER  (sustituye los :parametros nombrados por literales)
  sed -e "s/:ahora/now()/g" -e "s/:lease_segundos/$2/g" -e "s/:worker/'$3'/g" -e "s/:max/$1/g" "$Q/outbox-reclamar.sql"
}

echo "== 1) SKIP LOCKED: dos workers reclaman a la vez (A retiene su transaccion 3 s) =="
reset; sembrar 10
( { echo "BEGIN;"; claim_sql 5 30 A; echo ";"; echo "SELECT pg_sleep(3);"; echo "COMMIT;"; } | su postgres -c "psql -X -q -t -A -d sfe_test" > /tmp/claimA.txt 2>&1 ) &
sleep 1
( { echo "BEGIN;"; claim_sql 5 30 B; echo ";"; echo "COMMIT;"; } | su postgres -c "psql -X -q -t -A -d sfe_test" > /tmp/claimB.txt 2>&1 )
wait
ids() { cut -d'|' -f1 "$1" | grep -E '^[0-9a-f-]{36}$' | sort; }
A=$(ids /tmp/claimA.txt | wc -l); B=$(ids /tmp/claimB.txt | wc -l)
INTER=$(comm -12 <(ids /tmp/claimA.txt) <(ids /tmp/claimB.txt) | wc -l)
echo "worker A reclamo $A, worker B reclamo $B, en comun: $INTER   (esperado: 5, 5, 0)"
PG "-c \"SELECT estado || ' x' || count(*) || ' intentos=' || min(intentos) || '..' || max(intentos) FROM envio_outbox GROUP BY estado\""

echo "== 2) Lease: un EN_PROCESO abandonado (lease vencido) se retoma; uno con lease vigente NO =="
reset; sembrar 2
su postgres -c "psql -X -q -d sfe_test" <<'SQL'
WITH x AS (SELECT id, row_number() OVER (ORDER BY id) rn FROM envio_outbox)
UPDATE envio_outbox o SET estado='EN_PROCESO', intentos=1,
       lease_hasta = CASE WHEN x.rn=1 THEN now() - interval '5 seconds' ELSE now() + interval '60 seconds' END, worker_id='muerto'
FROM x WHERE o.id = x.id;
SQL
claim_sql 10 30 C | PG "" | awk -F'|' '{print "reclamado: intento=" $3}'
echo "(esperado: exactamente 1 fila, con intento=2)"

echo "== 3) Fencing: el worker lento NO pisa al que retomo el envio =="
reset; sembrar 1
ID=$(PG "-c 'SELECT id FROM envio_outbox'")
claim_sql 1 30 W1 | PG "" >/dev/null                                                       # W1 toma el intento 1
PG "-c \"UPDATE envio_outbox SET lease_hasta = now() - interval '1 second'\"" >/dev/null   # W1 se cuelga
claim_sql 1 30 W2 | PG "" >/dev/null                                                       # W2 retoma: intento 2
cerrar() { sed -e "s/:estado/'COMPLETADO'/" -e "s/:proximo/NULL/" -e "s/:error/NULL/" -e "s/:id/'$ID'/" -e "s/:intento/$1/" "$Q/outbox-cerrar.sql" | grep -v '^--' | tr '\n' ' '; }
R1=$(su postgres -c "psql -X -d sfe_test -c \"$(cerrar 1)\"" 2>&1 | grep -E "UPDATE|ERROR")
R2=$(su postgres -c "psql -X -d sfe_test -c \"$(cerrar 2)\"" 2>&1 | grep -E "UPDATE|ERROR")
echo "W1 (obsoleto, intento=1): $R1   |   W2 (vigente, intento=2): $R2   (esperado: UPDATE 0 / UPDATE 1)"

echo "== 4) Correlativos sin saltos: 40 transacciones concurrentes, 10 con ROLLBACK =="
reset
sql4() { sed -e "s/:pos/(SELECT id FROM pos LIMIT 1)/" -e "s/:tipo/'01'/" -e "s/:letra/'F'/" "$Q/serie-siguiente-correlativo.sql"; }
rm -f /tmp/corr_*.txt
for i in $(seq 1 40); do
  ( if [ $((i % 4)) -eq 0 ]; then FIN=ROLLBACK; else FIN=COMMIT; fi
    { echo "BEGIN;"; sql4; echo ";"; echo "SELECT pg_sleep(0.05);"; echo "$FIN;"; } | su postgres -c "psql -X -q -t -A -d sfe_test" > /tmp/corr_$i.txt 2>&1 ) &
done
wait
PG "-c \"SELECT 'ultimo_correlativo=' || ultimo_correlativo FROM serie WHERE serie='F001'\""
echo "(esperado: 30 = 40 - 10 revertidas; el rollback devuelve el numero, no quedan huecos)"
# solo transacciones CONFIRMADAS (i no multiplo de 4): sus numeros deben ser exactamente 1..30, sin repetir ni saltar
for i in $(seq 1 40); do [ $((i % 4)) -ne 0 ] && cat /tmp/corr_$i.txt; done | grep -E '^F001\|' | cut -d'|' -f2 | sort -n > /tmp/confirmados.txt
echo "confirmadas: $(wc -l < /tmp/confirmados.txt), distintos: $(sort -un /tmp/confirmados.txt | wc -l), min=$(head -1 /tmp/confirmados.txt), max=$(tail -1 /tmp/confirmados.txt)"
diff <(seq 1 30) /tmp/confirmados.txt >/dev/null && echo "OK: los numeros confirmados son exactamente 1..30 (sin huecos ni duplicados)" || echo "FALLO: hay huecos o duplicados"

echo "== 5) Inmutabilidad y bitacora de solo agregar =="
reset; sembrar 1
for stmt in "UPDATE comprobante SET xml_firmado='\\\\x01'" "UPDATE comprobante SET estado='ACEPTADO', cdr_codigo='0'" "DELETE FROM comprobante" \
            "INSERT INTO bitacora_auditoria(evento,actor) VALUES ('X','t')" "UPDATE bitacora_auditoria SET actor='y'" "DELETE FROM bitacora_auditoria"; do
  printf '%-62s -> ' "$stmt"
  su postgres -c "psql -X -d sfe_test -c \"$stmt\"" 2>&1 | grep -E "ERROR|UPDATE|INSERT|DELETE" | head -1
done
