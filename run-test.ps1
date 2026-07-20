. .\test-creds.ps1
& "$PSScriptRoot\gradlew.bat" :mediapipe-vision:connectedDebugAndroidTest `
    -PsupabaseUrl="$env:TEST_SUPABASE_URL" `
    -PsupabaseKey="$env:TEST_SUPABASE_KEY" `
    -PgeminiApiKey=""
