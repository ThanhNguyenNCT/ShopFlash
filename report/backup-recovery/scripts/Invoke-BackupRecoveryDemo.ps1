[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^BKP-\d{8}T\d{6}-[A-F0-9]{8}$')]
    [string]$BackupId,

    [Parameter(Mandatory)]
    [ValidateSet('RecordBackupTiming', 'CaptureBaseline', 'SimulateDataLoss', 'RestoreOracle', 'RestoreRedisLive', 'Summarize')]
    [string]$Action,

    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_-]{0,31}$')]
    [string]$RunId = '01',

    [switch]$ConfirmDataLoss,
    [switch]$ConfirmOracleRestore,
    [switch]$ConfirmRedisLiveRestore
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$ReportRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ResultsRoot = Join-Path $ReportRoot 'results'
$OracleScriptsRoot = Join-Path $ReportRoot 'oracle'
$RedisScriptsRoot = Join-Path $ReportRoot 'redis'
$RunPrefix = "run-$RunId"
$RunResultsRoot = Join-Path $ResultsRoot $RunPrefix
$RunStatePath = Join-Path $RunResultsRoot 'state.json'
$RunSummaryPath = Join-Path $RunResultsRoot 'summary.md'

function Invoke-Compose {
    param([Parameter(Mandatory)][string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        # docker compose cp writes normal progress messages to stderr on Windows.
        # Do not convert those messages into terminating PowerShell errors; use the native exit code instead.
        $ErrorActionPreference = 'Continue'
        $output = & docker compose @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "Docker Compose failed: docker compose $($Arguments -join ' ')`n$($output -join [Environment]::NewLine)"
    }
    return @($output | ForEach-Object { $_.ToString() })
}

function Invoke-Docker {
    param([Parameter(Mandatory)][string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & docker @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "Docker failed: docker $($Arguments -join ' ')`n$($output -join [Environment]::NewLine)"
    }
    return @($output | ForEach-Object { $_.ToString() })
}

function Assert-DockerServicesRunning {
    Invoke-Compose @('version') | Out-Null
    foreach ($service in @('oracle', 'redis')) {
        $containerId = (Invoke-Compose @('ps', '-q', $service) | Where-Object { $_.Trim() }) | Select-Object -First 1
        if (-not $containerId) {
            throw "Service $service is not running. Run 'docker compose up -d --wait' first."
        }
    }
}

function Assert-ApplicationStopped {
    $portCommand = Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue
    if (-not $portCommand) {
        Write-Warning 'Ports 8080/5173 cannot be checked on this operating system. Ensure the backend and frontend are stopped yourself.'
        return
    }

    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
            Where-Object { $_.LocalPort -in @(8080, 5173) })
    if ($listeners.Count -gt 0) {
        throw 'The backend or frontend is still listening on port 8080/5173. Stop the application before deleting or restoring data.'
    }
}

function Get-BackupContext {
    $backupRoot = Join-Path $ProjectRoot 'backups'
    $backupDirectory = Join-Path $backupRoot $BackupId
    $dumpName = "shop-$BackupId.dmp"
    $oracleDump = Join-Path $backupDirectory "oracle\$dumpName"
    $redisRdb = Join-Path $backupDirectory 'redis\dump.rdb'
    $manifestPath = Join-Path $backupDirectory 'manifest.json'

    foreach ($required in @($backupDirectory, $oracleDump, $redisRdb, $manifestPath)) {
        if (-not (Test-Path -LiteralPath $required)) {
            throw "Required backup artifact was not found: $required"
        }
    }

    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.backupId -ne $BackupId -or $manifest.status -ne 'COMPLETED') {
        throw 'manifest.json is not a valid completed backup.'
    }

    $oracleHash = (Get-FileHash -LiteralPath $oracleDump -Algorithm SHA256).Hash.ToLowerInvariant()
    $redisHash = (Get-FileHash -LiteralPath $redisRdb -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($oracleHash -ne $manifest.oracle.sha256 -or $redisHash -ne $manifest.redis.sha256) {
        throw 'Artifact SHA-256 does not match the manifest. Recovery stopped to avoid using a modified backup.'
    }

    return [pscustomobject]@{
        Directory = $backupDirectory
        DumpName = $dumpName
        OracleDump = $oracleDump
        RedisRdb = $redisRdb
        Manifest = $manifest
        OracleSha256 = $oracleHash
        RedisSha256 = $redisHash
    }
}

function Copy-SupportScriptsToContainers {
    Invoke-Compose @('exec', '-T', 'oracle', 'mkdir', '-p', '/tmp/shopflash-backup-recovery') | Out-Null
    foreach ($name in @('collect-checkpoint.sql', 'simulate-data-loss.sql', 'disable-foreign-keys.sql', 'enable-foreign-keys.sql')) {
        Invoke-Compose @('cp', (Join-Path $OracleScriptsRoot $name), "oracle:/tmp/shopflash-backup-recovery/$name") | Out-Null
    }

    Invoke-Compose @('exec', '-T', 'redis', 'mkdir', '-p', '/tmp/shopflash-backup-recovery') | Out-Null
    foreach ($name in @('collect-checkpoint.lua', 'simulate-data-loss.lua')) {
        Invoke-Compose @('cp', (Join-Path $RedisScriptsRoot $name), "redis:/tmp/shopflash-backup-recovery/$name") | Out-Null
    }
}

function Invoke-OracleSqlFile {
    param([Parameter(Mandatory)][string]$FileName)

    $command = "exec sqlplus -s -L `"shop/`$`{SHOP_PASSWORD}@//localhost:1521/FREEPDB1`" @/tmp/shopflash-backup-recovery/$FileName"
    return Invoke-Compose @('exec', '-T', 'oracle', 'bash', '-lc', $command)
}

function Get-DataPumpDirectory {
    $command = @'
set -euo pipefail
sqlplus -s -L /nolog <<SQL
connect shop/${SHOP_PASSWORD}@//localhost:1521/FREEPDB1
whenever sqlerror exit failure
set heading off feedback off pages 0 verify off
select directory_path from all_directories where directory_name = 'DATA_PUMP_DIR';
exit success
SQL
'@
    $output = Invoke-Compose @('exec', '-T', 'oracle', 'bash', '-lc', $command)
    $directory = $output | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^/' } | Select-Object -First 1
    if (-not $directory) {
        throw 'DATA_PUMP_DIR could not be read from the Oracle container.'
    }
    return $directory
}

function Convert-OracleCheckpoint {
    param([Parameter(Mandatory)][string[]]$Lines)

    $counts = [ordered]@{}
    $samples = [System.Collections.Generic.List[string]]::new()
    foreach ($line in $Lines) {
        $text = $line.Trim()
        if ($text -match '^TABLE_COUNT\|([^|]+)\|(\d+)$') {
            $counts[$Matches[1]] = [long]$Matches[2]
        } elseif ($text -match '^PRODUCT_SAMPLE\|') {
            $samples.Add($text)
        }
    }
    if ($counts.Count -eq 0) {
        throw "Oracle checkpoint could not be read.`n$($Lines -join [Environment]::NewLine)"
    }
    return [pscustomobject]@{
        tableCounts = $counts
        productSamples = @($samples | Sort-Object)
        raw = @($Lines)
    }
}

function Get-OracleCheckpoint {
    return Convert-OracleCheckpoint (Invoke-OracleSqlFile 'collect-checkpoint.sql')
}

function Get-TableCountEntries {
    param([Parameter(Mandatory)]$TableCounts)

    if ($TableCounts -is [System.Collections.IDictionary]) {
        return @($TableCounts.GetEnumerator() | ForEach-Object {
                [pscustomobject]@{ Name = [string]$_.Key; Value = $_.Value }
            })
    }
    return @($TableCounts.PSObject.Properties | ForEach-Object {
            [pscustomobject]@{ Name = $_.Name; Value = $_.Value }
        })
}

function Get-TableCountValue {
    param(
        [Parameter(Mandatory)]$TableCounts,
        [Parameter(Mandatory)][string]$TableName
    )

    $entry = Get-TableCountEntries $TableCounts | Where-Object { $_.Name -eq $TableName } | Select-Object -First 1
    if ($null -eq $entry) {
        throw "Oracle checkpoint does not contain table $TableName."
    }
    return [long]$entry.Value
}

function Convert-RedisCheckpoint {
    param([Parameter(Mandatory)][string[]]$Lines)

    $dbSize = $null
    $productDocuments = $null
    foreach ($line in $Lines) {
        $text = $line.Trim()
        if ($text -match '^DBSIZE\|(\d+)$') {
            $dbSize = [long]$Matches[1]
        } elseif ($text -match '^PRODUCT_DOCUMENTS\|(\d+)$') {
            $productDocuments = [long]$Matches[1]
        }
    }
    if ($null -eq $dbSize -or $null -eq $productDocuments) {
        throw "Redis checkpoint could not be read.`n$($Lines -join [Environment]::NewLine)"
    }
    return [pscustomobject]@{
        databaseKeys = $dbSize
        productDocuments = $productDocuments
        raw = @($Lines)
    }
}

function Get-LiveRedisCheckpoint {
    $output = Invoke-Compose @('exec', '-T', 'redis', 'redis-cli', '--raw', '--eval', '/tmp/shopflash-backup-recovery/collect-checkpoint.lua')
    return Convert-RedisCheckpoint $output
}

function Get-RedisContainerDetails {
    $containerId = (Invoke-Compose @('ps', '-q', 'redis') | Where-Object { $_.Trim() } | Select-Object -First 1)
    if (-not $containerId) {
        throw 'The Redis service is not running.'
    }
    $inspection = ((Invoke-Docker @('inspect', $containerId)) -join [Environment]::NewLine) | ConvertFrom-Json
    $dataMount = @($inspection[0].Mounts | Where-Object { $_.Destination -eq '/data' }) | Select-Object -First 1
    $volumeName = if ($dataMount) { [string]$dataMount.Name } else { '' }
    $image = [string]$inspection[0].Config.Image
    if (-not $volumeName -or -not $image) {
        throw 'Could not resolve the Redis data volume or image from the running container.'
    }
    return [pscustomobject]@{ containerId = $containerId; volumeName = $volumeName; image = $image }
}

function Wait-ForRedisContainer {
    param([Parameter(Mandatory)][string]$ContainerName)

    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        try {
            $ping = Invoke-Docker @('exec', $ContainerName, 'sh', '-lc', 'unset REDISCLI_AUTH; exec redis-cli --raw PING')
            if (($ping | Select-Object -Last 1).Trim() -eq 'PONG') {
                return
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    throw "Redis container $ContainerName did not start within 30 seconds."
}

function Rebuild-AofFromRdb {
    param(
        [Parameter(Mandatory)]$Details,
        [Parameter(Mandatory)][string]$RdbDirectory
    )

    $containerName = "shopflash-aof-rebuild-$([guid]::NewGuid().ToString('N').Substring(0, 8))".ToLowerInvariant()
    $started = $false
    try {
        # Start from dump.rdb without AOF, then let Redis generate a fresh AOF from loaded keys.
        Invoke-Docker @('run', '-d', '--rm', '--name', $containerName, '--network', 'none',
            '-v', "$($Details.volumeName):/data",
            '-e', 'REDIS_DATA_DIR=/data',
            '-e', 'REDIS_ARGS=--appendonly no',
            '-e', 'REDISCLI_AUTH=',
            $Details.image) | Out-Null
        $started = $true
        Wait-ForRedisContainer -ContainerName $containerName

        $loadedKeys = [long](Invoke-Docker @('exec', $containerName, 'sh', '-lc', 'unset REDISCLI_AUTH; exec redis-cli --raw DBSIZE') | Select-Object -Last 1)
        if ($loadedKeys -eq 0) {
            throw 'The RDB did not load any keys in the AOF rebuild container.'
        }
        Invoke-Docker @('exec', $containerName, 'sh', '-lc', 'unset REDISCLI_AUTH; exec redis-cli --raw CONFIG SET appendonly yes') | Out-Null

        $aofReady = $false
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            try {
                $info = Invoke-Docker @('exec', $containerName, 'sh', '-lc', 'unset REDISCLI_AUTH; exec redis-cli --raw INFO persistence')
                $rewriteFinished = $info -contains 'aof_rewrite_in_progress:0'
                Invoke-Docker @('exec', $containerName, 'sh', '-lc', 'test -s /data/appendonlydir/appendonly.aof.manifest && find /data/appendonlydir -type f -name "appendonly.aof.*.base.rdb" -size +0c -print -quit | grep -q .') | Out-Null
                if ($rewriteFinished) {
                    $aofReady = $true
                    break
                }
            } catch {
                Start-Sleep -Seconds 1
            }
        }
        if (-not $aofReady) {
            throw 'Redis did not finish rebuilding a non-empty AOF from the RDB within 60 seconds.'
        }
    } finally {
        if ($started) {
            try { Invoke-Docker @('stop', $containerName) | Out-Null } catch { Write-Warning "Could not stop $containerName automatically." }
        }
    }
}

function Get-OracleSignature {
    param([Parameter(Mandatory)]$Checkpoint)

    $rows = foreach ($property in (Get-TableCountEntries $Checkpoint.tableCounts)) {
        "TABLE_COUNT|$($property.Name)|$($property.Value)"
    }
    return @($rows + @($Checkpoint.productSamples) | Sort-Object)
}

function Get-RedisSignature {
    param([Parameter(Mandatory)]$Checkpoint)

    return @(
        "DBSIZE|$($Checkpoint.databaseKeys)",
        "PRODUCT_DOCUMENTS|$($Checkpoint.productDocuments)"
    ) | Sort-Object
}

function Compare-Checkpoints {
    param(
        [Parameter(Mandatory)]$Expected,
        [Parameter(Mandatory)]$Actual,
        [Parameter(Mandatory)][ValidateSet('Oracle', 'Redis')][string]$Source
    )

    $expectedRows = if ($Source -eq 'Oracle') { Get-OracleSignature $Expected } else { Get-RedisSignature $Expected }
    $actualRows = if ($Source -eq 'Oracle') { Get-OracleSignature $Actual } else { Get-RedisSignature $Actual }
    $differences = @(Compare-Object -ReferenceObject $expectedRows -DifferenceObject $actualRows |
            ForEach-Object { "$($_.SideIndicator) $($_.InputObject)" })
    return [pscustomobject]@{
        source = $Source
        matchesBaseline = $differences.Count -eq 0
        differences = $differences
    }
}

function Read-RunState {
    if (-not (Test-Path -LiteralPath $RunStatePath)) {
        return [pscustomobject]@{
            runId = $RunId
            backupId = $BackupId
        }
    }
    return Get-Content -LiteralPath $RunStatePath -Raw | ConvertFrom-Json
}

function Write-RunState {
    param([Parameter(Mandatory)]$State)

    New-Item -ItemType Directory -Path $RunResultsRoot -Force | Out-Null
    $State | Add-Member -NotePropertyName updatedAt -NotePropertyValue (Get-Date).ToString('o') -Force
    # UTF8 works in both Windows PowerShell 5.1 and PowerShell 7.
    $State | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $RunStatePath -Encoding UTF8
    return $RunStatePath
}

function Save-RunStage {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)]$Value
    )

    $state = Read-RunState
    $state | Add-Member -NotePropertyName $Name -NotePropertyValue $Value -Force
    Write-RunState $state | Out-Null
    return $state
}

