#Requires -Version 7.0
<#
.SYNOPSIS
    Opt-in, real-OpenAI end-to-end gameplay smoke test through the Stage 14 REST API.

.DESCRIPTION
    Plays a short scripted session against a LOCALLY running server that has real AI enabled:
    creates a run, walks from the Last Lantern into the Hollow Chapel with slash commands, attacks
    a visible enemy in natural language, defends against its attack in natural language, reloads,
    and replays an earlier turn. Prints the player-facing writing and a summary of the AI roles.

    It fails visibly if any required AI narration fell back. It never prints the invite code, the
    run token or the API key (the key lives only in the server's environment; this script never
    reads it). Not part of the Maven test suite.

.PARAMETER BaseUrl
    The local server. Only localhost / 127.0.0.1 / ::1 are accepted.
.PARAMETER InviteCode
    One of the server's GAME_INVITE_CODES. Defaults to $env:GAME_SMOKE_INVITE_CODE, else prompts (hidden).
.PARAMETER ServerLog
    Optional path to the server's log file (started with --logging.file.name=...), used for per-role
    model, latency and token usage. Only lines written during this run are read.
.PARAMETER ExpectedModel
    Model every logged AI call must have used (checked only with -ServerLog).
#>
[CmdletBinding()]
param(
    [string] $BaseUrl = 'http://localhost:8080',
    [string] $InviteCode = $env:GAME_SMOKE_INVITE_CODE,
    [string] $ServerLog,
    [string] $ExpectedModel = 'gpt-4.1-mini',
    [ValidateRange(1, 20)] [int] $MaxNavigationTurns = 8,
    [ValidateRange(0, 10)] [int] $MaxEnemyWaitTurns = 3
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# ---------------------------------------------------------------- state and output helpers

$script:RunId = $null
$script:LastVersion = $null
$script:Stage = 'preflight'
$script:Turns = [System.Collections.Generic.List[object]]::new()
$script:Checks = [System.Collections.Generic.List[object]]::new()
$script:LogStart = 0L

function Write-Section([string] $title) {
    Write-Host ''
    Write-Host ('=' * 78) -ForegroundColor DarkGray
    Write-Host "  $title" -ForegroundColor Cyan
    Write-Host ('=' * 78) -ForegroundColor DarkGray
}

function Write-Prose([string] $label, [string] $text, [string] $source) {
    Write-Host ''
    Write-Host "  $label" -NoNewline -ForegroundColor Yellow
    if ($source) { Write-Host "  [$source]" -ForegroundColor DarkGray } else { Write-Host '' }
    foreach ($line in ($text -split "`n")) { Write-Host "    $line" }
}

# kind: VERIFIED (the API response or a reload proves it) or INFERRED (consistent with, not proven by, the API)
function Add-Check([string] $kind, [string] $what) {
    $script:Checks.Add([pscustomobject]@{ Stage = $script:Stage; Kind = $kind; Check = $what })
    $color = if ($kind -eq 'VERIFIED') { 'Green' } else { 'DarkYellow' }
    Write-Host "    [$kind] $what" -ForegroundColor $color
}

function Fail-Stage([string] $detail, $response = $null) {
    Write-Host ''
    Write-Host "FAILED at stage '$($script:Stage)': $detail" -ForegroundColor Red
    if ($null -ne $response) {
        Write-Host "  HTTP status: $($response.Status)" -ForegroundColor Red
        $err = $null
        if ($response.Json -and ($response.Json.PSObject.Properties.Name -contains 'error')) { $err = $response.Json.error }
        if ($err) {
            foreach ($field in 'code', 'message', 'reason', 'hint') {
                if ($err.PSObject.Properties.Name -contains $field) { Write-Host "  error.$($field): $($err.$field)" -ForegroundColor Red }
            }
        }
    }
    if ($script:RunId) { Write-Host "  runId: $($script:RunId)  (not a secret: useless without the run token)" -ForegroundColor Red }
    if ($null -ne $script:LastVersion) { Write-Host "  last known stateVersion: $($script:LastVersion)" -ForegroundColor Red }
    Write-Host '  Server-side reasons (fallbacks, provider errors) are in the server log lines starting with "ai role=".' -ForegroundColor Red
    Show-Summary -Failed
    exit 1
}

# ---------------------------------------------------------------- HTTP

function Invoke-Api([string] $method, [string] $path, [hashtable] $headers = @{}, $body = $null) {
    $params = @{
        Method = $method; Uri = "$BaseUrl$path"; Headers = $headers
        SkipHttpErrorCheck = $true; TimeoutSec = 120; ErrorAction = 'Stop'
    }
    if ($null -ne $body) {
        $params.Body = ($body | ConvertTo-Json -Compress -Depth 10)
        $params.ContentType = 'application/json'
    }
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $r = Invoke-WebRequest @params
    } catch {
        # Message is about the connection only; headers are never included.
        Fail-Stage "could not reach $BaseUrl ($($_.Exception.GetType().Name): $($_.Exception.Message))"
    }
    $watch.Stop()
    $content = [string] $r.Content
    $json = $null
    if ($content) { try { $json = $content | ConvertFrom-Json -Depth 64 } catch { $json = $null } }
    [pscustomobject]@{ Status = [int] $r.StatusCode; Raw = $content; Json = $json; Ms = $watch.ElapsedMilliseconds }
}

