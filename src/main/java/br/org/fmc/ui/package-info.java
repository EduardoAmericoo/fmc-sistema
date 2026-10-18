/**
 * Controllers das telas (JavaFX). Cada tela tem um arquivo .fxml em
 * resources/br/org/fmc/ui/ e um controller aqui.
 *
 * Os controllers só cuidam da tela: leem os campos, chamam os services e
 * mostram o resultado. Regra de negócio fica em service.
 *
 * Botão que só alguns perfis podem usar (ex.: "Novo imóvel", "Receber"):
 * no initialize(), chame Permissoes.mostrarSe(botao, Acao.X). O menu já
 * esconde sozinho as telas que o perfil não pode abrir (ver model/Tela).
 */
package br.org.fmc.ui;