function Get-RunStage {
    param([Parameter(Mandatory)][string]$Name)

    $state = Read-RunState
    $property = $state.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        throw "Missing stage '$Name' in $RunStatePath."
    }
    return $property.Value
}

function Get-OptionalDurationMilliseconds {
    param([Parameter(Mandatory)]$Result)

    $property = $Result.PSObject.Properties['durationMilliseconds']
    if ($null -eq $property -or $null -eq $property.Value) {
        return $null
    }
    return [long]$property.Value
}

function Format-Duration {
    param([Parameter(Mandatory)][long]$Milliseconds)

    return ('{0} ms ({1:F3} s)' -f $Milliseconds, ($Milliseconds / 1000.0))
}

function Record-BackupTiming {
    $context = Get-BackupContext
    try {
        $startedAt = [DateTimeOffset]::Parse([string]$context.Manifest.createdAt, [Globalization.CultureInfo]::InvariantCulture)
        $completedAt = [DateTimeOffset]::Parse([string]$context.Manifest.completedAt, [Globalization.CultureInfo]::InvariantCulture)
    } catch {
        throw 'Backup timing could not be read from manifest.json.'
    }

    $durationMilliseconds = [long][math]::Round(($completedAt - $startedAt).TotalMilliseconds)
    if ($durationMilliseconds -lt 0) {
        throw 'Backup timing in manifest.json is invalid: completedAt is earlier than createdAt.'
    }

    $result = [pscustomobject]@{
        backupId = $BackupId
        startedAt = $startedAt.ToString('o')
        completedAt = $completedAt.ToString('o')
        durationMilliseconds = $durationMilliseconds
        measurement = 'Backend runner duration from createdAt immediately before runner.create() to completedAt immediately after artifact creation.'
    }
    Save-RunStage -Name 'backupTiming' -Value $result | Out-Null
    Write-Output "Backup duration was written to $RunStatePath."
}