function Get-RunHeaders([string] $key) {
    $h = @{ Authorization = "Bearer $script:Token" }
    if ($key) { $h['Idempotency-Key'] = $key }
    $h
}

function Get-View {
    $r = Invoke-Api GET "/api/v1/runs/$script:RunId" (Get-RunHeaders)
    if ($r.Status -ne 200) { Fail-Stage 'GET of the run failed' $r }
    $script:LastVersion = $r.Json.stateVersion
    $r.Json
}

# Submits a turn; retries the SAME idempotency key on 429 (rate limit) and 409 REQUEST_IN_PROGRESS,
# which are never stored, so a retry can never apply anything twice.
function Submit-Turn([string] $text, [long] $version, [string] $key = [guid]::NewGuid().ToString(), [string] $kind) {
    $body = [ordered]@{ input = $text; stateVersion = $version }
    for ($attempt = 1; $attempt -le 6; $attempt++) {
        $r = Invoke-Api POST "/api/v1/runs/$script:RunId/turns" (Get-RunHeaders $key) $body
        $code = if ($r.Json -and ($r.Json.PSObject.Properties.Name -contains 'error')) { $r.Json.error.code } else { $null }
        if ($r.Status -eq 429) {
            Write-Host '    (turn rate limit reached; waiting 15 s, then retrying the same request key)' -ForegroundColor DarkGray
            Start-Sleep -Seconds 15; continue
        }
        if ($r.Status -eq 409 -and $code -eq 'REQUEST_IN_PROGRESS') { Start-Sleep -Seconds 3; continue }
        break
    }
    $source = if ($r.Status -eq 200) { $r.Json.narration.source } else { '-' }
    $script:Turns.Add([pscustomobject]@{
        Stage = $script:Stage; Kind = $kind; Input = $text; Status = $r.Status; Ms = $r.Ms; Narration = $source })
    if ($r.Status -eq 200) { $script:LastVersion = $r.Json.view.stateVersion }
    [pscustomobject]@{ Key = $key; Body = $body; Response = $r }
}

function Assert-TurnOk($turn, [string] $what) {
    if ($turn.Response.Status -ne 200) { Fail-Stage "$what was not accepted" $turn.Response }
}

function Assert-AiSource([string] $what, $narration) {
    if ($null -eq $narration) { Fail-Stage "$what is missing" }
    if ($narration.source -ne 'AI') {
        Fail-Stage "$what came from the deterministic FALLBACK, not the real AI provider. Check the server's AI settings and its 'ai role=' log lines for the failure kind."
    }
}

# ---------------------------------------------------------------- game helpers

function Show-Scene($view) {
    $s = $view.scene
    $zoneName = @{}; foreach ($z in $s.zones) { $zoneName[$z.alias] = $z.name }
    Write-Host ''
    Write-Host "  Scene: $($view.location.scene)  -  you are in the $($view.location.zone.name) ($($view.location.zone.alias))" -ForegroundColor Yellow
    Write-Host "    Zones:     $(($s.zones | ForEach-Object { "$($_.name) ($($_.alias))" }) -join ', ')"
    Write-Host "    Exits:     $(($s.exits | ForEach-Object { "$($_.alias) in the $($zoneName[$_.zone])" }) -join ', ')"
    $creatures = @($s.creatures | ForEach-Object { "$($_.name) ($($_.alias), $($zoneName[$_.zone]), $($_.condition))" })
    Write-Host "    Creatures: $(if ($creatures.Count) { $creatures -join ', ' } else { 'none visible' })"
    if (@($s.objects).Count) { Write-Host "    Objects:   $(($s.objects | ForEach-Object { "$($_.name) ($($zoneName[$_.zone]))" }) -join ', ')" }
    if (@($s.hazards).Count) { Write-Host "    Hazards:   $(($s.hazards | ForEach-Object { "$($_.name) ($($zoneName[$_.zone]))" }) -join ', ')" }
    Write-Host "    HP: $($view.character.hp)/$($view.character.maxHp)   awaiting: $($view.awaiting)   stateVersion: $($view.stateVersion)" -ForegroundColor DarkGray
}

