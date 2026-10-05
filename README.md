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

## Fontes do design system (opcional)

Para usar as fontes oficiais (Urbanist e Plus Jakarta Sans), coloque os arquivos `.ttf` em
`src/main/resources/br/org/fmc/fonts/`. Sem elas, o sistema usa a fonte Segoe UI.

## Combinados de trabalho com Git

1. Antes de começar: **Fetch/Pull** no GitHub Desktop.
2. Trabalhe numa **branch** com o número do card. Ex.: `BOARD-53-cadastro-usuarios`.
3. Faça commits pequenos, com mensagem clara.
4. Ao terminar, **Push** e abra um **Pull Request** para alguém revisar antes de juntar na `main`.
