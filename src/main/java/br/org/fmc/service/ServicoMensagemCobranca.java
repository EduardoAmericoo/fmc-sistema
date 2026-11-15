package br.org.fmc.service;

import br.org.fmc.model.Acao;
import br.org.fmc.model.Aluguel;
import br.org.fmc.model.ContatoCobranca;
import br.org.fmc.model.Inquilino;
import br.org.fmc.model.ResultadoContato;
import br.org.fmc.service.ServicoCobranca.Linha;
import br.org.fmc.util.Correio;
import br.org.fmc.util.Formatos;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * BOARD-189 · Mensagem de cobrança pronta, mandada por e-mail ou WhatsApp pela janela de contato.
 *
 * - E-mail: o sistema envia de verdade, pelo mesmo Correio do "Esqueci a senha".
 * - WhatsApp: o sistema monta o link do WhatsApp (wa.me) com o número e o texto prontos; a
 *   pessoa confere e aperta enviar. Não precisa de conta empresarial nem tem custo.
 *
 * Um inquilino por vez, com alguém conferindo antes de mandar (nada de disparo em massa).
 * Cada envio entra sozinho no histórico de contatos de cobrança.
 */
public class ServicoMensagemCobranca {

    static final String ASSINATURA = "Fundação Manuel Cruz · Administração de imóveis";

    private final Correio correio;
    private final ServicoCobranca cobranca;

    public ServicoMensagemCobranca() {
        this(Correio.doSistema(), new ServicoCobranca());
    }

    ServicoMensagemCobranca(Correio correio, ServicoCobranca cobranca) {
        this.correio = correio;
        this.cobranca = cobranca;
    }

    /** O assunto do e-mail e o texto (o mesmo para e-mail e WhatsApp). */
    public record Mensagem(String assunto, String texto) {
    }

    /**
     * A mensagem pronta. Um aluguel em atraso: lembrete gentil ("se já pagou, desconsidere").
     * Dois ou mais: cobrança mais firme, pedindo para regularizar ou combinar um acordo.
     */
    public Mensagem mensagem(Linha linha) {
        Inquilino inquilino = linha.contrato().getInquilino();
        String imovel = linha.contrato().getImovel().getCodigo();
        String valor = Formatos.moeda(linha.valorAtualizado());
        String meses = meses(linha.alugueis());
        String nome = primeiroNome(inquilino.getNome());
        if (linha.mesesEmAberto() == 1) {
            return new Mensagem("Lembrete: aluguel de " + meses + " em aberto · " + imovel,
                    "Olá, " + nome + "! Aqui é da Fundação Manuel Cruz.\n\n"
                            + "O aluguel de " + meses + " do imóvel " + imovel + " ainda não aparece como pago "
                            + "no nosso sistema. O valor atualizado hoje é " + valor
                            + " (com multa, juros e correção).\n\n"
                            + "Se você já pagou, por favor desconsidere esta mensagem e nos mande o comprovante. "
                            + "Qualquer dúvida, estamos à disposição.\n\n" + ASSINATURA);
        }
        return new Mensagem("Aluguéis em atraso · " + imovel,
                "Olá, " + nome + ". Aqui é da Fundação Manuel Cruz.\n\n"
                        + "Constam em aberto " + linha.mesesEmAberto() + " aluguéis do imóvel " + imovel + " ("
                        + meses + "), no valor atualizado de " + valor + " (com multa, juros e correção até hoje).\n\n"
                        + "Pedimos que entre em contato com a Fundação o quanto antes para regularizar o pagamento "
                        + "ou combinar um acordo. Se você já pagou, por favor nos mande o comprovante.\n\n"
                        + ASSINATURA);
    }

    /** O e-mail do inquilino, ou uma explicação de por que não dá para mandar (null = dá). */
    public String problemaEmail(Linha linha) {
        String email = linha.contrato().getInquilino().getEmail();
        if (email == null || email.isBlank()) {
            return "O inquilino não tem e-mail no cadastro.";
        }
        if (correio == null) {
            return "O envio de e-mail não está configurado neste computador.";
        }
        return null;
    }

