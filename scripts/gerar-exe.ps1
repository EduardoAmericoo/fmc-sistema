# Gera o programa pronto para Windows, que abre sem precisar instalar o Java.
#
# Como usar, no PowerShell, dentro da pasta do projeto:
#   .\scripts\gerar-exe.ps1                 gera o instalador e o .zip portátil
#   .\scripts\gerar-exe.ps1 -SemInstalador  gera só o .zip portátil (não precisa do WiX)
#
# O resultado fica em target\pacote:
#   - Instalar-FMC-<versao>.exe          o instalador: Avançar, Instalar, Concluir. Cria o ícone na
#                                        Área de Trabalho e no menu Iniciar, e aparece em
#                                        "Adicionar ou remover programas" para desinstalar.
#   - FMC-Sistema-<versao>-windows.zip   a versão portátil: extrair e abrir o FMC.exe de dentro da pasta FMC
#   - FMC\                               a pasta portátil já extraída, para testar
# Os dois arquivos vão anexados no Release do GitHub.
#
# Precisa de:
#   - JDK 21 (ele traz o jpackage);
#   - para o instalador, o WiX Toolset 3.14 (gratuito). O script procura o WiX no PATH e em
#     %LOCALAPPDATA%\wix314 (é só extrair ali o wix314-binaries.zip do GitHub do WiX).
#     Quem INSTALA o FMC não precisa de nada disso: só quem gera o instalador.
#
# Os dados (banco, documentos e cópias) não ficam junto do programa: ficam em
# %LOCALAPPDATA%\FMC\data (ver db/Database.java).

param([switch]$SemInstalador)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

# A versão vem do pom.xml (ex.: 0.1.0). O jpackage não aceita "-SNAPSHOT" e afins: fica só o número.
$versao = ([xml](Get-Content pom.xml -Encoding UTF8)).project.version
$versaoNumerica = ($versao -split '[^0-9.]')[0]

$pacote = Join-Path $raiz 'target\pacote'
$libs = Join-Path $raiz 'target\exe-libs'
$javafx = Join-Path $raiz 'target\exe-javafx'
$etapas = if ($SemInstalador) { 5 } else { 6 }

if (-not $SemInstalador -and -not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    $wixLocal = Join-Path $env:LOCALAPPDATA 'wix314'
    if (Test-Path (Join-Path $wixLocal 'candle.exe')) {
        $env:PATH = "$wixLocal;$env:PATH"
    } else {
        throw ("O WiX Toolset 3.14 não foi encontrado. Baixe o wix314-binaries.zip em " +
               "https://github.com/wixtoolset/wix3/releases e extraia em $wixLocal, " +
               "ou rode com -SemInstalador para gerar só o .zip portátil.")
    }
}

Write-Host "1/$etapas Compilando o sistema (sem rodar os testes)..."
& .\mvnw.cmd -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'A compilação falhou.' }

Write-Host "2/$etapas Separando as bibliotecas..."
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

Write-Host "3/$etapas Preparando o ícone..."
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

Write-Host "4/$etapas Montando o FMC.exe com o Java embutido..."
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

Write-Host "5/$etapas Compactando a versão portátil..."
$zip = Join-Path $pacote "FMC-Sistema-$versaoNumerica-windows.zip"
Compress-Archive -Path (Join-Path $pacote 'FMC') -DestinationPath $zip -Force

if (-not $SemInstalador) {
    Write-Host "6/$etapas Montando o instalador..."
    # O upgrade-uuid é fixo para sempre: é ele que faz uma versão nova substituir a antiga
    # na hora de instalar, em vez de aparecer duas vezes em "Adicionar ou remover programas".
    # O instalador vai para "Arquivos de Programas\FMC" (pede a senha de administrador, como
    # qualquer programa) e cria os atalhos. Os dados ficam fora dali e não somem ao desinstalar.
    & jpackage --type exe `
        --app-image (Join-Path $pacote 'FMC') `
        --name FMC `
        --app-version $versaoNumerica `
        --vendor 'Fundação Manuel Cruz' `
        --description 'Sistema de gestão de imóveis e aluguéis da Fundação Manuel Cruz' `
        --win-upgrade-uuid '6f1c2a3e-8d4b-4b8e-9a51-2f7c0d9e4b11' `
        --win-shortcut `
        --win-menu `
        --win-menu-group 'Fundação Manuel Cruz' `
        --win-dir-chooser `
        --dest $pacote
    if ($LASTEXITCODE -ne 0) { throw 'O jpackage falhou ao montar o instalador.' }
    $gerado = Join-Path $pacote "FMC-$versaoNumerica.exe"
    $instalador = Join-Path $pacote "Instalar-FMC-$versaoNumerica.exe"
    Move-Item $gerado $instalador -Force
}

Write-Host ""
Write-Host "Pronto:"
Get-ChildItem $pacote -File | ForEach-Object {
    Write-Host ("  {0} ({1} MB)" -f $_.FullName, [math]::Round($_.Length / 1MB, 1))
}