function Get-BaselineResult {
    try {
        return Get-RunStage 'beforeLoss'
    } catch {
        throw "Run CaptureBaseline before deleting data. $($_.Exception.Message)"
    }
}

function Assert-BaselineMatchesManifest {
    param([Parameter(Mandatory)]$Baseline, [Parameter(Mandatory)]$Manifest)

    $oracle = $Baseline.oracle
    $redis = $Baseline.redis
    $expected = @{
        CATEGORIES = [long]$Manifest.baseline.oracle.categories
        PRODUCTS = [long]$Manifest.baseline.oracle.products
        PRODUCT_INVENTORY = [long]$Manifest.baseline.oracle.inventoryRows
    }
    foreach ($table in $expected.Keys) {
        $actual = Get-TableCountValue -TableCounts $oracle.tableCounts -TableName $table
        if ($actual -ne $expected[$table]) {
            throw "Current data for $table ($actual) does not match the manifest ($($expected[$table])). Create a new backup before running the recovery demo."
        }
    }
    if ([long]$redis.productDocuments -ne [long]$Manifest.baseline.redis.productDocuments -or
        [long]$redis.databaseKeys -ne [long]$Manifest.baseline.redis.totalKeys) {
        throw 'The current Redis checkpoint does not match the manifest. Create a new backup before running the recovery demo.'
    }
}

