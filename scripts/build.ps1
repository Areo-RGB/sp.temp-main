$ErrorActionPreference = 'Stop'
$projectRoot = "C:\Users\paul\Documents\.projects\sp.temp-main"
Set-Location -LiteralPath $projectRoot
npm run build
