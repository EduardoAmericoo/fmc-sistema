package br.org.fmc.util;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Transforma a senha num "hash": um código que não dá para desfazer.
 * O banco guarda só o hash; no login, geramos o hash da senha digitada e comparamos.
 *
 * Usa PBKDF2 com um "sal" aleatório por senha: duas pessoas com a mesma senha
 * ficam com hashes diferentes, e descobrir a senha por tentativa fica muito lento.
 * Formato guardado: pbkdf2$iteracoes$sal$hash
 */
public final class SenhaUtil {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final int ITERACOES = 120_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private SenhaUtil() {
    }

    public static String gerarHash(String senha) {
        if (senha == null || senha.isEmpty()) {
            throw new IllegalArgumentException("A senha não pode ser vazia.");
        }
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] hash = calcular(senha, sal, ITERACOES);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2$" + ITERACOES + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(hash);
    }

    /** Letras e números sem os que se confundem ao ler (0 e O, 1, l e I). */
    private static final String MAIUSCULAS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijkmnpqrstuvwxyz";
    private static final String NUMEROS = "23456789";

    /**
     * Senha provisória aleatória (BOARD-62), fácil de ler e ditar, no formato
     * "Abc4-dEf7": 8 letras e números, com maiúscula, minúscula e número (já é uma senha forte).
     */
    public static String gerarProvisoria() {
        String todos = MAIUSCULAS + MINUSCULAS + NUMEROS;
        char[] c = new char[8];
        for (int i = 0; i < c.length; i++) {
            c[i] = todos.charAt(ALEATORIO.nextInt(todos.length()));
        }
        // Garante um de cada tipo, em posições sorteadas e diferentes.
        int[] posicoes = {0, 1, 2, 3, 4, 5, 6, 7};
        for (int i = posicoes.length - 1; i > 0; i--) {
            int j = ALEATORIO.nextInt(i + 1);
            int t = posicoes[i];
            posicoes[i] = posicoes[j];
            posicoes[j] = t;
        }
        c[posicoes[0]] = MAIUSCULAS.charAt(ALEATORIO.nextInt(MAIUSCULAS.length()));
        c[posicoes[1]] = MINUSCULAS.charAt(ALEATORIO.nextInt(MINUSCULAS.length()));
        c[posicoes[2]] = NUMEROS.charAt(ALEATORIO.nextInt(NUMEROS.length()));
        String s = new String(c);
        return s.substring(0, 4) + "-" + s.substring(4);
    }

    /** A senha digitada corresponde ao hash guardado? */
    public static boolean confere(String senha, String hashGuardado) {
        if (senha == null || hashGuardado == null) {
            return false;
        }
        String[] partes = hashGuardado.split("\\$");
        if (partes.length != 4 || !partes[0].equals("pbkdf2")) {
            return false;
        }
        try {
            int iteracoes = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            // Comparação em tempo constante: não dá pistas de quantos caracteres acertou.
            return MessageDigest.isEqual(esperado, calcular(senha, sal, iteracoes));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] calcular(String senha, byte[] sal, int iteracoes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), sal, iteracoes, BITS_HASH);
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível calcular o hash da senha.", e);
        }
    }
}