function Show-Turn($turn, [string] $label) {
    $j = $turn.Response.Json
    Write-Prose "$label  (turn $($j.turnNumber), $($j.overall))" $j.narration.text $j.narration.source
    $c = $j.changes
    $parts = @("HP lost: $($c.playerHpLost)")
    if (@($c.enemiesDefeated).Count) { $parts += "defeated: $(@($c.enemiesDefeated) -join ', ')" }
    if ($c.movedTo) { $parts += "moved to: $($c.movedTo)" }
    if ($c.enteredScene) { $parts += "entered: $($c.enteredScene)" }
    if ($j.enemyTurn) { $parts += "enemy: $($j.enemyTurn.attacker) chose $($j.enemyTurn.action)" }
    Write-Host "    $($parts -join '   ')" -ForegroundColor DarkGray
}

function Show-PendingAttack($view) {
    $p = $view.pendingAttack
    Write-Host ''
    Write-Host "  Incoming attack from the $($p.attacker) ($($p.alias))" -ForegroundColor Magenta
    Write-Host "    Java cue: $($p.cueText)" -ForegroundColor Magenta
    if ($p.narration) { Write-Prose 'Enemy attack narration' $p.narration.text $p.narration.source }
}

function Get-ActiveCreatures($view) { @($view.scene.creatures | Where-Object { $_.condition -eq 'ACTIVE' }) }

# The natural method and wording for each bundled weapon.
function Get-WeaponStyle([string] $weapon) {
    switch ($weapon) {
        'Longsword'  { @{ Method = 'slash';   Attack = 'I slash at the {0} with my Longsword.';                         Defend = "I raise my Longsword to parry the {0}'s blow." } }
        'Dagger'     { @{ Method = 'thrust';  Attack = 'I lunge in and stab at the {0} with my Dagger.';                 Defend = "I turn the {0}'s blow aside with my Dagger." } }
        'War Hammer' { @{ Method = 'smash';   Attack = 'I bring my War Hammer down on the {0}.';                        Defend = "I brace behind the haft of my War Hammer and parry the {0}'s blow." } }
        'Ember Rod'  { @{ Method = 'project'; Attack = 'I hurl a bolt of flame from my Ember Rod at the {0}.';           Defend = "I throw myself aside to dodge the {0}'s attack." } }
        default      { @{ Method = 'slash';   Attack = "I attack the {0} with my $weapon.";                             Defend = "I try to block the {0}'s attack with my $weapon." } }
    }
}

# Breadth-first next zone on the way to a target zone, over the visible connections.
function Get-NextZone($view, [string] $target) {
    $from = $view.location.zone.alias
    if ($from -eq $target) { return $null }
    $adjacent = @{}
    foreach ($c in $view.scene.connections) {
        if (-not $adjacent.ContainsKey($c.zoneA)) { $adjacent[$c.zoneA] = @() }
        if (-not $adjacent.ContainsKey($c.zoneB)) { $adjacent[$c.zoneB] = @() }
        $adjacent[$c.zoneA] += $c.zoneB; $adjacent[$c.zoneB] += $c.zoneA
    }
    $previous = @{ $from = $null }
    $queue = [System.Collections.Generic.Queue[string]]::new(); $queue.Enqueue($from)
    while ($queue.Count) {
        $zone = $queue.Dequeue()
        if ($zone -eq $target) { break }
        foreach ($next in @($adjacent[$zone])) {
            if ($next -and -not $previous.ContainsKey($next)) { $previous[$next] = $zone; $queue.Enqueue($next) }
        }
    }
    if (-not $previous.ContainsKey($target)) { return $null }
    $step = $target
    while ($previous[$step] -ne $from) { $step = $previous[$step] }
    $step
}

