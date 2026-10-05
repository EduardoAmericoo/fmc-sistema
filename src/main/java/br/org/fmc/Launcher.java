package br.org.fmc;

/**
 * Atalho para rodar o sistema pelo botão "Run" do VS Code.
 *
 * O Java exige que a classe principal de um app JavaFX não estenda Application
 * quando o projeto roda sem módulos; por isso esta classe só repassa para App.
 */
public class Launcher {

    public static void main(String[] args) {
        App.main(args);
    }
}
