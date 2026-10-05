/**
 * Acesso ao banco de dados: salvar, buscar, listar e atualizar.
 * Um repositório por entidade (ImovelRepository, InquilinoRepository...).
 *
 * Parecido com os models/managers do Django, mas escrito com SQL via JDBC.
 * Use sempre PreparedStatement (com ?) para evitar SQL injection.
 */
package br.org.fmc.repository;