# An enemy attack can only arise after a resolved non-defend step in a scene with visible enemies.
# If one is pending when the script wants to act, defend first (slash command, no interpreter call)
# rather than walking into DEFENSE_REQUIRED.
function Resolve-UnexpectedPendingAttack($view, [string] $context) {
    if ($view.awaiting -ne 'DEFENSE') { return $view }
    Write-Host "    An enemy attack is pending $context; defending before continuing." -ForegroundColor DarkYellow
    Show-PendingAttack $view
    Assert-AiSource 'The enemy attack narration' $view.pendingAttack.narration
    $t = Submit-Turn '/defend parry' $view.stateVersion -kind 'command'
    Assert-TurnOk $t 'The defense against an unexpected attack'
    Show-Turn $t 'Defense'
    Assert-AiSource 'The outcome narration' $t.Response.Json.narration
    $v = $t.Response.Json.view
    if ($v.status -ne 'ACTIVE') { Fail-Stage "The run ended ($($v.status)) while handling an unexpected attack." }
    $v
}

# ---------------------------------------------------------------- server log (optional)

function Read-NewLogLines {
    if (-not $ServerLog -or -not (Test-Path -LiteralPath $ServerLog)) { return @() }
    $stream = [System.IO.File]::Open((Resolve-Path -LiteralPath $ServerLog), 'Open', 'Read', 'ReadWrite')
    try {
        $start = [Math]::Min($script:LogStart, $stream.Length)
        [void] $stream.Seek($start, 'Begin')
        $reader = [System.IO.StreamReader]::new($stream)
        $text = $reader.ReadToEnd()
    } finally { $stream.Dispose() }
    @($text -split "`r?`n" | Where-Object { $_ -match ' ai role=' })
}

function Get-AiCalls {
    $calls = foreach ($line in Read-NewLogLines) {
        if ($line -match 'ai role=(\S+) promptVersion=(\S+) model=(\S+) latencyMs=(\d+) attempts=(\d+) outcome=(\S+) inputTokens=(\S+) cachedInputTokens=(\S+) outputTokens=(\S+) reasoningTokens=(\S+)') {
            [pscustomobject]@{
                Role = $Matches[1]; Model = $Matches[3]; LatencyMs = [int] $Matches[4]; Attempts = [int] $Matches[5]
                Outcome = $Matches[6]; Input = $Matches[7]; Cached = $Matches[8]; Output = $Matches[9]
            }
        }
    }
    @($calls)
}

# Sums a token column; '-' (not reported by the provider) counts as nothing.
function Get-TokenSum($calls, [string] $field) {
    $total = 0L
    foreach ($c in @($calls)) { if ($c.$field -match '^\d+$') { $total += [long] $c.$field } }
    $total
}

function Show-Summary([switch] $Failed) {
    Write-Section 'Summary'
    if ($script:Turns.Count) {
        Write-Host '  Turns submitted:'
        $script:Turns | Format-Table @{ n = 'Stage'; e = { $_.Stage } }, @{ n = 'Input'; e = { $_.Kind } },
            @{ n = 'Text'; e = { if ($_.Input.Length -gt 48) { $_.Input.Substring(0, 45) + '...' } else { $_.Input } } },
            @{ n = 'HTTP'; e = { $_.Status } }, @{ n = 'ms'; e = { $_.Ms } }, @{ n = 'Narration'; e = { $_.Narration } } -AutoSize |
            Out-String -Width 160 | Write-Host
    }
    if ($script:Checks.Count) {
        $v = @($script:Checks | Where-Object Kind -eq 'VERIFIED').Count
        $i = @($script:Checks | Where-Object Kind -eq 'INFERRED').Count
        Write-Host "  Checks: $v verified by the API, $i inferred (consistent with the API, not proven by it)."
    }
    $calls = Get-AiCalls
    if ($ServerLog) {
        if ($calls.Count -eq 0) {
            Write-Host "  No 'ai role=' lines found in $ServerLog for this run (was the server started with --logging.file.name?)." -ForegroundColor DarkYellow
        } else {
            Write-Host '  AI calls (from the server log):'
            $calls | Group-Object Role | ForEach-Object {
                $g = $_.Group
                [pscustomobject]@{
                    Role = $_.Name; Calls = $g.Count; Models = (($g.Model | Sort-Object -Unique) -join ',')
                    Outcomes = (($g.Outcome | Group-Object | ForEach-Object { "$($_.Name) x$($_.Count)" }) -join ', ')
                    'Avg ms' = [int] (($g | Measure-Object LatencyMs -Average).Average)
                    'In tok' = Get-TokenSum $g 'Input'; 'Cached' = Get-TokenSum $g 'Cached'; 'Out tok' = Get-TokenSum $g 'Output'
                }
            } | Format-Table -AutoSize | Out-String -Width 160 | Write-Host
            $in = Get-TokenSum $calls 'Input'
            $out = Get-TokenSum $calls 'Output'
            Write-Host "  Total: $($calls.Count) AI calls, $in input tokens, $out output tokens."
        }
    } else {
        Write-Host '  (Pass -ServerLog <file> to include per-role model, latency and token usage.)' -ForegroundColor DarkGray
    }
    if ($Failed) { Write-Host ''; Write-Host "RESULT: FAILED at stage '$($script:Stage)'" -ForegroundColor Red }
}

