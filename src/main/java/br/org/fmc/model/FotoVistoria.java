package br.org.fmc.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Uma foto da vistoria de um contrato (entrada ou saída), de um cômodo.
 *
 * @param arquivo      caminho dentro de data/documentos (a foto já reduzida)
 * @param hashSha256   o "lacre" do arquivo guardado: se a foto for editada depois, não confere mais
 * @param registradaEm gravado pelo sistema na hora do envio (não dá para mudar)
 * @param tiradaEm     quando o celular ou a câmera tirou a foto (lido do arquivo; null se não houver)
 * @param arquivadaEm  null = ativa; preenchido = enviada por engano (não existe excluir)
 */
public record FotoVistoria(Long id, long contratoId, EtapaVistoria etapa, String comodo, String observacao,
        String arquivo, String nomeOriginal, String hashSha256, LocalDateTime registradaEm, String registradaPor,
        LocalDateTime tiradaEm,
        LocalDateTime arquivadaEm) {

    /** Cômodos sugeridos na tela (dá para digitar outro). */
    public static final List<String> COMODOS = List.of("Fachada", "Sala", "Cozinha", "Quarto 1", "Quarto 2",
            "Quarto 3", "Banheiro", "Banheiro 2", "Área de serviço", "Varanda", "Área externa", "Garagem",
            "Medidores (água e luz)", "Chaves");

    public boolean arquivada() {
        return arquivadaEm != null;
    }
}
