package br.org.fmc.ui;

import atlantafx.base.theme.PrimerLight;
import java.io.InputStream;
import javafx.application.Application;
import javafx.scene.text.Font;

/**
 * Visual do sistema (design system da Fundação).
 *
 * Duas camadas, como a base e o acabamento de uma parede:
 * 1. AtlantaFX (tema PrimerLight): deixa botões, campos e tabelas com cara de sistema moderno.
 * 2. css/tema.css: as cores, fontes e medidas da Fundação por cima (aplicado pelo Navegador).
 */
public final class Tema {

    private static final String[] FONTES = {
        "PlusJakartaSans-Regular", "PlusJakartaSans-Medium", "PlusJakartaSans-SemiBold", "PlusJakartaSans-Bold",
        "Urbanist-SemiBold", "Urbanist-Bold"
    };

    private Tema() {
    }

    /** Chamado uma vez pelo App, antes de abrir a primeira tela. */
    public static void aplicar() {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        for (String nome : FONTES) {
            try (InputStream arquivo = Tema.class.getResourceAsStream("/br/org/fmc/fonts/" + nome + ".ttf")) {
                if (arquivo != null) {
                    Font.loadFont(arquivo, 14);
                }
            } catch (Exception e) {
                // Sem a fonte, o sistema usa a Segoe UI (ver tema.css).
            }
        }
    }
}