function Capture-Baseline {
    $context = Get-BackupContext
    Copy-SupportScriptsToContainers
    $oracle = Get-OracleCheckpoint
    $redis = Get-LiveRedisCheckpoint
    $result = [pscustomobject]@{
        backupId = $BackupId
        capturedAt = (Get-Date).ToString('o')
        oracle = $oracle
        redis = $redis
    }
    Assert-BaselineMatchesManifest -Baseline $result -Manifest $context.Manifest
    Save-RunStage -Name 'beforeLoss' -Value $result | Out-Null
    Write-Output "Baseline was written to $RunStatePath."
}

function Simulate-DataLoss {
    if (-not $ConfirmDataLoss) {
        throw 'This step deletes all Oracle business data and the entire Redis keyspace. Run it again with -ConfirmDataLoss after stopping the application.'
    }
    $context = Get-BackupContext
    $baseline = Get-BaselineResult
    Assert-BaselineMatchesManifest -Baseline $baseline -Manifest $context.Manifest
    Assert-ApplicationStopped
    Copy-SupportScriptsToContainers

    Invoke-OracleSqlFile 'simulate-data-loss.sql' | Out-Null
    Invoke-Compose @('exec', '-T', 'redis', 'redis-cli', '--raw', '--eval', '/tmp/shopflash-backup-recovery/simulate-data-loss.lua') | Out-Null

    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $keys = [long](Invoke-Compose @('exec', '-T', 'redis', 'redis-cli', '--raw', 'DBSIZE') | Select-Object -Last 1)
        if ($keys -eq 0) { break }
        Start-Sleep -Seconds 1
    }
    if ($keys -ne 0) {
        throw "Redis still has $keys keys after 30 seconds; recovery will not continue."
    }

    $oracle = Get-OracleCheckpoint
    $redis = Get-LiveRedisCheckpoint
    $oracleRows = @(Get-TableCountEntries $oracle.tableCounts | ForEach-Object { [long]$_.Value })
    if (($oracleRows | Measure-Object -Sum).Sum -ne 0 -or $redis.databaseKeys -ne 0) {
        throw 'The simulated data loss did not leave both sources empty.'
    }
    $result = [pscustomobject]@{
        backupId = $BackupId
        capturedAt = (Get-Date).ToString('o')
        oracle = $oracle
        redis = $redis
    }
    Save-RunStage -Name 'afterLoss' -Value $result | Out-Null
    Write-Output 'Oracle and Redis are confirmed empty. You can run RestoreOracle and RestoreRedisLive.'
}

