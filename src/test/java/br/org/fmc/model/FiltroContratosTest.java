package br.org.fmc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.org.fmc.service.ServicoContratos;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** BOARD-59: situação do contrato, busca e filtro da lista. */
class FiltroContratosTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 28);

    /** Contrato com 12 meses que termina na data dada. */
    private static Contrato contrato(String numero, String codigoImovel, String unidade, String endereco,
            String nome, Cpf cpf, LocalDate termino) {
        Imovel imovel = new Imovel(codigoImovel, TipoImovel.APARTAMENTO, endereco, new BigDecimal("1000"));
        imovel.setUnidade(unidade);
        Inquilino inquilino = new Inquilino(nome, cpf);
        inquilino.setRendaMensal(new BigDecimal("5000"));
        Fiador fiador = new Fiador("José Fiador", Fixtures.CPF_B);
        Contrato c = new Contrato(imovel, inquilino, new Fianca(fiador), PrazoContrato.MESES_12,
                termino.minusMonths(12));
        c.setNumero(numero);
        return c;
    }

    private static Contrato terminandoEm(LocalDate termino) {
        return contrato("CT-1", "IM-1", null, "Rua A, 1", "Maria Teste", Fixtures.CPF_A, termino);
    }

    private final Contrato ativo = contrato("CT-1001", "A-01", "Apto 101", "Rua das Flores, 10",
            "Maria Souza", new Cpf("529.982.247-25"), HOJE.plusMonths(8));
    private final Contrato vencendo = contrato("CT-1002", "A-02", "Apto 102", "Rua das Flores, 10",
            "João Pereira", new Cpf("111.444.777-35"), HOJE.plusDays(35));
    private final Contrato vencido = contrato("CT-1003", "C-03", null, "Avenida Conde da Boa Vista, 500",
            "Ana Lúcia Lima", new Cpf("390.533.447-05"), HOJE.minusDays(40));
    private final Contrato encerrado = contrato("CT-1004", "C-04", null, "Rua do Sol, 7",
            "Carlos Mendes", new Cpf("168.995.350-09"), HOJE.plusMonths(3));
    private List<Contrato> todos;

    {
        encerrado.setStatus(StatusContrato.ENCERRADO);
        todos = List.of(ativo, vencendo, vencido, encerrado);
    }

    @Test
    void situacaoSegueOStatusEAData() {
        assertEquals(SituacaoContrato.ATIVO, SituacaoContrato.de(ativo, HOJE));
        assertEquals(SituacaoContrato.VENCENDO, SituacaoContrato.de(vencendo, HOJE));
        assertEquals(SituacaoContrato.VENCIDO, SituacaoContrato.de(vencido, HOJE));
        assertEquals(SituacaoContrato.ENCERRADO, SituacaoContrato.de(encerrado, HOJE));
    }

    @Test
    void limitesDaSituacao() {
        assertEquals(SituacaoContrato.VENCIDO, SituacaoContrato.de(terminandoEm(HOJE.minusDays(1)), HOJE));
        assertEquals(SituacaoContrato.VENCENDO, SituacaoContrato.de(terminandoEm(HOJE), HOJE));
        assertEquals(SituacaoContrato.VENCENDO, SituacaoContrato.de(terminandoEm(HOJE.plusDays(60)), HOJE));
        assertEquals(SituacaoContrato.ATIVO, SituacaoContrato.de(terminandoEm(HOJE.plusDays(61)), HOJE));
    }

    @Test
    void contratoEncerradoNuncaApareceComoVencido() {
        Contrato c = terminandoEm(HOJE.minusDays(10));
        c.setStatus(StatusContrato.ENCERRADO);
        assertEquals(SituacaoContrato.ENCERRADO, SituacaoContrato.de(c, HOJE));
    }

    @Test
    void semFiltroMostraTodos() {
        assertEquals(todos, ServicoContratos.filtrar(todos, null, "", HOJE));
        assertEquals(todos, ServicoContratos.filtrar(todos, null, null, HOJE));
        assertEquals(todos, ServicoContratos.filtrar(todos, null, "   ", HOJE));
    }

    @Test
    void filtraPorCadaSituacao() {
        assertEquals(List.of(ativo), ServicoContratos.filtrar(todos, SituacaoContrato.ATIVO, "", HOJE));
        assertEquals(List.of(vencendo), ServicoContratos.filtrar(todos, SituacaoContrato.VENCENDO, "", HOJE));
        assertEquals(List.of(vencido), ServicoContratos.filtrar(todos, SituacaoContrato.VENCIDO, "", HOJE));
        assertEquals(List.of(encerrado), ServicoContratos.filtrar(todos, SituacaoContrato.ENCERRADO, "", HOJE));
    }

    @Test
    void buscaPorNumeroDoContrato() {
        assertEquals(List.of(vencendo), ServicoContratos.filtrar(todos, null, "ct-1002", HOJE));
        assertEquals(List.of(vencendo), ServicoContratos.filtrar(todos, null, "1002", HOJE));
    }

    @Test
    void buscaPorImovel() {
        assertEquals(List.of(ativo), ServicoContratos.filtrar(todos, null, "a-01", HOJE));
        assertEquals(List.of(vencendo), ServicoContratos.filtrar(todos, null, "apto 102", HOJE));
        assertEquals(List.of(ativo, vencendo), ServicoContratos.filtrar(todos, null, "flores", HOJE));
        assertEquals(List.of(vencido), ServicoContratos.filtrar(todos, null, "conde da boa vista", HOJE));
    }

    @Test
    void buscaPorInquilinoIgnorandoAcentosEMaiusculas() {
        assertEquals(List.of(vencendo), ServicoContratos.filtrar(todos, null, "JOAO", HOJE));
        assertEquals(List.of(vencido), ServicoContratos.filtrar(todos, null, "ana lucia", HOJE));
        assertEquals(List.of(ativo), ServicoContratos.filtrar(todos, null, "souza maria", HOJE));
    }

    @Test
    void buscaPorCpfComOuSemMascara() {
        assertEquals(List.of(ativo), ServicoContratos.filtrar(todos, null, "529.982.247-25", HOJE));
        assertEquals(List.of(ativo), ServicoContratos.filtrar(todos, null, "52998224725", HOJE));
        assertEquals(List.of(ativo), ServicoContratos.filtrar(todos, null, "529.982", HOJE));
        assertEquals(List.of(encerrado), ServicoContratos.filtrar(todos, null, "168.995", HOJE));
    }

    @Test
    void combinaSituacaoEBusca() {
        assertEquals(List.of(vencendo),
                ServicoContratos.filtrar(todos, SituacaoContrato.VENCENDO, "flores", HOJE));
        assertEquals(List.of(), ServicoContratos.filtrar(todos, SituacaoContrato.VENCIDO, "flores", HOJE));
    }

    @Test
    void semCorrespondenciaNaoMostraNada() {
        assertEquals(List.of(), ServicoContratos.filtrar(todos, null, "inexistente", HOJE));
        assertFalse(ativo.atendeBusca("999.999"));
        assertTrue(ativo.atendeBusca(null));
    }
}
