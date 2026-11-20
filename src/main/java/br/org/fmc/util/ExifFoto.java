package br.org.fmc.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Lê duas informações que o celular grava dentro da foto JPEG (o "EXIF"):
 * - a orientação: o celular grava a foto deitada e anota "gire 90°"; sem ler isso, a foto
 *   aparece de lado;
 * - a data e a hora em que a foto foi tirada (vira prova na vistoria).
 *
 * Só lê o pedaço necessário do arquivo, sem biblioteca extra. Se a foto não tiver EXIF (PNG,
 * captura de tela, foto enviada pelo WhatsApp), devolve orientação normal e data vazia.
 */
public final class ExifFoto {

    /** O que interessa do EXIF. orientacao: 1 = normal, 3 = de cabeça para baixo, 6 = girar 90°, 8 = girar 270°. */
    public record Dados(int orientacao, LocalDateTime tiradaEm) {
        public static final Dados VAZIO = new Dados(1, null);

        /** Quantos graus girar (no sentido do relógio) para a foto ficar em pé. */
        public int grausParaGirar() {
            return switch (orientacao) {
                case 3 -> 180;
                case 6 -> 90;
                case 8 -> 270;
                default -> 0;
            };
        }
    }

    private static final int ORIENTACAO = 0x0112;
    private static final int DATA_HORA = 0x0132;
    private static final int PONTEIRO_EXIF = 0x8769;
    private static final int DATA_ORIGINAL = 0x9003;
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    private ExifFoto() {
    }

    public static Dados ler(Path foto) {
        try {
            byte[] bytes = Files.readAllBytes(foto);
            return ler(bytes);
        } catch (IOException | RuntimeException e) {
            return Dados.VAZIO; // EXIF estranho não impede a foto de entrar
        }
    }

    static Dados ler(byte[] b) {
        if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
            return Dados.VAZIO; // não é JPEG
        }
        int i = 2;
        while (i + 4 <= b.length && (b[i] & 0xFF) == 0xFF) {
            int marcador = b[i + 1] & 0xFF;
            int tamanho = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
            if (marcador == 0xE1 && tamanho > 8 && i + 4 + 6 <= b.length
                    && new String(b, i + 4, 4, StandardCharsets.US_ASCII).equals("Exif")) {
                return lerTiff(b, i + 10, Math.min(b.length, i + 2 + tamanho));
            }
            if (marcador == 0xDA) {
                break; // começou a imagem: não há mais cabeçalhos
            }
            i += 2 + tamanho;
        }
        return Dados.VAZIO;
    }

    private static Dados lerTiff(byte[] b, int inicio, int fim) {
        ByteBuffer t = ByteBuffer.wrap(b, 0, fim);
        t.order(b[inicio] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        int ifd0 = inicio + t.getInt(inicio + 4);
        int orientacao = 1;
        LocalDateTime data = null;
        LocalDateTime dataOriginal = null;
        int exif = -1;
        int n = t.getShort(ifd0) & 0xFFFF;
        for (int k = 0; k < n; k++) {
            int e = ifd0 + 2 + k * 12;
            int tag = t.getShort(e) & 0xFFFF;
            if (tag == ORIENTACAO) {
                orientacao = t.getShort(e + 8) & 0xFFFF;
            } else if (tag == DATA_HORA) {
                data = texto(b, inicio + t.getInt(e + 8));
            } else if (tag == PONTEIRO_EXIF) {
                exif = inicio + t.getInt(e + 8);
            }
        }
        if (exif > 0 && exif + 2 <= fim) {
            int m = t.getShort(exif) & 0xFFFF;
            for (int k = 0; k < m; k++) {
                int e = exif + 2 + k * 12;
                if ((t.getShort(e) & 0xFFFF) == DATA_ORIGINAL) {
                    dataOriginal = texto(b, inicio + t.getInt(e + 8));
                }
            }
        }
        return new Dados(orientacao, dataOriginal != null ? dataOriginal : data);
    }

    private static LocalDateTime texto(byte[] b, int posicao) {
        if (posicao < 0 || posicao + 19 > b.length) {
            return null;
        }
        try {
            return LocalDateTime.parse(new String(b, posicao, 19, StandardCharsets.US_ASCII), FORMATO);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
