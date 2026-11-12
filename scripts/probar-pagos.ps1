# ==============================================================================
# Pruebas de la pasarela de pagos SIMULADA (HU-12 / N-08 / HU-13) contra el backend LOCAL.
# Requisito: backend corriendo con el perfil dev (H2 en memoria) en http://localhost:8080
#   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
# Uso (desde la carpeta Prisma-Backend-Web):
#   powershell -ExecutionPolicy Bypass -File .\scripts\probar-pagos.ps1
# ==============================================================================
param([string]$Base = "http://localhost:8080/api/v1")

$ErrorActionPreference = "Stop"
$script:ok = 0
$script:fallos = 0

function Llamar([string]$metodo, [string]$ruta, [string]$token, [string]$cuerpo) {
    $params = @{ UseBasicParsing = $true; Method = $metodo; Uri = "$Base$ruta"; ContentType = "application/json; charset=utf-8" }
    if ($token) { $params.Headers = @{ Authorization = "Bearer $token" } }
    if ($cuerpo) { $params.Body = [System.Text.Encoding]::UTF8.GetBytes($cuerpo) }
    try {
        $r = Invoke-WebRequest @params
        $texto = [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        return @{ Status = [int]$r.StatusCode; Json = ($texto | ConvertFrom-Json) }
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { throw "No hay conexión con $Base. ¿Está corriendo el backend?" }
        $lector = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
        $texto = $lector.ReadToEnd()
        $json = $null
        try { $json = $texto | ConvertFrom-Json } catch { }
        return @{ Status = [int]$resp.StatusCode; Json = $json }
    }
}

function Verificar([string]$caso, [bool]$condicion, [string]$detalle) {
    if ($condicion) {
        $script:ok++
        Write-Host "  [OK]    $caso" -ForegroundColor Green
    } else {
        $script:fallos++
        Write-Host "  [FALLA] $caso -> $detalle" -ForegroundColor Red
    }
}

function Login([string]$email, [string]$clave) {
    $r = Llamar "POST" "/auth/login" $null "{`"email`":`"$email`",`"password`":`"$clave`"}"
    if ($r.Status -ne 200) { throw "No se pudo iniciar sesión con $email (HTTP $($r.Status))" }
    return $r.Json.datos.token
}

function CrearPedido([string]$token) {
    $r = Llamar "POST" "/tiendas/pedidos" $token '{"proveedorId":1,"items":[]}'
    if ($r.Status -ne 200) { throw "No se pudo crear el pedido (HTTP $($r.Status))" }
    return $r.Json.datos
}

Write-Host "`n=== Iniciando sesión ===" -ForegroundColor Cyan
$tendero = Login "dona.marta@labendicion.com" "admin123"
$proveedor = Login "logistica@femsa.com.co" "femsa123"
$conductor = Login "conductor.juan@femsa.com.co" "admin123"
Write-Host "  Tendero, proveedor y conductor autenticados"

# ------------------------------------------------------------------------------
Write-Host "`n=== Checkout del tendero (N-08) ===" -ForegroundColor Cyan
$pedido = CrearPedido $tendero
Write-Host "  Pedido creado: $($pedido.numeroPedido) (id $($pedido.id), total $($pedido.totalAPagar), PIN $($pedido.pinEntregaHash))"
$rutaPago = "/tiendas/pedidos/$($pedido.id)/pago"

$r = Llamar "GET" $rutaPago $tendero $null
Verificar "CP-01 Pedido nuevo no tiene pagos (404)" ($r.Status -eq 404) "HTTP $($r.Status)"

$r = Llamar "POST" $rutaPago $tendero '{"medio":"NEQUI","celular":"3992222222"}'
Verificar "CP-02 Nequi 3992222222 es RECHAZADO" ($r.Status -eq 200 -and $r.Json.datos.estado -eq "RECHAZADO") "HTTP $($r.Status) estado $($r.Json.datos.estado)"
Verificar "CP-02b HU-13: mensaje 'El pago no pudo ser procesado, intenta con otro medio'" ($r.Json.mensaje -eq "El pago no pudo ser procesado, intenta con otro medio") "'$($r.Json.mensaje)'"

$r = Llamar "POST" $rutaPago $tendero '{"medio":"PSE","banco":"Banco que rechaza","tipoPersona":"NATURAL","documento":"1020304050"}'
Verificar "CP-03 PSE 'Banco que rechaza' es RECHAZADO" ($r.Json.datos.estado -eq "RECHAZADO") "estado $($r.Json.datos.estado)"

$r = Llamar "POST" $rutaPago $tendero '{"medio":"TARJETA","numeroTarjeta":"4111111111111111","titular":"MARTA","vencimiento":"12/30"}'
Verificar "CP-04 Tarjeta 4111... es RECHAZADA" ($r.Json.datos.estado -eq "RECHAZADO") "estado $($r.Json.datos.estado)"

$r = Llamar "POST" $rutaPago $tendero '{"medio":"TARJETA","numeroTarjeta":"4242424242424241","titular":"MARTA","vencimiento":"12/30"}'
Verificar "CP-05 Tarjeta con número inválido (Luhn) da 400" ($r.Status -eq 400) "HTTP $($r.Status)"

$r = Llamar "POST" $rutaPago $tendero '{"medio":"TARJETA","numeroTarjeta":"4242424242424242","titular":"MARTA","vencimiento":"01/20"}'
Verificar "CP-06 Tarjeta vencida da 400" ($r.Status -eq 400) "HTTP $($r.Status)"

$r = Llamar "POST" $rutaPago $tendero '{"medio":"NEQUI","celular":"12345"}'
Verificar "CP-07 Celular Nequi inválido da 400" ($r.Status -eq 400) "HTTP $($r.Status)"

$r = Llamar "GET" "/tiendas/mis-pedidos" $tendero $null
$mio = $r.Json.datos | Where-Object { $_.id -eq $pedido.id }
Verificar "CP-08 Mis Pedidos muestra el pago RECHAZADO (puede reintentar)" ($mio.estadoPago -eq "RECHAZADO") "estadoPago $($mio.estadoPago)"
Verificar "CP-08b HU-13: tras el rechazo el pedido sigue Confirmado ($($mio.estado))" ($mio.estado -eq "CREADO" -or $mio.estado -eq "ACEPTADO") "estado $($mio.estado)"

$r = Llamar "POST" $rutaPago $tendero '{"medio":"NEQUI","celular":"3991111111"}'
Verificar "CP-09 Nequi 3991111111 APRUEBA y queda EN_GARANTIA" ($r.Status -eq 200 -and $r.Json.datos.estado -eq "EN_GARANTIA") "HTTP $($r.Status) estado $($r.Json.datos.estado)"
Write-Host "          Referencia: $($r.Json.datos.referencia) | Transacción: $($r.Json.datos.transaccionId)"
$p = $r.Json.datos
$esperadaComision = [math]::Round([decimal]$p.monto * [decimal]0.035, 2, [MidpointRounding]::AwayFromZero)
Verificar "CP-09b HU-13: comisión PRISMA = 3.5% del total ($($p.comisionPrisma))" ([decimal]$p.comisionPrisma -eq $esperadaComision) "esperado $esperadaComision"
Verificar "CP-09c HU-13: comisión + proveedor = total (split atómico)" (([decimal]$p.comisionPrisma + [decimal]$p.montoProveedor) -eq [decimal]$p.monto) "$($p.comisionPrisma) + $($p.montoProveedor) <> $($p.monto)"
Verificar "CP-09d HU-13: confirmado por webhook, tarifa Wompi aparte ($($p.tarifaPasarela))" ($p.confirmadoPorWebhook -eq $true -and [decimal]$p.tarifaPasarela -gt 0) "confirmadoPorWebhook $($p.confirmadoPorWebhook)"

$r = Llamar "POST" $rutaPago $tendero '{"medio":"NEQUI","celular":"3991111111"}'
Verificar "CP-10 No se puede pagar dos veces (409)" ($r.Status -eq 409) "HTTP $($r.Status)"

$r = Llamar "GET" "/tiendas/mis-pedidos" $tendero $null
$mio = $r.Json.datos | Where-Object { $_.id -eq $pedido.id }
Verificar "CP-11 Mis Pedidos muestra EN_GARANTIA con medio NEQUI" ($mio.estadoPago -eq "EN_GARANTIA" -and $mio.medioPagoEnLinea -eq "NEQUI") "estadoPago $($mio.estadoPago)"

$r = Llamar "POST" $rutaPago $proveedor '{"medio":"NEQUI","celular":"3991111111"}'
Verificar "CP-12 Un proveedor no puede pagar pedidos de tenderos (403)" ($r.Status -eq 403) "HTTP $($r.Status)"

# ------------------------------------------------------------------------------
Write-Host "`n=== Garantía y liberación al entregar ===" -ForegroundColor Cyan
$r = Llamar "GET" "/proveedores/pagos/recaudos" $proveedor $null
$pagoProv = $r.Json.datos.pagos | Where-Object { $_.pedidoId -eq $pedido.id }
Verificar "CP-13 El proveedor ve el pago EN_GARANTIA" ($pagoProv.estado -eq "EN_GARANTIA") "estado $($pagoProv.estado)"
Write-Host "          Pagado $($pagoProv.monto) | Comisión PRISMA $($pagoProv.comisionPrisma) | Recibe $($pagoProv.montoProveedor)"

$r = Llamar "POST" "/repartidores/pedidos/$($pedido.id)/confirmar-entrega" $conductor "{`"pin`":`"$($pedido.pinEntregaHash)`",`"medioPago`":`"EFECTIVO`"}"
Verificar "CP-14 El conductor confirma la entrega con el PIN y el pago queda LIBERADO" ($r.Status -eq 200 -and $r.Json.datos.estadoPago -eq "LIBERADO") "HTTP $($r.Status) estadoPago $($r.Json.datos.estadoPago)"

$r = Llamar "GET" "/proveedores/pagos/recaudos" $proveedor $null
$pagoProv = $r.Json.datos.pagos | Where-Object { $_.pedidoId -eq $pedido.id }
Verificar "CP-15 El proveedor ve el pago LIBERADO" ($pagoProv.estado -eq "LIBERADO") "estado $($pagoProv.estado)"

$otro = CrearPedido $tendero
$r = Llamar "POST" "/tiendas/pedidos/$($otro.id)/pago" $tendero '{"medio":"TARJETA","numeroTarjeta":"4242 4242 4242 4242","titular":"MARTA RODRIGUEZ","vencimiento":"12/30","cuotas":3}'
Verificar "CP-16 Tarjeta 4242... APRUEBA (segundo pedido $($otro.numeroPedido))" ($r.Json.datos.estado -eq "EN_GARANTIA") "estado $($r.Json.datos.estado)"

# ------------------------------------------------------------------------------
Write-Host "`n=== Webhook de la pasarela (HU-13) ===" -ForegroundColor Cyan
$eventoFalso = '{"event":"transaction.updated","data":{"transaction":{"id":"SBX-FALSO","reference":"X","status":"APPROVED","amount_in_cents":100}},"signature":{"properties":["transaction.id","transaction.status","transaction.amount_in_cents"],"checksum":"firma-falsa"},"timestamp":1700000000}'
$r = Llamar "POST" "/pagos/webhooks/wompi" $null $eventoFalso
Verificar "CP-W1 Webhook con firma falsa se rechaza (401)" ($r.Status -eq 401) "HTTP $($r.Status)"

# ------------------------------------------------------------------------------
Write-Host "`n=== Cuenta de cobro del proveedor (HU-12) ===" -ForegroundColor Cyan
$null = Llamar "DELETE" "/proveedores/pagos/cuenta" $proveedor $null   # deja el estado limpio si ya estaba vinculada

$r = Llamar "GET" "/proveedores/pagos/cuenta" $proveedor $null
Verificar "CP-17 Sin cuenta vinculada" ($r.Json.datos.vinculada -eq $false) "vinculada $($r.Json.datos.vinculada)"
Verificar "CP-17b HU-13: el proveedor recibe 96.5% (comisión fija 3.5%)" ([decimal]$r.Json.datos.porcentajeProveedor -eq 96.5) "porcentaje $($r.Json.datos.porcentajeProveedor)"

$r = Llamar "POST" "/proveedores/pagos/cuenta" $proveedor '{"banco":"Bancolombia","tipoCuenta":"AHORROS","numeroCuenta":"1234560000","titular":"FEMSA Colombia"}'
Verificar "CP-18 Cuenta terminada en 0000: rechazo con el mensaje de la HU" ($r.Status -eq 400 -and $r.Json.mensaje -eq "No se pudo vincular tu cuenta de cobro, intenta nuevamente") "HTTP $($r.Status) '$($r.Json.mensaje)'"

$r = Llamar "POST" "/proveedores/pagos/cuenta" $proveedor '{"banco":"Bancolombia","tipoCuenta":"AHORROS","numeroCuenta":"12345678912","titular":"FEMSA Colombia"}'
Verificar "CP-19 Vinculación exitosa, solo últimos 4 dígitos" ($r.Status -eq 200 -and $r.Json.datos.vinculada -and $r.Json.datos.numeroCuentaEnmascarado -like "*8912") "HTTP $($r.Status)"

$r = Llamar "POST" "/proveedores/pagos/cuenta" $proveedor '{"banco":"Davivienda","tipoCuenta":"CORRIENTE","numeroCuenta":"99998888","titular":"FEMSA"}'
Verificar "CP-20 No se vinculan dos cuentas (409)" ($r.Status -eq 409) "HTTP $($r.Status)"

$r = Llamar "GET" "/proveedores/pagos/cuenta" $tendero $null
Verificar "CP-21 El tendero no accede a cuentas de cobro (403)" ($r.Status -eq 403) "HTTP $($r.Status)"

# ------------------------------------------------------------------------------
$color = if ($script:fallos -eq 0) { "Green" } else { "Red" }
Write-Host "`nResultado: $($script:ok) OK, $($script:fallos) con falla`n" -ForegroundColor $color
Write-Host "Nota: la cuenta de cobro de FEMSA quedó VINCULADA para que la veas en la web." -ForegroundColor DarkGray
