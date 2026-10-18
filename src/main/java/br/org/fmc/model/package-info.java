/**
 * Classes do domínio (o "mundo" da Fundação), conforme o diagrama de classes do MVP:
 * Usuario, Perfil, Acao, Pessoa, Inquilino, Fiador, Conjuge, Cpf, Imovel,
 * TipoImovel, SituacaoImovel, Contrato, PrazoContrato, StatusContrato,
 * Garantia, Fianca, Caucao, Aluguel, SituacaoAluguel, Encargos, Recibo,
 * FormaPagamento e RegrasNegocio.
 *
 * Regras de POO que aparecem aqui:
 * - Encapsulamento: atributos private, acesso por getters/setters que validam.
 * - Herança: Pessoa -> Inquilino/Fiador; Garantia -> Fianca/Caucao.
 * - Polimorfismo: métodos sobrescritos com @Override
 *   (ex.: Pessoa.rendaConsiderada(), Garantia.validar(), Garantia.getDescricao()).
 * - O tipo do imóvel (casa, apartamento...) é o enum TipoImovel, não uma subclasse.
 * - O pagamento fica registrado no próprio Recibo (não existe classe Pagamento).
 *
 * Estas classes NÃO acessam o banco nem as telas.
 */
package br.org.fmc.model;
