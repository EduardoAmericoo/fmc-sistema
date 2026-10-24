# Arquitetura da Solução: Sistema Fundação Manuel Cruz

**Disciplina:** GTI/POO, Projeto 2, CESAR School
**Entregável:** Sprint 7, arquitetura da solução
**Equipe:** _(preencher)_
**Responsável pela arquitetura:** _(preencher)_

## 1. Visão geral

Aplicação desktop para gestão de imóveis e aluguéis da Fundação Manuel Cruz. Roda localmente em um único computador, sem servidor. O código é organizado em camadas, e cada camada tem uma responsabilidade e só conversa com a camada vizinha.

## 2. Camadas do sistema

```
┌─────────────────────────────────────────────┐
│  Apresentação (ui)                          │  Telas FXML + controllers
├─────────────────────────────────────────────┤
│  Negócio (service)                          │  Regras: CPF, encargos, recibo
├─────────────────────────────────────────────┤
│  Acesso a dados (repository)                │  Salvar, buscar, listar (SQL)
├─────────────────────────────────────────────┤
│  Banco (db)                                 │  Conexão SQLite + schema.sql
└─────────────────────────────────────────────┘
   Transversais: model (entidades do domínio) e util (formatação, validações)
```

| Camada | Pacote | Responsabilidade | Exemplos |
|---|---|---|---|
| Apresentação | `br.org.fmc.ui` | Exibir telas, receber cliques e dados do usuário e chamar os serviços. Não tem regra de negócio nem SQL. | `PrincipalController`, `principal.fxml` |
| Negócio | `br.org.fmc.service` | Regras do sistema e validações. Coordena os repositórios. | Validação de CPF, cálculo de encargos, geração de recibo |
| Acesso a dados | `br.org.fmc.repository` | Único lugar com SQL. Converte linhas do banco em objetos e o contrário. | `ImovelRepository`, `ContratoRepository` |
| Banco | `br.org.fmc.db` | Abrir conexões e criar as tabelas. | `Database`, `schema.sql` |
| Modelo | `br.org.fmc.model` | Classes do domínio, sem dependência das outras camadas. | `Imovel`, `Inquilino`, `Contrato`, `Garantia` |
| Utilitários | `br.org.fmc.util` | Funções de apoio usadas por várias camadas. | Formatação de moeda e data |

**Regra de dependência:** `ui → service → repository → db`. A camada de cima chama a de baixo, nunca o contrário. Uma tela nunca acessa o banco diretamente. O `model` pode ser usado por todas as camadas.

**Fluxo de exemplo (cadastrar imóvel):** a tela coleta os campos, o controller chama o `ImovelService`, o service valida os dados, o `ImovelRepository` executa o `INSERT` no SQLite e o resultado volta pelo mesmo caminho para a tela.

## 3. Toolkit gráfico escolhido: JavaFX

| Critério | Decisão |
|---|---|
| Escolha | **JavaFX 21** (`javafx-controls` e `javafx-fxml`) |
| Alternativa descartada | Swing |

**Por que JavaFX:**
- As telas ficam em arquivos **FXML**, separadas da lógica. Isso combina com a arquitetura em camadas, porque o controller faz o papel da camada de apresentação.
- As telas podem ser montadas visualmente no **Scene Builder**, o que facilita o trabalho de quem não é da área de código.
- O visual é definido por **CSS** (`tema.css`), o que facilita seguir o design system e o protótipo (menu lateral, cartões, cores).
- É um toolkit moderno e mantido, com boa tabela e boa formatação visual para listagens de imóveis, contratos e cobranças.

**Contrapartida:** o JavaFX não vem junto com o JDK, então entra como dependência do Maven. Isso é resolvido automaticamente pelo `mvnw`.

## 4. Onde os dados ficam salvos: banco SQLite

| Critério | Decisão |
|---|---|
| Escolha | **SQLite**, banco relacional em um único arquivo local (`data/fmc.db`) |
| Acesso | JDBC (`sqlite-jdbc`), com SQL escrito nos repositories |
| Alternativa descartada | Arquivo local simples (CSV/JSON) |

**Por que SQLite e não arquivo simples:**
- O domínio tem várias entidades ligadas entre si (imóvel, inquilino, contrato, garantia, cobrança, pagamento, recibo). O modelo relacional com **chaves estrangeiras** (ativadas em `Database.conectar()`) garante a consistência desses vínculos.
- Permite consultas, filtros e relatórios com SQL, sem carregar e percorrer arquivos inteiros.
- Dá **transações**, que evitam dados pela metade se algo falhar.
- Não precisa instalar nem configurar servidor: o banco é um arquivo, ideal para um sistema que roda em um só computador.

**Criação das tabelas:** o `schema.sql` roda sempre que o sistema abre, usando `CREATE TABLE IF NOT EXISTS`, então nenhum dado existente é apagado. Cada história do MVP acrescenta suas tabelas nesse arquivo.

**Tabelas previstas:** usuario, imovel, inquilino, contrato, garantia, cobranca, pagamento, recibo.

## 5. Resumo técnico

| Item | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Interface | JavaFX + FXML + CSS |
| Persistência | SQLite via JDBC |
| Build | Maven (`mvnw`) |
| Testes | JUnit 5 |
| Versionamento | Git / GitHub |
