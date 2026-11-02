package br.org.fmc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.org.fmc.model.Imovel;
import br.org.fmc.model.SituacaoImovel;
import br.org.fmc.model.TipoImovel;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** BOARD-55: filtro por situação e busca por identificação ou endereço. */
class FiltroImoveisTest {

    private final Imovel vago = imovel("A-01", "Apto 101", "Rua das Flores, 10", "Várzea", SituacaoImovel.VAGO);
    private final Imovel ocupado = imovel("A-02", "Apto 102", "Rua das Flores, 10", "Várzea", SituacaoImovel.OCUPADO);
    private final Imovel manutencao = imovel("C-03", null, "Avenida Conde da Boa Vista, 500", "Boa Vista",
            SituacaoImovel.EM_MANUTENCAO);
    private final List<Imovel> todos = List.of(vago, ocupado, manutencao);

    private static Imovel imovel(String codigo, String unidade, String endereco, String bairro,
                                 SituacaoImovel situacao) {
        Imovel i = new Imovel(codigo, TipoImovel.APARTAMENTO, endereco, new BigDecimal("1000"));
        i.setUnidade(unidade);
        i.setBairro(bairro);
        i.setSituacao(situacao);
        return i;
    }

    @Test
    void semFiltroMostraTodos() {
        assertEquals(todos, ServicoImoveis.filtrar(todos, null, ""));
        assertEquals(todos, ServicoImoveis.filtrar(todos, null, null));
        assertEquals(todos, ServicoImoveis.filtrar(todos, null, "   "));
    }

    @Test
    void filtraPorSituacao() {
        assertEquals(List.of(vago), ServicoImoveis.filtrar(todos, SituacaoImovel.VAGO, ""));
        assertEquals(List.of(ocupado), ServicoImoveis.filtrar(todos, SituacaoImovel.OCUPADO, ""));
        assertEquals(List.of(manutencao), ServicoImoveis.filtrar(todos, SituacaoImovel.EM_MANUTENCAO, ""));
    }

    @Test
    void buscaPorCodigoUnidadeEnderecoOuBairro() {
        assertEquals(List.of(ocupado), ServicoImoveis.filtrar(todos, null, "a-02"));
        assertEquals(List.of(vago), ServicoImoveis.filtrar(todos, null, "apto 101"));
        assertEquals(List.of(manutencao), ServicoImoveis.filtrar(todos, null, "conde"));
        assertEquals(List.of(manutencao), ServicoImoveis.filtrar(todos, null, "boa vista"));
    }

    @Test
    void buscaIgnoraMaiusculasEAcentos() {
        assertEquals(List.of(vago, ocupado), ServicoImoveis.filtrar(todos, null, "VARZEA"));
        assertEquals(List.of(vago, ocupado), ServicoImoveis.filtrar(todos, null, "várzea"));
    }

    @Test
    void combinaSituacaoEBusca() {
        assertEquals(List.of(ocupado), ServicoImoveis.filtrar(todos, SituacaoImovel.OCUPADO, "flores"));
        assertEquals(List.of(), ServicoImoveis.filtrar(todos, SituacaoImovel.EM_MANUTENCAO, "flores"));
    }

    @Test
    void naoEncontraNadaQuandoNaoHaCorrespondencia() {
        assertEquals(List.of(), ServicoImoveis.filtrar(todos, null, "inexistente"));
    }
}
