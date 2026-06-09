package studio.flow.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {
  private static final int ITERATIONS = 120_000;
  private static final int SALT_BYTES = 16;
  private static final int HASH_BITS = 256;
  private final SecureRandom secureRandom = new SecureRandom();

  public String hash(String password) throws Exception {
    byte[] salt = new byte[SALT_BYTES];
    secureRandom.nextBytes(salt);
    byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS);
    return ITERATIONS
        + ":"
        + Base64.getEncoder().encodeToString(salt)
        + ":"
        + Base64.getEncoder().encodeToString(hash);
  }

  public boolean verify(String password, String stored) throws Exception {
    if (password == null || stored == null || stored.isBlank()) {
      return false;
    }

    String[] parts = stored.split(":");
    if (parts.length != 3) {
      return false;
    }

    int iterations = Integer.parseInt(parts[0]);
    byte[] salt = Base64.getDecoder().decode(parts[1]);
    byte[] expected = Base64.getDecoder().decode(parts[2]);
    byte[] actual = pbkdf2(password.toCharArray(), salt, iterations);
    return MessageDigest.isEqual(expected, actual);
  }

  private byte[] pbkdf2(char[] password, byte[] salt, int iterations) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    return factory.generateSecret(spec).getEncoded();
  }
}
