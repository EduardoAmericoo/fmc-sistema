@echo off
cd /d "%~dp0"
if not exist "target\executavel-pagamento-boleto\FMC-Sistema\FMC-Sistema.exe" (
    echo Executavel nao encontrado. A pasta target\executavel-pagamento-boleto deve permanecer dentro do projeto.
    pause
    exit /b 1
)
start "" "target\executavel-pagamento-boleto\FMC-Sistema\FMC-Sistema.exe"
