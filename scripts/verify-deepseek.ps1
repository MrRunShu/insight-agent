# Verify DeepSeek API connectivity.
# Prereq: setx DEEPSEEK_API_KEY "sk-..." then reopen PowerShell.
# Usage:  pwsh scripts/verify-deepseek.ps1

$ErrorActionPreference = "Stop"

if (-not $env:DEEPSEEK_API_KEY) {
    Write-Host "DEEPSEEK_API_KEY is not set. Run: setx DEEPSEEK_API_KEY `"sk-...`" and reopen the shell." -ForegroundColor Red
    exit 1
}

$body = @{
    model = "deepseek-chat"
    messages = @(
        @{ role = "system"; content = "You are InsightAgent, a news analysis assistant. Reply in one short sentence." },
        @{ role = "user";   content = "Say 'InsightAgent online' and nothing else." }
    )
    temperature = 0.1
    max_tokens = 32
} | ConvertTo-Json -Depth 5

$headers = @{
    "Authorization" = "Bearer $env:DEEPSEEK_API_KEY"
    "Content-Type"  = "application/json"
}

Write-Host "Calling DeepSeek chat completions..." -ForegroundColor Cyan

$response = Invoke-RestMethod `
    -Method Post `
    -Uri "https://api.deepseek.com/chat/completions" `
    -Headers $headers `
    -Body $body

$reply = $response.choices[0].message.content
$usage = $response.usage

Write-Host "--- Reply ---" -ForegroundColor Green
Write-Host $reply
Write-Host "--- Usage ---" -ForegroundColor Green
Write-Host ("prompt={0}  completion={1}  total={2}" -f $usage.prompt_tokens, $usage.completion_tokens, $usage.total_tokens)
Write-Host "OK." -ForegroundColor Green
