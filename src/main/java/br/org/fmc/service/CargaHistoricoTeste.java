package br.org.fmc.service;

import br.org.fmc.model.Aluguel;
import br.org.fmc.model.ContatoCobranca;
import br.org.fmc.model.Contrato;
import br.org.fmc.model.DispensaEncargos;
import br.org.fmc.model.Encargos;
import br.org.fmc.model.FormaPagamento;
import br.org.fmc.model.Imovel;
import br.org.fmc.model.MotivoEncerramento;
import br.org.fmc.model.Recibo;
import br.org.fmc.model.ResultadoContato;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.StatusContrato;
import br.org.fmc.model.TipoDocumento;
import br.org.fmc.model.Usuario;
import br.org.fmc.repository.AluguelRepository;
import br.org.fmc.repository.ContatoCobrancaRepository;
import br.org.fmc.repository.ContratoRepository;
import br.org.fmc.repository.ImovelRepository;
import br.org.fmc.repository.ReciboRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Histórico fictício dos dados de teste (revisão da SR2): 18 meses de aluguéis, com recibos de
 * verdade (que alimentam o Caixa e o Banco), perfis variados de pagador e um pouco de cada
 * situação que o sistema sabe tratar, para cada tela ter o que mostrar.
 *
 * Tudo é determinístico (depende só do número do contrato e do mês), então a carga sai igual
 * em qualquer computador.
 *
 * Perfis (pelo id do contrato): pontual (paga no vencimento ou até 3 dias antes), ocasional
 * (atrasa de vez em quando, às vezes com a multa dispensada), atrasa sempre e devedor (deve os
 * últimos 3 meses). O contratos.csv ainda diz quem está com 1 ou 2 meses em aberto.
 */
class CargaHistoricoTeste {

    /** Meses de histórico, contando o mês atual. */
    static final int MESES = 18;

    enum PerfilPagador { PONTUAL, OCASIONAL, ATRASA_SEMPRE, DEVEDOR }

    /** O que a carga fez, para o resumo da tela e para os testes. */
    record Resultado(int alugueis, int recibos, int dispensas, int contatos, int encerrados, int reajustes,
            int documentos) {
    }

    private final LocalDate hoje;
    private final Usuario admin;
    private final Usuario secretaria;
    private final Usuario aprendiz;
    private final AluguelRepository alugueis = new AluguelRepository();
    private final ReciboRepository recibos = new ReciboRepository();
    private final ContatoCobrancaRepository contatos = new ContatoCobrancaRepository();

    private int totalAlugueis;
    private int totalRecibos;
    private int totalDispensas;
    private int totalContatos;

    CargaHistoricoTeste(LocalDate hoje, Usuario admin, Usuario secretaria, Usuario aprendiz) {
        this.hoje = hoje;
        this.admin = admin;
        this.secretaria = secretaria;
        this.aprendiz = aprendiz;
    }

    static PerfilPagador perfil(Contrato c) {
        long resto = c.getId() % 10;
        if (resto == 9) {
            return PerfilPagador.DEVEDOR;
        }
        if (resto == 7) {
            return PerfilPagador.ATRASA_SEMPRE;
        }
        if (resto == 5 || resto == 6) {
            return PerfilPagador.OCASIONAL;
        }
        return PerfilPagador.PONTUAL;
    }

    Resultado carregar(Map<String, Integer> emAbertoDoCsv) throws SQLException {
        YearMonth atual = YearMonth.from(hoje);
        YearMonth inicio = atual.minusMonths(MESES - 1);
        // O saldo do Caixa e do Banco no começo do histórico (o Administrador informa uma vez).
        new ServicoCaixaBanco().informarSaldoInicial(inicio.minusMonths(1).atDay(1),
                new BigDecimal("1850.00"), new BigDecimal("42300.00"));

        List<Contrato> todos = new ContratoRepository().listarTodos();
        todos.sort(Comparator.comparing(Contrato::getId));
        Map<Long, LocalDate> saidas = escolherEncerrados(todos, emAbertoDoCsv);
        Recibo paraCancelar = null;
        for (Contrato c : todos) {
            int emAberto = emAbertoDoCsv.getOrDefault(c.getNumero(), 0);
            if (perfil(c) == PerfilPagador.DEVEDOR) {
                emAberto = Math.max(emAberto, 3);
            }
            Recibo ultimo = carregarContrato(c, inicio, atual, emAberto, saidas.get(c.getId()));
            if (paraCancelar == null && ultimo != null && perfil(c) == PerfilPagador.PONTUAL
                    && ultimo.getFormaPagamento() == FormaPagamento.PIX && !saidas.containsKey(c.getId())) {
                paraCancelar = ultimo;
            }
            // Antes do histórico, tudo conta como "pago no papel" (ver contrato_gerar_desde).
            alugueis.salvarGerarDesde(c.getId(), inicio);
        }
        cancelarERefazer(paraCancelar);
        carregarContatos();
        int encerrados = encerrarEArquivar(saidas);
        int reajustes = aplicarReajustes();
        int documentos = anexarDocumentos(todos, saidas);
        return new Resultado(totalAlugueis, totalRecibos, totalDispensas, totalContatos, encerrados, reajustes,
                documentos);
    }

