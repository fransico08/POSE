# Shared by the Supabase setup and offline checks; does not access a database.
function Get-PoseMigrationVersions {
    param([Parameter(Mandatory = $true)][string]$RepositoryPath)

    $poseSqlDir = Join-Path $RepositoryPath 'backend\src\main\resources\db\migration'
    $poseJavaDir = Join-Path $RepositoryPath 'backend\src\main\java\db\migration'
    if (-not (Test-Path -LiteralPath $poseSqlDir -PathType Container)) {
        throw 'Flyway SQL migration directory is missing.'
    }

    $poseSeen = New-Object 'System.Collections.Generic.Dictionary[int,string]'
    $poseSeen.Add(1, 'baseline V1')
    $poseFiles = @(Get-ChildItem -LiteralPath $poseSqlDir -Filter 'V*__*.sql' -File)
    if (Test-Path -LiteralPath $poseJavaDir -PathType Container) {
        $poseFiles += @(Get-ChildItem -LiteralPath $poseJavaDir -Filter 'V*__*.java' -File)
    }
    foreach ($poseFile in $poseFiles) {
        if ($poseFile.Name -notmatch '^V(\d+)__.+\.(sql|java)$') {
            throw ('Unsupported Flyway migration filename: ' + $poseFile.Name)
        }
        $poseVersion = [int]$Matches[1]
        if ($poseSeen.ContainsKey($poseVersion)) {
            throw ('Duplicate Flyway migration version ' + $poseVersion + ': ' + $poseSeen[$poseVersion] + ' and ' + $poseFile.FullName)
        }
        $poseSeen.Add($poseVersion, $poseFile.FullName)
    }
    foreach ($poseVersion in @($poseSeen.Keys | Sort-Object)) { [string]$poseVersion }
}