# ================================================================ 1. preflight

Write-Section '1. Preflight'
$uri = [Uri] $BaseUrl
if ($uri.Host -notin @('localhost', '127.0.0.1', '::1', '[::1]')) {
    Fail-Stage "BaseUrl must point at the local server (localhost), not '$($uri.Host)'."
}
$BaseUrl = $BaseUrl.TrimEnd('/')
if (-not $InviteCode) {
    $secure = Read-Host 'Invite code (one of the server''s GAME_INVITE_CODES; input hidden)' -AsSecureString
    $InviteCode = [System.Net.NetworkCredential]::new('', $secure).Password
}
if (-not $InviteCode) { Fail-Stage 'No invite code given (set $env:GAME_SMOKE_INVITE_CODE or enter it when prompted).' }
if ($ServerLog) {
    if (Test-Path -LiteralPath $ServerLog) { $script:LogStart = (Get-Item -LiteralPath $ServerLog).Length }
    else { Write-Host "  Server log '$ServerLog' does not exist yet; usage will be read if it appears." -ForegroundColor DarkYellow }
}
$probe = Invoke-Api GET "/api/v1/runs/$([guid]::NewGuid())"
if ($probe.Status -ne 401 -or -not $probe.Json -or $probe.Json.error.code -ne 'UNAUTHORIZED') {
    Fail-Stage "The server at $BaseUrl did not answer like the game API (expected 401 UNAUTHORIZED without a token)." $probe
}
Add-Check 'VERIFIED' "The game API is answering at $BaseUrl"

# A client-generated run token: 32 secure random bytes, unpadded base64url (43 characters). Never printed.
$script:Token = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).TrimEnd('=').Replace('+', '-').Replace('/', '_')

# ================================================================ 2. create the run

$script:Stage = 'create-run'
Write-Section '2. Create a run'
$creationKey = [guid]::NewGuid().ToString()
$created = Invoke-Api POST '/api/v1/runs' @{ 'X-Invite-Code' = $InviteCode; 'Idempotency-Key' = $creationKey; Authorization = "Bearer $script:Token" }
if ($created.Status -ne 201) { Fail-Stage 'Run creation was refused' $created }
$script:RunId = $created.Json.runId
$view = $created.Json.view
$script:LastVersion = $view.stateVersion
$ch = $view.character
Write-Host ''
Write-Host "  $($ch.name)   HP $($ch.hp)/$($ch.maxHp)   Fate: $($ch.fatedBand)" -ForegroundColor Yellow
Write-Host "    Stats:     $(($ch.stats.PSObject.Properties | ForEach-Object { "$($_.Name) $($_.Value)" }) -join ', ')"
Write-Host "    Weapons:   $(($ch.weapons | ForEach-Object name) -join ', ')"
Write-Host "    Items:     $(($ch.items | ForEach-Object name) -join ', ')"
Write-Host "    Ability:   $(($ch.abilities | ForEach-Object name) -join ', ')    Passive: $($ch.passive)"
Write-Prose 'Introduction' $view.introduction.text $view.introduction.source
Assert-AiSource 'The character introduction' $view.introduction
Add-Check 'VERIFIED' 'Run created (201); the introduction source is AI'
Show-Scene $view

# ================================================================ 3. navigate with slash commands

