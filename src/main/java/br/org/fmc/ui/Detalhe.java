package br.org.fmc.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

/**
 * Peças das janelas de consulta no desenho do protótipo (detalhes do contrato e ficha do
 * inquilino): colunas com título em maiúsculas, linhas "rótulo à esquerda, valor à direita"
 * e caixas coloridas com uma frase. Os estilos são as classes detalhe-* e veredito-* do tema.css.
 */
final class Detalhe {

    static final String SEM_DADO = "—";

    private Detalhe() {
    }

    /** Três blocos lado a lado, com a mesma largura e um espaço entre eles. */
    static GridPane tresColunas(Node a, Node b, Node c) {
        GridPane grade = new GridPane();
        grade.setHgap(28);
        for (int n = 0; n < 3; n++) {
            ColumnConstraints largura = new ColumnConstraints();
            largura.setPercentWidth(100.0 / 3);
            largura.setHgrow(Priority.ALWAYS);
            grade.getColumnConstraints().add(largura);
        }
        grade.addRow(0, a, b, c);
        return grade;
    }

    /** Uma coluna: o título em maiúsculas e as linhas embaixo. */
    static VBox coluna(String nome, Node... linhas) {
        VBox coluna = new VBox(titulo(nome));
        coluna.getChildren().addAll(linhas);
        return coluna;
    }

    /** Título de seção em maiúsculas e cinza (ex.: "IMÓVEL", "SITUAÇÃO DO CONTRATO"). */
    static Label titulo(String nome) {
        Label titulo = new Label(nome.toUpperCase());
        titulo.getStyleClass().add("detalhe-secao");
        return titulo;
    }

    /** Uma linha: rótulo à esquerda, valor à direita, com uma linha fina embaixo. */
    static HBox linha(String rotulo, String valor) {
        Label r = new Label(rotulo);
        r.getStyleClass().add("detalhe-rotulo");
        r.setMinWidth(Region.USE_PREF_SIZE);
        Label v = new Label(valor);
        v.getStyleClass().add("detalhe-valor");
        v.setWrapText(true);
        v.setMinHeight(Region.USE_PREF_SIZE);
        v.setAlignment(Pos.TOP_RIGHT);
        v.setTextAlignment(TextAlignment.RIGHT);
        v.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(v, Priority.ALWAYS);
        HBox linha = new HBox(12, r, v);
        linha.setAlignment(Pos.TOP_LEFT);
        linha.getStyleClass().add("detalhe-linha");
        return linha;
    }

    /** Uma caixa colorida com uma frase: veredito-ok, veredito-alerta, veredito-erro ou veredito-neutro. */
    static HBox veredito(String tipo, String texto) {
        Label frase = new Label(texto);
        frase.setWrapText(true);
        frase.setMinHeight(Region.USE_PREF_SIZE);
        HBox caixa = new HBox(frase);
        caixa.getStyleClass().addAll("veredito", tipo);
        return caixa;
    }

    /** Texto vazio vira um travessão, para a linha não ficar em branco. */
    static String texto(String valor) {
        return valor == null || valor.isBlank() ? SEM_DADO : valor;
    }
}
