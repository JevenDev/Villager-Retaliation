param(
    [Parameter(Mandatory = $true)]
    [string] $ArtifactDirectory
)

$ErrorActionPreference = 'Stop'

if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') {
    exit 0
}

if (-not (Test-Path -LiteralPath $ArtifactDirectory -PathType Container)) {
    exit 0
}

Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.ComTypes;

public static class RestartManagerLocks
{
    private const int ErrorMoreData = 234;
    private const int MaxAppName = 255;
    private const int MaxServiceName = 63;

    [StructLayout(LayoutKind.Sequential)]
    private struct RmUniqueProcess
    {
        public int ProcessId;
        public System.Runtime.InteropServices.ComTypes.FILETIME ProcessStartTime;
    }

    private enum RmAppType
    {
        Unknown = 0,
        MainWindow = 1,
        OtherWindow = 2,
        Service = 3,
        Explorer = 4,
        Console = 5,
        Critical = 1000
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct RmProcessInfo
    {
        public RmUniqueProcess Process;

        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = MaxAppName + 1)]
        public string AppName;

        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = MaxServiceName + 1)]
        public string ServiceShortName;

        public RmAppType ApplicationType;
        public uint AppStatus;
        public uint TerminalSessionId;

        [MarshalAs(UnmanagedType.Bool)]
        public bool Restartable;
    }

    [DllImport("rstrtmgr.dll", CharSet = CharSet.Unicode)]
    private static extern int RmStartSession(
        out uint sessionHandle,
        int sessionFlags,
        string sessionKey);

    [DllImport("rstrtmgr.dll")]
    private static extern int RmEndSession(uint sessionHandle);

    [DllImport("rstrtmgr.dll", CharSet = CharSet.Unicode)]
    private static extern int RmRegisterResources(
        uint sessionHandle,
        uint fileCount,
        string[] fileNames,
        uint applicationCount,
        IntPtr applications,
        uint serviceCount,
        string[] serviceNames);

    [DllImport("rstrtmgr.dll", CharSet = CharSet.Unicode)]
    private static extern int RmGetList(
        uint sessionHandle,
        out uint processInfoNeeded,
        ref uint processInfoCount,
        [In, Out] RmProcessInfo[] processInfo,
        ref uint rebootReasons);

    public static int[] Find(string[] paths)
    {
        uint sessionHandle;
        int result = RmStartSession(
            out sessionHandle,
            0,
            Guid.NewGuid().ToString("N"));

        if (result != 0)
            throw new InvalidOperationException("RmStartSession failed: " + result);

        try
        {
            result = RmRegisterResources(
                sessionHandle,
                (uint)paths.Length,
                paths,
                0,
                IntPtr.Zero,
                0,
                null);

            if (result != 0)
                throw new InvalidOperationException("RmRegisterResources failed: " + result);

            uint needed;
            uint count = 0;
            uint reasons = 0;
            result = RmGetList(sessionHandle, out needed, ref count, null, ref reasons);

            if (result == 0)
                return new int[0];
            if (result != ErrorMoreData)
                throw new InvalidOperationException("RmGetList failed: " + result);

            var processes = new RmProcessInfo[needed];
            count = needed;
            result = RmGetList(
                sessionHandle,
                out needed,
                ref count,
                processes,
                ref reasons);

            if (result != 0)
                throw new InvalidOperationException("RmGetList failed: " + result);

            var ids = new HashSet<int>();
            for (int i = 0; i < count; i++)
                ids.Add(processes[i].Process.ProcessId);

            var resultIds = new int[ids.Count];
            ids.CopyTo(resultIds);
            return resultIds;
        }
        finally
        {
            RmEndSession(sessionHandle);
        }
    }
}
'@

$artifactRoot = (Resolve-Path -LiteralPath $ArtifactDirectory).Path
$artifactPaths = @(
    Get-ChildItem -LiteralPath $artifactRoot -File -Filter '*.jar' |
        ForEach-Object { $_.FullName }
)

if ($artifactPaths.Count -eq 0) {
    exit 0
}

$lockingProcessIds = @([RestartManagerLocks]::Find($artifactPaths))
$blockedBy = @()

foreach ($lockingProcessId in $lockingProcessIds) {
    $process = Get-Process -Id $lockingProcessId -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        continue
    }

    if ($process.ProcessName -in @('java', 'javaw')) {
        Write-Host "Stopping stale Minecraft Java process $lockingProcessId because it is locking ModDevGradle artifacts."
        Stop-Process -Id $lockingProcessId -Force
        Wait-Process -Id $lockingProcessId -Timeout 10 -ErrorAction SilentlyContinue
        continue
    }

    $blockedBy += "$($process.ProcessName) ($lockingProcessId)"
}

if ($blockedBy.Count -gt 0) {
    throw "ModDevGradle artifacts are locked by a non-Java process: $($blockedBy -join ', '). Close that process and retry."
}
