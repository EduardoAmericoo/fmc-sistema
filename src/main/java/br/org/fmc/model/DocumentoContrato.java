package br.org.fmc.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Documento digitalizado anexado a um contrato (PDF do scanner ou foto do celular).
 *
 * @param arquivo      caminho da cópia dentro da pasta data/documentos (ex.: "CT-1001/rg.pdf")
 * @param nomeOriginal nome que o arquivo tinha no computador de quem enviou
 * @param tamanho      tamanho em bytes
 * @param arquivadoEm  preenchido quando o documento foi arquivado (enviado por engano); null se está ativo
 */
public record DocumentoContrato(Long id, long contratoId, TipoDocumento tipo, LocalDate data, String arquivo,
        String nomeOriginal, long tamanho, String observacao, LocalDateTime enviadoEm, String enviadoPor,
        LocalDateTime arquivadoEm) {

    public DocumentoContrato {
        if (contratoId <= 0) {
            throw new IllegalArgumentException("O contrato do documento é inválido.");
        }
        tipo = Valida.obrigatorio(tipo, "o tipo de documento");
        data = Valida.obrigatorio(data, "a data do documento");
        arquivo = Valida.textoObrigatorio(arquivo, "o arquivo");
        nomeOriginal = Valida.textoObrigatorio(nomeOriginal, "o nome do arquivo");
        observacao = Valida.textoOpcional(observacao);
        enviadoEm = Valida.obrigatorio(enviadoEm, "a data do envio");
        enviadoPor = Valida.textoObrigatorio(enviadoPor, "quem enviou o documento");
    }

    public boolean arquivado() {
        return arquivadoEm != null;
    }
}