$script:Stage = 'navigate'
Write-Section '3. Into the Hollow Chapel (slash commands: no interpreter calls; each turn is still narrated)'
$navTurns = 0
while (@(Get-ActiveCreatures $view).Count -eq 0) {
    if ($navTurns -ge $MaxNavigationTurns) { Fail-Stage "No visible, living enemy found within $MaxNavigationTurns navigation turns." }
    $view = Resolve-UnexpectedPendingAttack $view 'during navigation'
    $exits = @($view.scene.exits)
    if ($exits.Count -eq 0) { Fail-Stage 'The current scene shows no exits.' }
    # The zone you arrive in holds the exit back the way you came; prefer exits elsewhere.
    $onward = @($exits | Where-Object { $_.zone -ne $view.location.zone.alias })
    $exit = if ($onward.Count) { $onward[0] } else { $exits[0] }
    if ($exit.zone -ne $view.location.zone.alias) {
        $next = Get-NextZone $view $exit.zone
        if (-not $next) { Fail-Stage "No visible route to the zone of $($exit.alias)." }
        $t = Submit-Turn "/move $next" $view.stateVersion -kind 'command'
    } else {
        $t = Submit-Turn "/move $($exit.alias)" $view.stateVersion -kind 'command'
    }
    $navTurns++
    Assert-TurnOk $t "Navigation command '$($t.Body.input)'"
    Show-Turn $t "Navigation: $($t.Body.input)"
    Assert-AiSource 'The navigation narration' $t.Response.Json.narration
    $view = $t.Response.Json.view
    if ($t.Response.Json.changes.enteredScene) { Show-Scene $view }
}
Add-Check 'VERIFIED' "Reached '$($view.location.scene)' with a visible, living enemy in $navTurns slash-command turns"

# ================================================================ 4. natural-language attack

