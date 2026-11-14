<#
    Carica nei segreti del repository GitHub la chiave di firma della release,
    letta da .signing\keystore.properties e dal file keystore indicato lì.
    Serve una volta sola (o quando la chiave cambia); richiede GitHub CLI autenticata.

    Uso:  powershell -ExecutionPolicy Bypass -File tools\set-signing-secrets.ps1
    Autore: Bruno Leonardi
#>
param(
    [string]$Repository = "brn78/TabletWake"
)

$ErrorActionPreference = "Stop"

#region Lettura della configurazione di firma
$root = Split-Path -Parent $PSScriptRoot
$signingDir = Join-Path $root ".signing"
$propertiesFile = Join-Path $signingDir "keystore.properties"
if (-not (Test-Path $propertiesFile)) {
    throw "File non trovato: $propertiesFile"
}

$properties = @{}
foreach ($line in Get-Content $propertiesFile) {
    if ($line -match '^\s*([^#=\s][^=]*?)\s*=\s*(.*?)\s*$') {
        $properties[$Matches[1]] = $Matches[2]
    }
}
foreach ($key in "storeFile", "storePassword", "keyAlias", "keyPassword") {
    if (-not $properties[$key]) {
        throw "Valore mancante in keystore.properties: $key"
    }
}

$keystoreFile = Join-Path $signingDir $properties["storeFile"]
if (-not (Test-Path $keystoreFile)) {
    throw "Keystore non trovato: $keystoreFile"
}
#endregion

#region Invio dei segreti
function Set-RepositorySecret([string]$Name, [string]$Value) {
    gh secret set $Name --repo $Repository --body $Value
    if ($LASTEXITCODE -ne 0) {
        throw "Impossibile impostare il segreto $Name"
    }
}

Set-RepositorySecret "ANDROID_KEYSTORE_BASE64" ([Convert]::ToBase64String([IO.File]::ReadAllBytes($keystoreFile)))
Set-RepositorySecret "ANDROID_KEYSTORE_PASSWORD" $properties["storePassword"]
Set-RepositorySecret "ANDROID_KEY_ALIAS" $properties["keyAlias"]
Set-RepositorySecret "ANDROID_KEY_PASSWORD" $properties["keyPassword"]

Write-Host "Segreti di firma impostati su $Repository."
#endregion
