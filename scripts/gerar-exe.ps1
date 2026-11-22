# Gera o programa pronto para Windows (FMC.exe), que abre sem precisar instalar o Java.
#
# Como usar, no PowerShell, dentro da pasta do projeto:
#   .\scripts\gerar-exe.ps1
#
# O resultado fica em target\pacote:
#   - target\pacote\FMC\FMC.exe          o programa (a pasta FMC inteira precisa ir junto)
#   - target\pacote\FMC-Sistema-<versao>-windows.zip   a pasta FMC compactada, para anexar no Release do GitHub
#
# Precisa do JDK 21 (ele traz o jpackage). Não precisa do WiX: o resultado é uma pasta
# "portátil" (app-image), que a pessoa extrai e abre, sem instalador e sem pedir administrador.
# Os dados (pasta data) ficam dentro da pasta FMC, ao lado do FMC.exe.

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

# A versão vem do pom.xml (ex.: 0.1.0). O jpackage não aceita "-SNAPSHOT" e afins: fica só o número.
$versao = ([xml](Get-Content pom.xml -Encoding UTF8)).project.version
$versaoNumerica = ($versao -split '[^0-9.]')[0]

$pacote = Join-Path $raiz 'target\pacote'
$libs = Join-Path $raiz 'target\exe-libs'
$javafx = Join-Path $raiz 'target\exe-javafx'

Write-Host "1/4 Compilando o sistema (sem rodar os testes)..."
& .\mvnw.cmd -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'A compilação falhou.' }

Write-Host "2/4 Separando as bibliotecas..."
foreach ($pasta in $libs, $javafx, $pacote) {
    if (Test-Path $pasta) { Remove-Item -Recurse -Force $pasta }
    New-Item -ItemType Directory -Force $pasta | Out-Null
}
& .\mvnw.cmd -q dependency:copy-dependencies -DincludeScope=runtime "-DoutputDirectory=$libs"
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível copiar as bibliotecas.' }
# O JavaFX entra como módulo dentro do Java embutido (é o jeito que ele aceita sem avisos).
# Os jars "vazios" do JavaFX (sem o "-win") não servem para isso e são descartados.
Get-ChildItem $libs -Filter 'javafx-*' | ForEach-Object {
    if ($_.Name -like '*-win.jar') { Move-Item $_.FullName $javafx } else { Remove-Item $_.FullName }
}
Copy-Item (Join-Path $raiz "target\fmc-sistema-$versao.jar") $libs

Write-Host "3/4 Preparando o ícone..."
# O jpackage exige .ico no Windows. Um .ico pode guardar um PNG dentro: basta um cabeçalho de 22 bytes.
$png = [IO.File]::ReadAllBytes((Join-Path $raiz 'src\main\resources\br\org\fmc\icones\fmc-256.png'))
$ico = Join-Path $raiz 'target\fmc.ico'
$s = New-Object IO.MemoryStream
$w = New-Object IO.BinaryWriter($s)
$w.Write([UInt16]0); $w.Write([UInt16]1); $w.Write([UInt16]1)          # tipo ícone, 1 imagem
$w.Write([byte]0); $w.Write([byte]0); $w.Write([byte]0); $w.Write([byte]0) # 256x256, sem paleta
$w.Write([UInt16]1); $w.Write([UInt16]32)                              # planos, bits por pixel
$w.Write([UInt32]$png.Length); $w.Write([UInt32]22)                    # tamanho e posição do PNG
$w.Write($png); $w.Flush()
[IO.File]::WriteAllBytes($ico, $s.ToArray())

Write-Host "4/4 Montando o FMC.exe com o Java embutido..."
# Módulos do Java que o sistema usa: banco (java.sql), telas e impressão (java.desktop),
# e-mail com TLS (java.naming, jdk.crypto.ec), datas e moeda em português (jdk.localedata),
# acentos (jdk.charsets) e o JavaFX.
$modulos = 'javafx.controls,javafx.fxml,java.sql,java.desktop,java.naming,java.logging,java.management,' +
           'java.xml,java.prefs,java.scripting,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.charsets,jdk.zipfs'
& jpackage --type app-image `
    --name FMC `
    --app-version $versaoNumerica `
    --vendor 'Fundação Manuel Cruz' `
    --description 'Sistema de gestão de imóveis e aluguéis da Fundação Manuel Cruz' `
    --icon $ico `
    --input $libs `
    --main-jar "fmc-sistema-$versao.jar" `
    --main-class br.org.fmc.Launcher `
    --module-path $javafx `
    --add-modules $modulos `
    --java-options '--enable-native-access=javafx.graphics,ALL-UNNAMED' `
    --java-options '-Dfile.encoding=UTF-8' `
    --dest $pacote
if ($LASTEXITCODE -ne 0) { throw 'O jpackage falhou.' }

$zip = Join-Path $pacote "FMC-Sistema-$versaoNumerica-windows.zip"
Compress-Archive -Path (Join-Path $pacote 'FMC') -DestinationPath $zip -Force
$mb = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host ""
Write-Host "Pronto: $zip ($mb MB)"
Write-Host "Para testar: abra target\pacote\FMC\FMC.exe"