$script:Stage = 'natural-language-attack'
Write-Section '4. Natural-language attack (real interpreter)'
$view = Resolve-UnexpectedPendingAttack $view 'before the natural-language attack'
$target = Get-ActiveCreatures $view | Select-Object -First 1
if (-not $target) { Fail-Stage 'The visible enemy fell before the attack could be made.' }
$weapon = $view.character.weapons[0]
$style = Get-WeaponStyle $weapon.name
$attackText = $style.Attack -f $target.name
Write-Host "  Player: `"$attackText`"" -ForegroundColor White
$versionBefore = [long] $view.stateVersion
$hpBefore = [int] $view.character.hp
$turnBefore = if ($view.lastTurn) { [int] $view.lastTurn.turnNumber } else { 0 }
$attack = Submit-Turn $attackText $versionBefore -kind 'free text'
if ($attack.Response.Status -ne 200) {
    $code = if ($attack.Response.Json) { $attack.Response.Json.error.code } else { '?' }
    Fail-Stage "The natural-language attack was not accepted ($code). AI_UNAVAILABLE means the interpreter could not reach the model; INVALID_OUTPUT means the model's answer failed strict parsing or Java validation twice." $attack.Response
}
$aj = $attack.Response.Json
Show-Turn $attack 'Attack'
Assert-AiSource 'The attack outcome narration' $aj.narration
Add-Check 'VERIFIED' 'Free text was accepted (200): the AI interpreter produced an intent that passed strict parsing and Java validation (with AI unavailable this would be 422 INTERPRETATION_FAILED)'
if ($aj.turnNumber -ne $turnBefore + 1 -or $aj.view.stateVersion -ne $versionBefore + 1) {
    Fail-Stage "Turn bookkeeping is wrong: turn $($aj.turnNumber) (expected $($turnBefore + 1)), stateVersion $($aj.view.stateVersion) (expected $($versionBefore + 1))."
}
Add-Check 'VERIFIED' "Java committed the turn: turn $($aj.turnNumber), stateVersion $versionBefore -> $($aj.view.stateVersion)"
if ($aj.overall -eq 'MECHANICS_UNAVAILABLE') {
    Fail-Stage 'Java resolved no step of the interpreted action (MECHANICS_UNAVAILABLE): the interpreter chose an action the engine cannot resolve yet.'
}
Add-Check 'VERIFIED' "Java resolved at least one step (overall $($aj.overall)); the outcome narration source is AI"
Add-Check 'INFERRED' "That the resolved step was an ATTACK on the $($target.name): the public response does not expose the interpreted action type or target (only the narration and changes suggest it)"
if ($aj.changes.playerHpLost -ne 0 -or $aj.view.character.hp -ne $hpBefore) {
    Fail-Stage "An attack turn changed the player's HP ($hpBefore -> $($aj.view.character.hp)) with no pending attack to defend."
}
$reloaded = Get-View
if ($reloaded.stateVersion -ne $aj.view.stateVersion -or $reloaded.character.hp -ne $aj.view.character.hp -or
        $reloaded.awaiting -ne $aj.view.awaiting -or $reloaded.lastTurn.narration.text -ne $aj.narration.text) {
    Fail-Stage 'A reload does not match the attack turn response.'
}
Add-Check 'VERIFIED' 'A reload (GET) shows the same stateVersion, HP, awaiting state and narration: the result is persisted'
$view = $reloaded

# ================================================================ 5. wait for the enemy's attack

$script:Stage = 'await-enemy-attack'
Write-Section '5. The enemy responds'
$waited = 0
while ($view.awaiting -ne 'DEFENSE') {
    if ($view.status -ne 'ACTIVE') { Fail-Stage "The run ended ($($view.status)) before an enemy attacked." }
    if ($waited -ge $MaxEnemyWaitTurns) {
        Fail-Stage "No enemy attacked within $MaxEnemyWaitTurns further turns (enemies chose HOLD; their decisions are not overridden). Re-run to try a different run."
    }
    $target = Get-ActiveCreatures $view | Select-Object -First 1
    if (-not $target) { Fail-Stage 'Every visible enemy has fallen before attacking; no attack to defend against. Re-run to try a different run.' }
    Write-Host "    No attack yet; another attack by slash command (no interpreter call)."
    $t = Submit-Turn "/attack $($target.alias) $($style.Method) with $($weapon.alias)" $view.stateVersion -kind 'command'
    Assert-TurnOk $t 'The follow-up attack command'
    Show-Turn $t 'Follow-up attack'
    Assert-AiSource 'The follow-up outcome narration' $t.Response.Json.narration
    $view = $t.Response.Json.view
    $waited++
}
Show-PendingAttack $view
if (-not $view.pendingAttack.cueText) { Fail-Stage 'The pending attack has no Java cueText.' }
Assert-AiSource 'The enemy attack narration' $view.pendingAttack.narration
Add-Check 'VERIFIED' 'An enemy attack is pending with its Java cueText; the attack narration source is AI'
$again = Get-View
if (($again.pendingAttack | ConvertTo-Json -Depth 10 -Compress) -ne ($view.pendingAttack | ConvertTo-Json -Depth 10 -Compress)) {
    Fail-Stage 'Reloading changed the pending attack (it must never be rerolled or re-narrated).'
}
Add-Check 'VERIFIED' 'Reloading returns the identical pending attack, cue and narration'

# ================================================================ 6. natural-language defense

$script:Stage = 'natural-language-defense'
Write-Section '6. Natural-language defense (real interpreter)'
$defenseText = $style.Defend -f $view.pendingAttack.attacker
Write-Host "  Player: `"$defenseText`"" -ForegroundColor White
$hpBefore = [int] $view.character.hp
$versionBefore = [long] $view.stateVersion
$defense = Submit-Turn $defenseText $versionBefore -kind 'free text'
if ($defense.Response.Status -ne 200) {
    $code = if ($defense.Response.Json) { $defense.Response.Json.error.code } else { '?' }
    Fail-Stage "The natural-language defense was not accepted ($code). DEFENSE_REQUIRED means the interpreted action did not open with a defense against the pending attack." $defense.Response
}
$dj = $defense.Response.Json
Show-Turn $defense 'Defense'
Assert-AiSource 'The defense outcome narration' $dj.narration
Add-Check 'VERIFIED' 'Free-text defense accepted (200): Java only accepts a turn during a pending attack if its first step is a DEFEND against that attack that actually RESOLVED'
$expectedHp = [Math]::Max(0, $hpBefore - [int] $dj.changes.playerHpLost)
if ($dj.view.character.hp -ne $expectedHp) { Fail-Stage "HP is $($dj.view.character.hp), expected $hpBefore - $($dj.changes.playerHpLost) = $expectedHp." }
Add-Check 'VERIFIED' "HP $hpBefore -> $($dj.view.character.hp) matches the confirmed damage ($($dj.changes.playerHpLost))"
if ($dj.view.status -eq 'DEAD') {
    Write-Host '    The defense failed and the character died: a valid (unlucky) outcome.' -ForegroundColor DarkYellow
    Add-Check 'VERIFIED' 'The run is DEAD after HP reached 0'
} elseif ($dj.view.pendingAttack) {
    if ($dj.enemyTurn -and $dj.enemyTurn.action -eq 'ATTACK') {
        Add-Check 'VERIFIED' 'The defended attack was consumed; a follow-up step let an enemy attack again'
    } else {
        Fail-Stage 'The defended attack is still pending after the defense.'
    }
} else {
    Add-Check 'VERIFIED' 'The pending attack was consumed and nothing new is pending'
}
if ($dj.overall -eq 'MECHANICS_UNAVAILABLE') { Fail-Stage 'Java resolved no step of the defense.' }

# ================================================================ 7. reload

