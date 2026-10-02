$bytes = [System.IO.File]::ReadAllBytes("C:\AICode\Qoder\vertor\target\classes\com\vertor\service\Langchain4jVectorStoreService.class")
$text = [System.Text.Encoding]::UTF8.GetString($bytes)
Write-Output "=== Checking compiled class ==="
Write-Output "Contains CAST: $($text.Contains('CAST'))"
Write-Output "Contains ?::vector: $($text.Contains('?::vector'))"
Write-Output "Contains sub WHERE: $($text.Contains('sub WHERE'))"
Write-Output "Contains SELECT * FROM: $($text.Contains('SELECT * FROM'))"
Write-Output "File size: $($bytes.Length) bytes"
Write-Output "Last modified: $((Get-Item 'C:\AICode\Qoder\vertor\target\classes\com\vertor\service\Langchain4jVectorStoreService.class').LastWriteTime)"
