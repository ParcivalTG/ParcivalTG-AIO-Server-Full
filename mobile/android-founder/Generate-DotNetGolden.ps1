# Nonproduction interoperability vector. Scalars 1 and 2 are public test fixtures.
$ErrorActionPreference = 'Stop'
function Bytes([string]$hex) { return [Convert]::FromHexString($hex) }
function B64([byte[]]$value) { return [Convert]::ToBase64String($value).TrimEnd('=').Replace('+','-').Replace('/','_') }
function TestKey([byte]$scalar,[string]$x,[string]$y) {
    $parameter = [System.Security.Cryptography.ECParameters]::new()
    $parameter.Curve = [System.Security.Cryptography.ECCurve]::CreateFromFriendlyName('nistP256')
    $parameter.D = [byte[]]::new(32)
    $parameter.D[31] = $scalar
    $point = [System.Security.Cryptography.ECPoint]::new()
    $point.X = Bytes $x
    $point.Y = Bytes $y
    $parameter.Q = $point
    return [System.Security.Cryptography.ECDiffieHellman]::Create($parameter)
}
function Hkdf([byte[]]$shared,[string]$id,[string]$info) {
    $extract = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($id))
    $prk = $extract.ComputeHash($shared)
    $expand = [System.Security.Cryptography.HMACSHA256]::new($prk)
    $inputBytes = [byte[]]([Text.Encoding]::UTF8.GetBytes($info) + [byte]1)
    $result = $expand.ComputeHash($inputBytes)
    $extract.Dispose()
    $expand.Dispose()
    return ,$result
}
function Seal([byte[]]$key,[byte[]]$nonce,[string]$plain,[string]$aad) {
    $plainBytes = [Text.Encoding]::UTF8.GetBytes($plain)
    $cipher = [byte[]]::new($plainBytes.Length)
    $tag = [byte[]]::new(16)
    $aes = [System.Security.Cryptography.AesGcm]::new($key,16)
    $aes.Encrypt($nonce,$plainBytes,$cipher,$tag,[Text.Encoding]::UTF8.GetBytes($aad))
    $aes.Dispose()
    return @{ cipher = B64 $cipher; tag = B64 $tag; length = $cipher.Length }
}
$client = TestKey 1 '6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296' '4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5'
$server = TestKey 2 '7cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc47669978' '07775510db8ed040293d9ac69f7430dbba7dade63ce982299e04b79d227873d1'
try {
    $clientSpki = B64 $client.ExportSubjectPublicKeyInfo()
    $serverSpki = B64 $server.ExportSubjectPublicKeyInfo()
    $shared = $client.DeriveKeyMaterial($server.PublicKey)
    $requestId = '11111111-2222-3333-4444-555555555555'
    $created = 1700000000000L
    $expires = 1700000030000L
    $plain = '{"action":"windows.objective.submit","args":{"testOnly":true}}'
    $responsePlain = '{"schema":"aio.private-gateway.receipt.v1","requestId":"11111111-2222-3333-4444-555555555555","action":"windows.objective.submit","success":true,"result":{"state":"QUEUED","testOnly":true}}'
    $requestNonce = Bytes '000102030405060708090a0b'
    $responseNonce = Bytes '0c0d0e0f1011121314151617'
    $requestAad = [string]::Join([char]10, @($requestId,$clientSpki,[string]$created,[string]$expires))
    $responseAad = [string]::Join([char]10, @($requestId,'response',$serverSpki))
    $request = Seal (Hkdf $shared $requestId 'aio.private-gateway.v1.c2s') $requestNonce $plain $requestAad
    $response = Seal (Hkdf $shared $requestId 'aio.private-gateway.v1.s2c') $responseNonce $responsePlain $responseAad
    $requestNonceB64 = B64 $requestNonce
    $responseNonceB64 = B64 $responseNonce
    $lines = @(
        '# Public test-only fixture generated independently with .NET; not a pairing identity.'
        "requestId=$requestId"
        "createdAtUnixMs=$created"
        "expiresAtUnixMs=$expires"
        "serverPublicKeyB64=$serverSpki"
        "clientPublicKeyB64=$clientSpki"
        "requestNonceB64=$requestNonceB64"
        "requestCiphertextB64=$($request.cipher)"
        "requestTagB64=$($request.tag)"
        "responseNonceB64=$responseNonceB64"
        "responseCiphertextB64=$($response.cipher)"
        "responseTagB64=$($response.tag)"
        "responseCiphertextBytes=$($response.length)"
        "commandPlain=$plain"
        "responsePlain=$responsePlain"
    )
    $taskResourceDir = Join-Path $PSScriptRoot 'app\src\test\resources'
    New-Item -ItemType Directory -Path $taskResourceDir -Force | Out-Null
    [IO.File]::WriteAllLines((Join-Path $taskResourceDir 'dotnet-e2e-golden.properties'),$lines,[Text.UTF8Encoding]::new($false))
    Write-Output 'Generated nonproduction .NET golden vector; no build or live gateway call.'
} finally {
    $client.Dispose()
    $server.Dispose()
}