$script:Stage = 'reload'
Write-Section '7. Reload'
$final = Get-View
foreach ($field in 'stateVersion', 'status', 'awaiting') {
    if ($final.$field -ne $dj.view.$field) { Fail-Stage "After reload, $field is '$($final.$field)', but the defense response said '$($dj.view.$field)'." }
}
if ($final.character.hp -ne $dj.view.character.hp) { Fail-Stage 'After reload, HP differs from the defense response.' }
if (($final.pendingAttack | ConvertTo-Json -Depth 10 -Compress) -ne ($dj.view.pendingAttack | ConvertTo-Json -Depth 10 -Compress)) {
    Fail-Stage 'After reload, the pending attack differs from the defense response.'
}
if ($final.lastTurn.turnNumber -ne $dj.turnNumber -or $final.lastTurn.narration.text -ne $dj.narration.text) {
    Fail-Stage 'After reload, the last turn differs from the defense response.'
}
Add-Check 'VERIFIED' "Reload matches: stateVersion $($final.stateVersion), HP $($final.character.hp), status $($final.status), awaiting $($final.awaiting)"
Show-Scene $final

# ================================================================ 8. idempotent replay

$script:Stage = 'replay'
Write-Section '8. Replay the natural-language attack with its original idempotency key'
$replay = Invoke-Api POST "/api/v1/runs/$script:RunId/turns" (Get-RunHeaders $attack.Key) $attack.Body
$script:Turns.Add([pscustomobject]@{ Stage = $script:Stage; Kind = 'replay'; Input = $attack.Body.input; Status = $replay.Status; Ms = $replay.Ms; Narration = 'stored' })
if ($replay.Status -ne 200) { Fail-Stage 'The replay was not answered with the stored response' $replay }
if ($replay.Raw -ne $attack.Response.Raw) { Fail-Stage 'The replayed response is not byte-identical to the original.' }
Add-Check 'VERIFIED' 'The replay returned the byte-identical stored response'
$afterReplay = Get-View
if ($afterReplay.stateVersion -ne $final.stateVersion -or $afterReplay.character.hp -ne $final.character.hp) {
    Fail-Stage 'The replay changed the game state.'
}
Add-Check 'VERIFIED' 'stateVersion and HP are unchanged after the replay: nothing was applied twice'
Add-Check 'INFERRED' 'The replay made no AI call (the server serves stored answers; confirm with the AI call counts below)'

# ================================================================ 9. summary

$script:Stage = 'summary'
$calls = Get-AiCalls
if ($ServerLog -and $calls.Count) {
    $wrongModel = @($calls | Where-Object { $_.Model -ne $ExpectedModel })
    if ($wrongModel.Count) { Fail-Stage "Some AI calls used model(s) $(($wrongModel.Model | Sort-Object -Unique) -join ', '), not $ExpectedModel." }
    $notOk = @($calls | Where-Object { $_.Outcome -notin @('SUCCESS', 'REPAIRED') })
    if ($notOk.Count) { Fail-Stage "Some AI calls did not succeed: $(($notOk | ForEach-Object { "$($_.Role)=$($_.Outcome)" }) -join ', ')." }
    $narrators = @($calls | Where-Object Role -eq 'OUTCOME_NARRATOR').Count
    $committed = @($script:Turns | Where-Object { $_.Status -eq 200 -and $_.Kind -ne 'replay' }).Count
    if ($narrators -ne $committed) { Fail-Stage "Expected $committed outcome narrations (one per committed turn), the log shows $narrators." }
    Add-Check 'VERIFIED' "Server log: every AI call used $ExpectedModel and succeeded; one outcome narration per committed turn, none for the replay"
    $interp = @($calls | Where-Object Role -eq 'ACTION_INTERPRETER').Count
    if ($interp -ne 2) { Fail-Stage "Expected exactly 2 interpreter calls (the two free-text turns), the log shows $interp." }
    Add-Check 'VERIFIED' 'Server log: the interpreter ran exactly twice (only the two free-text turns; slash commands and the replay never use it)'
}
Show-Summary
Write-Host ''
Write-Host 'Roles exercised (source as reported by the API):' -ForegroundColor Cyan
Write-Host "  Character introduction : $($view.introduction.source)"
Write-Host "  Action interpreter     : used for 2 free-text turns (proven by their 200 responses)"
Write-Host "  Outcome narrator       : $(@($script:Turns | Where-Object { $_.Narration -eq 'AI' }).Count) turn(s), all AI"
Write-Host "  Enemy attack narrator  : AI"
Write-Host ''
Write-Host 'RESULT: PASSED' -ForegroundColor Green