    /** Três contratos de bons pagadores que já terminaram: as chaves foram devolvidas há 2 a 4 meses. */
    private Map<Long, LocalDate> escolherEncerrados(List<Contrato> todos, Map<String, Integer> emAberto) {
        Map<Long, LocalDate> saidas = new HashMap<>();
        int meses = 2;
        for (Contrato c : todos) {
            if (saidas.size() == 3) {
                break;
            }
            if (c.getId() % 10 == 3 && emAberto.getOrDefault(c.getNumero(), 0) == 0
                    && c.getDataEntregaChaves().isBefore(hoje.minusMonths(10))
                    && !c.getDataTermino().isBefore(hoje)) {
                saidas.put(c.getId(), c.vencimentoDe(YearMonth.from(hoje).minusMonths(meses)).plusDays(4));
                meses++;
            }
        }
        return saidas;
    }

    /** Os aluguéis do contrato no período, pagos conforme o perfil; os últimos vencidos ficam em aberto. */
    private Recibo carregarContrato(Contrato c, YearMonth inicio, YearMonth atual, int emAberto, LocalDate saida)
            throws SQLException {
        List<Aluguel> doContrato = new ArrayList<>();
        for (YearMonth mes = inicio; !mes.isAfter(atual); mes = mes.plusMonths(1)) {
            if (c.cobreCompetencia(mes) && (saida == null || !c.vencimentoDe(mes).isAfter(saida))) {
                Aluguel a = new Aluguel(c, mes);
                alugueis.salvar(a);
                doContrato.add(a);
                totalAlugueis++;
            }
        }
        List<Aluguel> vencidos = doContrato.stream().filter(a -> a.getVencimento().isBefore(hoje)).toList();
        Recibo ultimo = null;
        for (int i = 0; i < vencidos.size() - (saida == null ? emAberto : 0); i++) {
            Aluguel a = vencidos.get(i);
            LocalDate pagamento = dataDePagamento(c, a);
            if (pagamento.isAfter(hoje)) {
                continue; // ainda não pagou: fica em atraso
            }
            ultimo = receber(c, a, pagamento);
        }
        return ultimo;
    }

    /** Quando o inquilino pagou, conforme o perfil (sempre igual para o mesmo contrato e mês). */
    private LocalDate dataDePagamento(Contrato c, Aluguel a) {
        LocalDate venc = a.getVencimento();
        int mes = a.getCompetencia().getMonthValue();
        int semente = (int) ((c.getId() * 7 + mes * 13) % 100);
        return switch (perfil(c)) {
            case PONTUAL -> venc.minusDays(c.getId() % 4);
            case OCASIONAL -> mes % 4 == c.getId() % 4 ? venc.plusDays(8 + semente % 13) : venc.minusDays(1);
            case ATRASA_SEMPRE -> semente % 3 == 0 ? venc.plusDays(2) : venc.plusDays(6 + semente % 25);
            case DEVEDOR -> venc.plusDays(10 + semente % 31);
        };
    }

