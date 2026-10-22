package br.org.fmc.ui;

import br.org.fmc.model.Cpf;
import br.org.fmc.util.Formatos;
import java.math.BigDecimal;
import java.util.function.UnaryOperator;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

/**
 * Máscaras de entrada (BOARD-165): o campo se formata sozinho enquanto a pessoa digita.
 * Ex.: digitar 52998224725 mostra 529.982.247-25.
 *
 * Uso no initialize() do controller:
 *
 * <pre>
 *     Mascara.cpf(campoCpf, dicaCpf);   // dicaCpf: Label embaixo do campo, diz se o CPF está certo
 *     Mascara.telefone(campoTelefone);
 *     Mascara.dinheiro(campoRenda);
 * </pre>
 *
 * A máscara só ajuda a digitar. Quem decide se o dado vale é sempre o service.
 */
public final class Mascara {

    private Mascara() {
    }

    /**
     * CPF: aceita só números (até 11) e coloca pontos e traço. Se a dica não for nula,
     * mostra embaixo do campo se o CPF está certo assim que os 11 números forem digitados.
     */
    public static void cpf(TextField campo, Label dica) {
        soDigitos(campo, 11, Mascara::formatarCpf);
        if (dica != null) {
            campo.textProperty().addListener((obs, antes, agora) -> avisarCpf(campo, dica, agora));
            avisarCpf(campo, dica, campo.getText());
        }
    }

    /** Telefone: (79) 99999-9999 para celular ou (79) 3333-4444 para fixo. */
    public static void telefone(TextField campo) {
        soDigitos(campo, 11, Mascara::formatarTelefone);
    }

    /**
     * Dinheiro: aceita números, ponto e vírgula; ao sair do campo, arruma para o padrão
     * brasileiro (ex.: 2500 vira 2.500,00).
     */
    public static void dinheiro(TextField campo) {
        campo.setTextFormatter(new TextFormatter<String>(
                mudanca -> mudanca.getControlNewText().matches("[0-9.,R$\\s]*") ? mudanca : null));
        campo.focusedProperty().addListener((obs, antes, focado) -> {
            if (!focado && !campo.getText().isBlank()) {
                try {
                    BigDecimal valor = Formatos.lerValor(campo.getText());
                    campo.setText(Formatos.numero(valor));
                } catch (IllegalArgumentException e) {
                    // Deixa como está: ao salvar, o service explica o que está errado.
                }
            }
        });
    }

    /** Aceita só números, até o máximo, e reescreve o campo inteiro já formatado. */
    private static void soDigitos(TextField campo, int maximo, UnaryOperator<String> formato) {
        campo.setTextFormatter(new TextFormatter<String>(mudanca -> {
            if (!mudanca.isContentChange()) {
                return mudanca;
            }
            String digitos = mudanca.getControlNewText().replaceAll("\\D", "");
            if (digitos.length() > maximo) {
                digitos = digitos.substring(0, maximo);
            }
            String novo = formato.apply(digitos);
            mudanca.setRange(0, mudanca.getControlText().length());
            mudanca.setText(novo);
            mudanca.selectRange(novo.length(), novo.length());
            return mudanca;
        }));
    }

    /** 52998224725 → 529.982.247-25 (vai formatando aos poucos, enquanto digita). */
    static String formatarCpf(String d) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            if (i == 3 || i == 6) {
                s.append('.');
            } else if (i == 9) {
                s.append('-');
            }
            s.append(d.charAt(i));
        }
        return s.toString();
    }

    /** 79998124410 → (79) 99812-4410; 7932114410 → (79) 3211-4410. */
    static String formatarTelefone(String d) {
        if (d.isEmpty()) {
            return "";
        }
        if (d.length() <= 2) {
            return "(" + d;
        }
        String ddd = d.substring(0, 2);
        String numero = d.substring(2);
        int corte = numero.length() == 9 ? 5 : 4; // celular tem 9 números depois do DDD
        if (numero.length() > corte) {
            numero = numero.substring(0, corte) + "-" + numero.substring(corte);
        }
        return "(" + ddd + ") " + numero;
    }

    private static void avisarCpf(TextField campo, Label dica, String texto) {
        String digitos = texto == null ? "" : texto.replaceAll("\\D", "");
        campo.getStyleClass().remove("campo-erro");
        if (digitos.isEmpty()) {
            dica.setText("");
            dica.getStyleClass().setAll("label", "dica");
        } else if (digitos.length() < 11) {
            dica.setText(digitos.length() + " de 11 números");
            dica.getStyleClass().setAll("label", "dica");
        } else if (Cpf.ehValido(digitos)) {
            dica.setText("CPF válido");
            dica.getStyleClass().setAll("label", "dica", "sucesso");
        } else {
            dica.setText(Cpf.problema(digitos));
            dica.getStyleClass().setAll("label", "dica", "erro");
            campo.getStyleClass().add("campo-erro");
        }
        boolean mostrar = !dica.getText().isEmpty();
        dica.setVisible(mostrar);
        dica.setManaged(mostrar);
    }
}
