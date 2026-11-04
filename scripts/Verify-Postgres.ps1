# Ejecutar solo contra el contenedor temporal facturacion-arca-verificacion.
# La suite usa create-drop: nunca apuntar estas variables a una base con datos reales.
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-f0-9]{64}$')]
    [string]$ContainerId
)

$ErrorActionPreference = 'Stop'
$verificationContainer = 'facturacion-arca-verificacion'
$verificationInspectionJson = docker inspect --type container $ContainerId
if ($LASTEXITCODE -ne 0) { throw 'No existe el contenedor temporal confirmado.' }
$verificationInspection = ($verificationInspectionJson | ConvertFrom-Json)[0]
$verificationPorts = @($verificationInspection.NetworkSettings.Ports.'5432/tcp')
if ($verificationInspection.Id -ne $ContainerId -or
    $verificationInspection.Name -ne "/$verificationContainer" -or
    $verificationInspection.Config.Image -ne 'postgres:16' -or
    $verificationPorts.Count -ne 1 -or
    $verificationPorts[0].HostIp -ne '127.0.0.1' -or
    $verificationPorts[0].HostPort -ne '55432' -or
    $verificationInspection.Config.Env -notcontains 'POSTGRES_DB=facturacion_verificacion' -or
    $verificationInspection.Config.Env -notcontains 'POSTGRES_USER=facturacion_test') {
    throw 'El contenedor no coincide con la identidad, imagen, base y puerto aislados esperados.'
}
docker exec $verificationContainer pg_isready -U facturacion_test -d facturacion_verificacion
if ($LASTEXITCODE -ne 0) { throw 'La base temporal de verificación no está disponible.' }

$verificationEnvironment = @{
    SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:55432/facturacion_verificacion'
    SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'org.postgresql.Driver'
    SPRING_DATASOURCE_USERNAME = 'facturacion_test'
    SPRING_DATASOURCE_PASSWORD = 'verificacion-local'
    # El alias abreviado del JVM Windows no existe en algunas imágenes PostgreSQL.
    JAVA_TOOL_OPTIONS = (([Environment]::GetEnvironmentVariable('JAVA_TOOL_OPTIONS', 'Process') +
        ' -Duser.timezone=America/Argentina/Buenos_Aires').Trim())
}
$previousVerificationEnvironment = @{}
foreach ($verificationKey in $verificationEnvironment.Keys) {
    $previousVerificationEnvironment[$verificationKey] = [Environment]::GetEnvironmentVariable($verificationKey, 'Process')
}
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    foreach ($verificationKey in $verificationEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($verificationKey, $verificationEnvironment[$verificationKey], 'Process')
    }
    & .\gradlew.bat test --no-daemon --offline --rerun-tasks
    if ($LASTEXITCODE -ne 0) { throw 'Falló la suite PostgreSQL; consultar build/reports/tests/test.' }
} finally {
    foreach ($verificationKey in $verificationEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($verificationKey, $previousVerificationEnvironment[$verificationKey], 'Process')
    }
    Pop-Location
}