    /** O telefone pronto para o WhatsApp, ou uma explicação de por que não dá (null = dá). */
    public String problemaWhatsApp(Linha linha) {
        return numeroWhatsApp(linha.contrato().getInquilino().getTelefone()) == null
                ? "O inquilino não tem celular com DDD no cadastro." : null;
    }

    /**
     * Envia o e-mail e registra no histórico. O texto é o que a pessoa conferiu na janela
     * (pode ter sido ajustado); em branco, usa a mensagem pronta.
     */
    public ContatoCobranca enviarEmail(Linha linha, String texto) throws SQLException, IOException {
        Sessao.exigir(Acao.REGISTRAR_CONTATO_COBRANCA);
        String problema = problemaEmail(linha);
        if (problema != null) {
            throw new RegraNegocioException(problema);
        }
        Mensagem pronta = mensagem(linha);
        String email = linha.contrato().getInquilino().getEmail().strip();
        correio.enviar(email, pronta.assunto(), texto == null || texto.isBlank() ? pronta.texto() : texto);
        return cobranca.registrarContato(linha.contrato().getId(), ResultadoContato.EMAIL_ENVIADO, null,
                "Para " + email + (correio.simulado() ? " (modo de teste: salvo na caixa de saída)" : ""));
    }

    /**
     * O link que abre o WhatsApp com o número e o texto prontos, e o registro no histórico.
     * O sistema não sabe se a pessoa apertou "enviar"; registra que a mensagem foi aberta para envio.
     */
    public String abrirWhatsApp(Linha linha, String texto) throws SQLException {
        Sessao.exigir(Acao.REGISTRAR_CONTATO_COBRANCA);
        String link = linkWhatsApp(linha, texto);
        cobranca.registrarContato(linha.contrato().getId(), ResultadoContato.WHATSAPP_ENVIADO, null,
                "Para " + linha.contrato().getInquilino().getTelefone());
        return link;
    }

    /** https://wa.me/55DDDNUMERO?text=... (o texto vai codificado para caber no link). */
    public String linkWhatsApp(Linha linha, String texto) {
        String numero = numeroWhatsApp(linha.contrato().getInquilino().getTelefone());
        if (numero == null) {
            throw new RegraNegocioException("O inquilino não tem celular com DDD no cadastro.");
        }
        String mensagem = texto == null || texto.isBlank() ? mensagem(linha).texto() : texto;
        return "https://wa.me/" + numero + "?text="
                + URLEncoder.encode(mensagem, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** "(79) 99999-0000" vira "5579999990000". Precisa de DDD: 10 ou 11 números (ou já com o 55). */
    static String numeroWhatsApp(String telefone) {
        if (telefone == null) {
            return null;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() == 10 || digitos.length() == 11) {
            return "55" + digitos;
        }
        if ((digitos.length() == 12 || digitos.length() == 13) && digitos.startsWith("55")) {
            return digitos;
        }
        return null;
    }

    /** "setembro/2026" ou "agosto/2026 e setembro/2026". */
    private static String meses(List<Aluguel> alugueis) {
        List<String> nomes = alugueis.stream()
                .map(a -> a.getCompetencia().getMonth().getDisplayName(java.time.format.TextStyle.FULL,
                        Locale.forLanguageTag("pt-BR")) + "/" + a.getCompetencia().getYear())
                .toList();
        if (nomes.size() == 1) {
            return nomes.get(0);
        }
        return String.join(", ", nomes.subList(0, nomes.size() - 1)) + " e " + nomes.get(nomes.size() - 1);
    }

    private static String primeiroNome(String nome) {
        String limpo = nome == null ? "" : nome.strip();
        int espaco = limpo.indexOf(' ');
        return espaco < 0 ? limpo : limpo.substring(0, espaco);
    }
}