    private Recibo receber(Contrato c, Aluguel a, LocalDate pagamento) throws SQLException {
        // Quase tudo por PIX, transferência e boleto; dinheiro e cheque são raros (cerca de 5%), como
        // hoje. O sistema ainda não registra o depósito do Caixa no Banco, então o Caixa só acumula.
        int giro = (int) ((c.getId() * 3 + a.getCompetencia().getMonthValue()) % 40);
        FormaPagamento forma = giro < 24 ? FormaPagamento.PIX : giro < 31 ? FormaPagamento.TRANSFERENCIA
                : giro < 38 ? FormaPagamento.BOLETO_BANCARIO : giro == 38 ? FormaPagamento.DINHEIRO
                : FormaPagamento.CHEQUE;
        String documento = switch (forma) {
            case PIX -> "PIX E" + (100000 + c.getId() * 31 + a.getCompetencia().getMonthValue());
            case CHEQUE -> "Cheque nº " + (2000 + c.getId());
            case TRANSFERENCIA -> "TED " + (500 + c.getId());
            default -> "";
        };
        Usuario quem = a.getCompetencia().getMonthValue() % 2 == 0 ? secretaria : admin;
        Encargos encargos = a.encargosEm(pagamento);
        totalRecibos++;
        // Alguns atrasos de quem costuma pagar em dia tiveram a multa dispensada (BOARD-66).
        if (perfil(c) == PerfilPagador.OCASIONAL && encargos.temEncargos() && c.getId() % 3 == 0) {
            totalDispensas++;
            DispensaEncargos dispensa = DispensaEncargos.de(encargos,
                    "Primeiro atraso do ano; acordo com o inquilino autorizado pela diretoria", admin,
                    pagamento.atTime(10, 30));
            return recibos.registrar(a, pagamento, forma, encargos.dispensados(), documento, quem, dispensa);
        }
        return recibos.registrar(a, pagamento, forma, encargos, documento, quem);
    }

    /** Um recibo lançado por engano: cancelado no dia seguinte (estorno no Banco) e recebido de novo. */
    private void cancelarERefazer(Recibo recibo) throws SQLException {
        if (recibo == null) {
            return;
        }
        LocalDate pagamento = recibo.getDataPagamento();
        recibos.cancelar(recibo, "PIX registrado no aluguel errado (era do apartamento vizinho)", admin,
                pagamento.plusDays(1).atTime(9, 15));
        recibos.registrar(recibo.getAluguel(), pagamento, FormaPagamento.PIX, recibo.getAluguel().encargosEm(pagamento),
                "PIX conferido no extrato", admin);
        totalRecibos++;
    }

    /** Contatos de cobrança variados para quem está devendo: nunca contatado, não atendeu, promessas... */
    private void carregarContatos() throws SQLException {
        List<ServicoCobranca.Linha> devedores = ServicoCobranca.montarLista(
                ServicoAlugueis.listaDeCobranca(alugueis.listarEmAberto(), hoje), Map.of(), hoje);
        LocalDateTime agora = hoje.atTime(14, 0);
        for (ServicoCobranca.Linha l : devedores) {
            long id = l.contrato().getId();
            String email = l.contrato().getInquilino().getEmail();
            switch ((int) (id % 6)) {
                case 1 -> contato(id, agora.minusDays(6), ResultadoContato.NAO_ATENDEU, null, "Caixa postal", aprendiz);
                case 2 -> contato(id, agora.minusDays(10), ResultadoContato.PROMETEU_PAGAR, hoje.plusDays(3),
                        "Vai pagar quando receber o salário", secretaria);
                case 3 -> {
                    contato(id, agora.minusDays(20), ResultadoContato.NAO_ATENDEU, null, "", aprendiz);
                    contato(id, agora.minusDays(12), ResultadoContato.PROMETEU_PAGAR, hoje.minusDays(4),
                            "Prometeu pagar tudo de uma vez", secretaria);
                }
                case 4 -> {
                    contato(id, agora.minusDays(15), ResultadoContato.NAO_ATENDEU, null, "", aprendiz);
                    contato(id, agora.minusDays(2), ResultadoContato.EMAIL_ENVIADO, null,
                            "Para " + (email == null ? "o e-mail do cadastro" : email), secretaria);
                }
                case 5 -> {
                    contato(id, agora.minusDays(25), ResultadoContato.SEM_ACORDO, null,
                            "Disse que só paga depois de consertarem o vazamento", secretaria);
                    contato(id, agora.minusDays(5), ResultadoContato.PROMETEU_PAGAR, hoje.plusDays(15),
                            "Metade agora, o resto no dia 30", admin);
                }
                default -> { } // nunca contatado
            }
            if (l.mesesEmAberto() >= 3) {
                contato(id, agora.minusDays(45), ResultadoContato.TELEFONE_ERRADO, null,
                        "Número não existe; pegar outro com o fiador", aprendiz);
            }
        }
    }

    private void contato(long contratoId, LocalDateTime quando, ResultadoContato tipo, LocalDate prometido,
            String observacao, Usuario quem) throws SQLException {
        contatos.salvar(new ContatoCobranca(null, contratoId, quando, tipo, prometido, observacao, quem));
        totalContatos++;
    }