function Restore-Oracle {
    if (-not $ConfirmOracleRestore) {
        throw 'This step reloads all Oracle data from Data Pump. Run it again with -ConfirmOracleRestore after stopping the application.'
    }
    $context = Get-BackupContext
    $baseline = Get-BaselineResult
    Assert-ApplicationStopped
    Copy-SupportScriptsToContainers

    # Starts after preflight and measures the controlled restore plus its checkpoint verification.
    $restoreTimer = [Diagnostics.Stopwatch]::StartNew()
    $dataPumpDirectory = Get-DataPumpDirectory
    $importLogName = "shop-$BackupId-import.log"
    Invoke-Compose @('cp', $context.OracleDump, "oracle:$dataPumpDirectory/$($context.DumpName)") | Out-Null
    $foreignKeysDisabled = $false
    try {
        Invoke-OracleSqlFile 'disable-foreign-keys.sql' | Out-Null
        $foreignKeysDisabled = $true

        $command = @'
set -euo pipefail
impdp "shop/${{SHOP_PASSWORD}}@//localhost:1521/FREEPDB1" DIRECTORY=DATA_PUMP_DIR DUMPFILE={0} LOGFILE={1} CONTENT=DATA_ONLY TABLE_EXISTS_ACTION=TRUNCATE METRICS=YES
'@ -f $context.DumpName, $importLogName
        Invoke-Compose @('exec', '-T', 'oracle', 'bash', '-lc', $command) | Out-Null
    } finally {
        if ($foreignKeysDisabled) {
            Invoke-OracleSqlFile 'enable-foreign-keys.sql' | Out-Null
        }
    }
    $oracle = Get-OracleCheckpoint
    $redis = Get-LiveRedisCheckpoint
    $comparison = Compare-Checkpoints -Expected $baseline.oracle -Actual $oracle -Source Oracle
    $restoreTimer.Stop()
    $result = [pscustomobject]@{
        backupId = $BackupId
        restoredAt = (Get-Date).ToString('o')
        durationMilliseconds = [long]$restoreTimer.ElapsedMilliseconds
        artifactSha256 = $context.OracleSha256
        oracle = $oracle
        comparison = $comparison
    }
    Save-RunStage -Name 'oracleRecovery' -Value $result | Out-Null
    if (-not $comparison.matchesBaseline) {
        throw 'Oracle import ran but the checkpoint does not match the baseline. Review results before restarting the application.'
    }
    Write-Output "Oracle was restored and its checkpoint matches the baseline in $(Format-Duration $result.durationMilliseconds)."
}

