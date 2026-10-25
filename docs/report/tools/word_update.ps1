# Opens the generated report in Microsoft Word, refreshes every field (table of contents, lists of figures/tables/code,
# SEQ captions, REF cross-references), saves it, exports a PDF and writes the page count.
param([string]$Docx, [string]$Pdf, [string]$Info)
$ErrorActionPreference = 'Stop'
$word = New-Object -ComObject Word.Application
$word.Visible = $false
$word.DisplayAlerts = 0
try {
    $doc = $word.Documents.Open($Docx, $false, $false)
    $doc.Repaginate()
    $doc.Fields.Update() | Out-Null
    foreach ($t in $doc.TablesOfContents) { $t.Update() }
    foreach ($t in $doc.TablesOfFigures) { $t.Update() }
    $doc.Repaginate()
    foreach ($t in $doc.TablesOfContents) { $t.Update() }
    foreach ($t in $doc.TablesOfFigures) { $t.Update() }
    $pages = $doc.ComputeStatistics(2)
    $doc.Save()
    $doc.ExportAsFixedFormat($Pdf, 17)
    "pages=$pages" | Out-File -FilePath $Info -Encoding utf8
    "fields=$($doc.Fields.Count) tables=$($doc.Tables.Count) inlineShapes=$($doc.InlineShapes.Count)" | Out-File -FilePath $Info -Append -Encoding utf8
    $doc.Close(0)
    "ok pages=$pages"
} finally {
    $word.Quit()
}
