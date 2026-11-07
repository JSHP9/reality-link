$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$modelDir = Join-Path $projectDir 'models'
New-Item -ItemType Directory -Force $modelDir | Out-Null
$modelPath = Join-Path $modelDir 'mobilenetv2-12.onnx'
$expected = 'C0C3F76D93FA3FD6580652A45618618A220FCED18BABF65774ED169DE0432AD5'
if ((Test-Path -LiteralPath $modelPath) -and ((Get-FileHash -LiteralPath $modelPath -Algorithm SHA256).Hash -eq $expected)) {
    Write-Host 'Model already verified.'
    exit 0
}
$tempPath = Join-Path $modelDir ('download-' + [Guid]::NewGuid().ToString('N') + '.part')
try {
    Invoke-WebRequest 'https://huggingface.co/onnxmodelzoo/mobilenetv2-12/resolve/23a2a25eaaa6fcd61865414c8a1acf13dda66b7a/mobilenetv2-12.onnx?download=true' -OutFile $tempPath
    if ((Get-FileHash -LiteralPath $tempPath -Algorithm SHA256).Hash -ne $expected) { throw 'Model SHA-256 mismatch; existing model was not replaced.' }
    Move-Item -LiteralPath $tempPath -Destination $modelPath -Force
    Write-Host 'Model downloaded and SHA-256 verified.'
} finally {
    if (Test-Path -LiteralPath $tempPath) { Remove-Item -LiteralPath $tempPath }
}