function Restore-RedisLive {
    if (-not $ConfirmRedisLiveRestore) {
        throw 'This step replaces the live Redis persistence state with the backup RDB. Run it again with -ConfirmRedisLiveRestore after stopping the application.'
    }
    $context = Get-BackupContext
    $baseline = Get-BaselineResult
    Assert-ApplicationStopped
    Copy-SupportScriptsToContainers

    $beforeRestore = Get-LiveRedisCheckpoint
    if ($beforeRestore.databaseKeys -ne 0) {
        throw "Live Redis still has $($beforeRestore.databaseKeys) keys. This controlled action only restores immediately after SimulateDataLoss, so it will not overwrite newer data."
    }

    # Starts after the empty-keyspace safety check and measures restore plus checkpoint verification.
    $restoreTimer = [Diagnostics.Stopwatch]::StartNew()
    $details = Get-RedisContainerDetails
    $rdbDirectory = (Resolve-Path (Split-Path -Parent $context.RedisRdb)).Path
    $archiveName = "recovery-snapshots/$RunPrefix-$BackupId-before-live-restore-$(Get-Date -Format 'yyyyMMddTHHmmss')"
    $helperCommand = @'
set -eu
archive="/data/{0}"
mkdir -p "$archive"
if [ -d /data/appendonlydir ]; then cp -a /data/appendonlydir "$archive/appendonlydir"; fi
if [ -f /data/dump.rdb ]; then cp -a /data/dump.rdb "$archive/dump.rdb"; fi
rm -rf /data/appendonlydir
rm -f /data/dump.rdb
cp /backup/dump.rdb /data/dump.rdb
test -s /data/dump.rdb
'@ -f $archiveName

    $redisStopped = $false
    try {
        Invoke-Compose @('stop', 'redis') | Out-Null
        $redisStopped = $true
        Invoke-Docker @('run', '--rm', '--entrypoint', 'sh',
            '-v', "$($details.volumeName):/data",
            '-v', "${rdbDirectory}:/backup:ro",
            $details.image, '-lc', $helperCommand) | Out-Null
        Rebuild-AofFromRdb -Details $details -RdbDirectory $rdbDirectory
        Invoke-Compose @('up', '-d', '--wait', 'redis') | Out-Null
        $redisStopped = $false
    } finally {
        if ($redisStopped) {
            Write-Warning 'Redis is still stopped because the persistence replacement failed. The previous files are retained under the recovery-snapshots directory in the Redis volume.'
        }
    }

    Copy-SupportScriptsToContainers
    $redis = Get-LiveRedisCheckpoint
    $oracle = Get-OracleCheckpoint
    $comparison = Compare-Checkpoints -Expected $baseline.redis -Actual $redis -Source Redis
    $restoreTimer.Stop()
    $result = [pscustomobject]@{
        backupId = $BackupId
        restoredAt = (Get-Date).ToString('o')
        durationMilliseconds = [long]$restoreTimer.ElapsedMilliseconds
        artifactSha256 = $context.RedisSha256
        liveRedisVolume = $details.volumeName
        archivedPreviousPersistence = "/data/$archiveName"
        recoveryMethod = 'Loaded RDB with AOF disabled, rebuilt AOF from loaded keys, then started the live Redis service.'
        redis = $redis
        comparison = $comparison
    }
    Save-RunStage -Name 'redisRecovery' -Value $result | Out-Null
    if (-not $comparison.matchesBaseline) {
        throw 'Live Redis started from the RDB but its checkpoint does not match the baseline. Review results before restarting the application.'
    }
    Write-Output "Live Redis was restored from the RDB and its checkpoint matches the baseline in $(Format-Duration $result.durationMilliseconds)."
}

