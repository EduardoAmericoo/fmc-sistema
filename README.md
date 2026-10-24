# Sistema Fundação Manuel Cruz

Sistema de gestão de imóveis e aluguéis da Fundação Manuel Cruz.
Projeto 2 da CESAR School (GTI/POO). Java 21+, JavaFX, SQLite e Maven.

## O que instalar (uma vez só)

1. **JDK 21 ou mais novo**: https://adoptium.net (escolha a versão 21 LTS).
2. **VS Code**: https://code.visualstudio.com, com a extensão **Extension Pack for Java**.
   O VS Code sugere a extensão sozinho ao abrir o projeto.
3. **GitHub Desktop**: https://desktop.github.com

Não é preciso instalar o Maven. O projeto traz o `mvnw`, que baixa tudo sozinho na primeira vez.

## Como pegar o projeto

1. No GitHub Desktop: **File → Clone repository**, escolha `fmc-sistema`.
2. Clique em **Open in Visual Studio Code**.

## Como rodar

**Pelo VS Code:** abra a aba *Run and Debug* (Ctrl+Shift+D) e clique em **▶ Rodar o sistema**.

**Pelo terminal** (dentro da pasta do projeto):

```
mvnw javafx:run
```

Na primeira vez demora alguns minutos, porque baixa o Maven, o JavaFX e o driver do SQLite.

## Primeiro acesso

O sistema nasce com um único usuário, o **Administrador**:

| E-mail | Senha |
|---|---|
| `admin@fmc.org` | `Fmc@2026` |

É ele quem cadastra os outros usuários. Esta senha é só para desenvolvimento e testes; a troca de senha pelo próprio usuário é a história desejável D2.

## Como rodar os testes

```
mvnw test
```

## Onde fica cada coisa

| Pasta | O que vai lá |
|---|---|
| `src/main/java/br/org/fmc/model` | Classes do domínio: Imovel, Inquilino, Contrato, Garantia… |
| `src/main/java/br/org/fmc/repository` | Acesso ao banco: salvar, buscar, listar |
| `src/main/java/br/org/fmc/service` | Regras de negócio: CPF, encargos, recibo… |
| `src/main/java/br/org/fmc/ui` | Controllers das telas |
| `src/main/java/br/org/fmc/db` | Conexão com o SQLite |
| `src/main/java/br/org/fmc/util` | Utilitários (formatação, validações gerais) |
| `src/main/resources/br/org/fmc/ui` | Telas `.fxml` (dá para editar no Scene Builder) |
| `src/main/resources/br/org/fmc/css/tema.css` | Cores e fontes do design system |
| `src/main/resources/db/schema.sql` | Tabelas do banco |
| `src/test/java` | Testes automatizados (JUnit) |

Cada pacote tem um arquivo `package-info.java` explicando o que entra nele.

**Fluxo de uma funcionalidade:** tela (`ui`) → regra (`service`) → banco (`repository`), usando as classes de `model`.

## Banco de dados

O banco é o arquivo `data/fmc.db`, criado automaticamente ao abrir o sistema.
Ele **não** vai para o GitHub: cada pessoa tem o seu, com dados de teste.
Para recomeçar do zero, apague a pasta `data/`.

Para criar uma tabela nova, acrescente um `CREATE TABLE IF NOT EXISTS ...` em `schema.sql`.

⚠️ Nunca use nomes ou CPFs reais da Fundação como dados de teste.

## Dados de teste

Para testar ou apresentar o sistema com dados fictícios (124 imóveis, 110 inquilinos e usuários de teste):

- **No VS Code:** em Run and Debug, escolha **"Rodar com dados de teste (apaga tudo e recarrega)"** e aperte ▶.
- **No terminal (Windows):** `$env:FMC_DADOS_TESTE="recarregar"; .\mvnw.cmd javafx:run`
- **No terminal (Mac/Linux):** `FMC_DADOS_TESTE=recarregar ./mvnw javafx:run`

⚠️ Essa opção **apaga todos os dados** do seu banco local e carrega os de teste do zero. Use a configuração normal ("Rodar o sistema") no dia a dia.

Usuários de teste (senha `teste123`): `secretaria@exemplo.org` (Secretaria), `financeiro@exemplo.org` (Financeiro) e
`aprendiz@exemplo.org` (Secretaria que não pode cadastrar inquilino novo). O Administrador continua `admin@fmc.org`.

Os dados ficam em `src/main/resources/dados-teste/*.csv`, exportados da planilha "Dados de teste FMC". Os arquivos de
contratos, fiadores e cauções já estão prontos e passam a ser carregados quando o cadastro de contratos (BOARD-36/57) existir.

## Visual das telas (design system)

O sistema segue o design system da Fundação (azul #1D4D7A, fontes Urbanist e Plus Jakarta Sans):

- **AtlantaFX** dá a base moderna dos controles e **`css/tema.css`** aplica as cores e medidas da Fundação.
- **Ícones:** pacote Feather do Ikonli. No FXML: `<FontIcon iconLiteral="fth-home"/>`
  (lista completa: https://kordamp.org/ikonli/cheat-sheet-feather.html).
- **Fontes:** já estão em `src/main/resources/br/org/fmc/fonts/` (licença livre, Google Fonts).
- **Modelo para telas novas:** a tela de Inquilinos (`inquilinos.fxml` + `InquilinosController`):
  cabeçalho com título e botão principal, tabela num cartão (`tabela-fmc`) e o formulário numa
  janela por cima da tela (`Navegador.mostrarJanela`).

## Combinados de trabalho com Git

As regras completas, com passo a passo e prompts prontos para a IA, estão no **Guia de Git do Grupo FMC** (peça o link ao PO). O resumo:

1. **Ninguém faz commit direto na `main`.** Tudo entra por Pull Request, com 1 aprovação de outra pessoa.
2. **Uma tarefa do Plane = uma branch**, no modelo `tipo/BOARD-numero-descricao`.
   Ex.: `feature/BOARD-53-cadastro-usuarios` (tipos: `feature/`, `fix/`, `docs/`, `test/`).
3. Antes de criar a branch, atualize a `main` (**Pull**).
4. Commits pequenos, no modelo `tipo(BOARD-numero): o que foi feito`.
   Ex.: `feat(BOARD-53): cria tela de cadastro de usuários`.
5. Antes do Pull Request, o sistema precisa compilar e abrir.
