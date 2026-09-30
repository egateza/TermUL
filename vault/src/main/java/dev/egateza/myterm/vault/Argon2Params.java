package dev.egateza.myterm.vault;

import java.security.SecureRandom;
import java.util.Arrays;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Parameter Argon2id untuk menurunkan KEK dari master password.
 *
 * @param memoryKiB   memory cost dalam KiB
 * @param iterations  time cost
 * @param parallelism lanes
 */
public record Argon2Params(int memoryKiB, int iterations, int parallelism) {

    public static final int SALT_LENGTH = 16;

    public Argon2Params {
        if (memoryKiB < 8 || iterations < 1 || parallelism < 1) {
            throw new IllegalArgumentException("Parameter Argon2 tidak valid");
        }
        if (memoryKiB > 4 * 1024 * 1024 || iterations > 100 || parallelism > 64) {
            throw new IllegalArgumentException("Parameter Argon2 terlalu besar (file vault rusak?)");
        }
    }

    /** Default ADR 0002: m=64 MiB, t=3, p=1. */
    public static Argon2Params defaults() {
        return new Argon2Params(64 * 1024, 3, 1);
    }

    public static byte[] newSalt(SecureRandom random) {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }

    /** Menurunkan key 32 byte. {@code password} tidak diubah; buffer internal di-zero. */
    public byte[] deriveKey(char[] password, byte[] salt) {
        byte[] pwBytes = Secrets.toUtf8(password);
        try {
            var params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withMemoryAsKB(memoryKiB)
                    .withIterations(iterations)
                    .withParallelism(parallelism)
                    .withSalt(Arrays.copyOf(salt, salt.length))
                    .build();
            var gen = new Argon2BytesGenerator();
            gen.init(params);
            byte[] key = new byte[32];
            gen.generateBytes(pwBytes, key);
            return key;
        } finally {
            Secrets.zero(pwBytes);
        }
    }
}