function Write-Summary {
    $state = Read-RunState
    foreach ($stage in @('beforeLoss', 'afterLoss', 'oracleRecovery', 'redisRecovery')) {
        if ($null -eq $state.PSObject.Properties[$stage] -or $null -eq $state.PSObject.Properties[$stage].Value) {
            throw "Missing stage '$stage' in $RunStatePath. A summary can be created only after all recovery stages finish."
        }
    }
    $oracle = $state.oracleRecovery
    $redis = $state.redisRecovery
    $lines = [System.Collections.Generic.List[string]]::new()
    foreach ($line in @(
        "# Run $RunId - Recovery conclusion",
        '',
        ('- Backup ID: `{0}`' -f $BackupId),
        "- Oracle Data Pump: $(if ($oracle.comparison.matchesBaseline) { 'Recovery succeeded' } else { 'Does not match baseline' })",
        "- Redis RDB: $(if ($redis.comparison.matchesBaseline) { 'Recovery succeeded' } else { 'Does not match baseline' })"
    )) {
        $lines.Add($line)
    }
    $oracleDuration = Get-OptionalDurationMilliseconds $oracle
    $redisDuration = Get-OptionalDurationMilliseconds $redis
    if ($null -ne $oracleDuration) {
        $lines.Add("- Oracle technical restore duration: **$(Format-Duration $oracleDuration)**")
    }
    if ($null -ne $redisDuration) {
        $lines.Add("- Redis technical restore duration: **$(Format-Duration $redisDuration)**")
    }
    if ($null -ne $oracleDuration -and $null -ne $redisDuration) {
        $lines.Add("- Combined technical restore duration: **$(Format-Duration ($oracleDuration + $redisDuration))**")
    }
    if ($null -ne $state.PSObject.Properties['backupTiming'] -and $null -ne $state.backupTiming) {
        $backupDuration = Get-OptionalDurationMilliseconds $state.backupTiming
        if ($null -ne $backupDuration) {
            $lines.Add("- Backup creation duration: **$(Format-Duration $backupDuration)**")
        }
    }
    $lines.Add('')
    $lines.Add('Durations are controlled technical measurements; they exclude manual delay between commands and are not RTO.')
    $lines.Add('All raw metrics and comparisons are kept in state.json for this run.')
    New-Item -ItemType Directory -Path $RunResultsRoot -Force | Out-Null
    $lines | Set-Content -LiteralPath $RunSummaryPath -Encoding UTF8
    Write-Output "Summary was written to $RunSummaryPath."
}

Push-Location $ProjectRoot
try {
    if ($Action -ne 'RecordBackupTiming') {
        Assert-DockerServicesRunning
    }
    switch ($Action) {
        'RecordBackupTiming' { Record-BackupTiming }
        'CaptureBaseline' { Capture-Baseline }
        'SimulateDataLoss' { Simulate-DataLoss }
        'RestoreOracle' { Restore-Oracle }
        'RestoreRedisLive' { Restore-RedisLive }
        'Summarize' { Write-Summary }
    }
} finally {
    Pop-Location
}
