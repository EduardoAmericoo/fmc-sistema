#!/usr/bin/env bash
# Gera o programa pronto para Mac ou Linux, com o Java embutido (a versão Windows é o gerar-exe.ps1).
#
# Roda no próprio sistema de destino: o jpackage só gera instalador para o sistema em que está.
# Por isso quem roda este script, normalmente, é o GitHub Actions (.github/workflows/instaladores.yml),
# num Mac e num Linux do GitHub. Também dá para rodar à mão num Mac ou num Linux com o JDK 21:
#   ./scripts/gerar-instalador.sh
#
# O resultado fica em target/pacote:
#   - Mac:   FMC-Sistema-<versao>-mac-<arm64|x64>.dmg   abrir e arrastar o FMC para Aplicativos
#   - Linux: FMC-Sistema-<versao>-linux-amd64.deb       instalar com: sudo apt install ./arquivo.deb
#            FMC-Sistema-<versao>-linux-x64.tar.gz      versão portátil: extrair e abrir FMC/bin/FMC
#
# Os dados ficam fora do programa (ver db/Database.java):
#   Mac: ~/Library/Application Support/FMC/data   Linux: ~/.local/share/FMC/data
set -euo pipefail

raiz="$(cd "$(dirname "$0")/.." && pwd)"
cd "$raiz"

case "$(uname -s)" in
  Darwin) sistema=mac ;;
  Linux) sistema=linux ;;
  *) echo "Este script é para Mac ou Linux. No Windows, use scripts/gerar-exe.ps1." >&2; exit 1 ;;
esac
case "$(uname -m)" in
  arm64|aarch64) arq=arm64 ;;
  *) arq=x64 ;;
esac

versao="$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout)"
versaoNumerica="${versao%%-*}"   # o jpackage não aceita "-SNAPSHOT" e afins

pacote="$raiz/target/pacote"
libs="$raiz/target/exe-libs"
javafx="$raiz/target/exe-javafx"

echo "1/5 Compilando o sistema (sem rodar os testes)..."
./mvnw -q -DskipTests package

echo "2/5 Separando as bibliotecas..."
rm -rf "$libs" "$javafx" "$pacote"
mkdir -p "$libs" "$javafx" "$pacote"
./mvnw -q dependency:copy-dependencies -DincludeScope=runtime "-DoutputDirectory=$libs"
# O JavaFX entra como módulo dentro do Java embutido. Ficam só os jars com o código do sistema
# (ex.: javafx-controls-21.0.5-mac-aarch64.jar); os "vazios", sem o sufixo, são descartados.
for jar in "$libs"/javafx-*.jar; do
  nome="$(basename "$jar")"
  if [[ "$nome" =~ ^javafx-[a-z]+-[0-9.]+-.+\.jar$ ]]; then mv "$jar" "$javafx/"; else rm "$jar"; fi
done
cp "target/fmc-sistema-$versao.jar" "$libs/"

echo "3/5 Preparando o ícone..."
png="$raiz/src/main/resources/br/org/fmc/icones/fmc-256.png"
if [ "$sistema" = mac ]; then
  # O Mac usa .icns: um conjunto do mesmo ícone em vários tamanhos.
  conjunto="$raiz/target/fmc.iconset"
  rm -rf "$conjunto"; mkdir -p "$conjunto"
  for t in 16 32 64 128 256; do
    sips -z $t $t "$png" --out "$conjunto/icon_${t}x${t}.png" >/dev/null
  done
  cp "$conjunto/icon_32x32.png" "$conjunto/icon_16x16@2x.png"
  cp "$conjunto/icon_64x64.png" "$conjunto/icon_32x32@2x.png"
  cp "$conjunto/icon_256x256.png" "$conjunto/icon_128x128@2x.png"
  rm "$conjunto/icon_64x64.png"
  iconutil -c icns "$conjunto" -o "$raiz/target/fmc.icns"
  icone="$raiz/target/fmc.icns"
else
  icone="$png"
fi

# No Mac, o número da versão não pode começar com 0 (regra da Apple): a 0.1.2 vira 1.1.2 por dentro
# do programa. O nome do arquivo continua com a versão de verdade.
versaoApp="$versaoNumerica"
if [ "$sistema" = mac ] && [[ "$versaoApp" == 0.* ]]; then versaoApp="1.${versaoApp#0.}"; fi

echo "4/5 Montando o programa com o Java embutido..."
# Os mesmos módulos do gerar-exe.ps1.
modulos="javafx.controls,javafx.fxml,java.sql,java.desktop,java.naming,java.logging,java.management,java.xml,java.prefs,java.scripting,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.charsets,jdk.zipfs"
extras=()
if [ "$sistema" = mac ]; then extras+=(--mac-package-identifier br.org.fmc.sistema --mac-package-name FMC); fi
jpackage --type app-image \
  --name FMC \
  --app-version "$versaoApp" \
  --vendor "Fundação Manuel Cruz" \
  --description "Sistema de gestão de imóveis e aluguéis da Fundação Manuel Cruz" \
  --icon "$icone" \
  --input "$libs" \
  --main-jar "fmc-sistema-$versao.jar" \
  --main-class br.org.fmc.Launcher \
  --module-path "$javafx" \
  --add-modules "$modulos" \
  --java-options "--enable-native-access=javafx.graphics,ALL-UNNAMED" \
  --java-options "-Dfile.encoding=UTF-8" \
  "${extras[@]}" \
  --dest "$pacote"

echo "5/5 Montando o instalador..."
if [ "$sistema" = mac ]; then
  jpackage --type dmg \
    --app-image "$pacote/FMC.app" \
    --name FMC \
    --app-version "$versaoApp" \
    --vendor "Fundação Manuel Cruz" \
    --dest "$pacote"
  mv "$pacote/FMC-$versaoApp.dmg" "$pacote/FMC-Sistema-$versaoNumerica-mac-$arq.dmg"
else
  jpackage --type deb \
    --app-image "$pacote/FMC" \
    --name FMC \
    --linux-package-name fmc-sistema \
    --app-version "$versaoNumerica" \
    --vendor "Fundação Manuel Cruz" \
    --description "Sistema de gestão de imóveis e aluguéis da Fundação Manuel Cruz" \
    --linux-shortcut \
    --linux-menu-group Office \
    --dest "$pacote"
  mv "$pacote"/fmc-sistema_*.deb "$pacote/FMC-Sistema-$versaoNumerica-linux-amd64.deb"
  tar -czf "$pacote/FMC-Sistema-$versaoNumerica-linux-x64.tar.gz" -C "$pacote" FMC
fi

echo
echo "Pronto:"
ls -lh "$pacote" | grep -v '^total' | grep -v ' FMC'