    /** Encerra os contratos escolhidos, arquiva um dos inquilinos que saiu e um imóvel vendido. */
    private int encerrarEArquivar(Map<Long, LocalDate> saidas) throws SQLException {
        ServicoContratos servico = new ServicoContratos();
        MotivoEncerramento[] motivos = {MotivoEncerramento.SAIDA_DO_INQUILINO, MotivoEncerramento.FIM_DO_PRAZO,
            MotivoEncerramento.SAIDA_DO_INQUILINO};
        String[] observacoes = {"Mudou-se para Salvador a trabalho", "Não quis renovar", "Comprou casa própria"};
        int i = 0;
        Long primeiroInquilino = null;
        for (Map.Entry<Long, LocalDate> e : saidas.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            Contrato c = servico.encerrar(e.getKey(), e.getValue(), motivos[i], observacoes[i]);
            if (primeiroInquilino == null) {
                primeiroInquilino = c.getInquilino().getId();
            }
            i++;
        }
        if (primeiroInquilino != null) {
            new ServicoInquilinos().arquivar(primeiroInquilino, "Mudou-se de cidade; contrato encerrado");
        }
        // Um imóvel vago que a Fundação vendeu: sai da lista, mas o histórico fica.
        Set<Long> comContrato = new java.util.HashSet<>();
        new ContratoRepository().listarTodos().forEach(c -> comContrato.add(c.getImovel().getId()));
        for (Imovel imovel : new ImovelRepository().listarTodos()) {
            if (imovel.getSituacao() == SituacaoImovel.VAGO && !comContrato.contains(imovel.getId())) {
                new ServicoImoveis().arquivar(imovel.getId(), "Vendido pela Fundação em 2026");
                break;
            }
        }
        return i;
    }

    /** Metade dos contratos com reajuste vencido já foi reajustada (média INPC + IGP-DI digitada). */
    private int aplicarReajustes() throws SQLException {
        ServicoContratos servico = new ServicoContratos();
        int feitos = 0;
        for (Contrato c : new ContratoRepository().listarTodos()) {
            if (c.getStatus() == StatusContrato.ATIVO && c.reajustePendente(hoje) && c.getId() % 2 == 0) {
                try {
                    servico.reajustar(c.getId(), c.getId() % 4 == 0 ? "4,85" : "5,12");
                    feitos++;
                } catch (RegraNegocioException e) {
                    // contrato que a regra não deixa reajustar: segue sem reajuste
                }
            }
        }
        return feitos;
    }

    /** Documentos fictícios nos 8 primeiros contratos e nos encerrados (vistoria e devolução das chaves). */
    private int anexarDocumentos(List<Contrato> todos, Map<Long, LocalDate> saidas) throws SQLException {
        ServicoDocumentos servico = new ServicoDocumentos();
        int total = 0;
        try {
            Path pdf = Files.createTempFile("documento-ficticio", ".pdf");
            Files.writeString(pdf, "%PDF-1.4\n% Documento fictício dos dados de teste do Sistema FMC\n",
                    StandardCharsets.UTF_8);
            TipoDocumento[] tipos = {TipoDocumento.PROPOSTA_LOCACAO, TipoDocumento.RG_CPF_INQUILINO,
                TipoDocumento.COMPROVANTE_RENDA, TipoDocumento.CONTRATO_ASSINADO, TipoDocumento.VISTORIA,
                TipoDocumento.TERMO_ENTREGA_CHAVES};
            for (Contrato c : todos.subList(0, Math.min(8, todos.size()))) {
                int quantos = 3 + (int) (c.getId() % 4); // alguns completos, outros faltando documentos
                for (int i = 0; i < quantos && i < tipos.length; i++) {
                    servico.enviar(c.getId(), tipos[i], c.getDataEntregaChaves().minusDays(5 - i), pdf, null);
                    total++;
                }
            }
            for (Map.Entry<Long, LocalDate> e : saidas.entrySet()) {
                servico.enviar(e.getKey(), TipoDocumento.VISTORIA_SAIDA, e.getValue(), pdf, "Sem pendências");
                servico.enviar(e.getKey(), TipoDocumento.DEVOLUCAO_CHAVES, e.getValue(), pdf, null);
                total += 2;
            }
            Files.deleteIfExists(pdf);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar os documentos fictícios.", e);
        }
        return total;
    }
}
