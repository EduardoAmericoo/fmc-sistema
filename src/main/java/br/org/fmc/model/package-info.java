/**
 * Classes do domínio (o "mundo" da Fundação): Pessoa, Inquilino, Fiador,
 * Imovel, Casa, Apartamento, Contrato, Garantia, GarantiaFiador, Caucao,
 * Cobranca, Pagamento, Recibo, Usuario, Perfil.
 *
 * Regras de POO que aparecem aqui:
 * - Encapsulamento: atributos private, acesso por getters/setters que validam.
 * - Herança: Pessoa -> Inquilino/Fiador; Imovel -> Casa/Apartamento;
 *   Garantia -> GarantiaFiador/Caucao.
 * - Polimorfismo: métodos sobrescritos com @Override (ex.: Garantia.validar()).
 *
 * Estas classes NÃO acessam o banco nem as telas.
 */
package br.org.fmc.model;
