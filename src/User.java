import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

public abstract class User {
    private static final int SALT_BYTES = 16;
    private static final int DEFAULT_ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    protected String username;
    protected String name;
    private String passwordHash;
    private String salt;
    private int iterations;

    public User(String username, String password, String name) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty.");
        }
        this.username = username;
        this.name = name;
        this.iterations = DEFAULT_ITERATIONS;
        this.salt = createSalt();
        this.passwordHash = hashPassword(password, this.salt, this.iterations);
    }

    /**
     * Restores a user from previously stored credentials.
     *
     * <p>No hashing happens here: the stored hash, salt, and iteration count are
     * used as-is so passwords are never re-hashed or stored as plaintext.</p>
     */
    protected User(String username, String name, String passwordHash, String salt, int iterations) {
        if (username == null || name == null || passwordHash == null || salt == null) {
            throw new IllegalArgumentException("Stored user credentials are incomplete.");
        }
        if (iterations <= 0) {
            throw new IllegalArgumentException("Iteration count must be positive.");
        }
        this.username = username;
        this.name = name;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.iterations = iterations;
    }

    public String getUsername() { return username; }
    public String getName() { return name; }

    String getPasswordHash() { return passwordHash; }
    String getSalt()         { return salt; }
    int getIterations()      { return iterations; }

    public boolean checkPassword(String password) {
        if (password == null) return false;
        String candidate = hashPassword(password, salt, iterations);
        return MessageDigest.isEqual(
                passwordHash.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    public abstract String getRole();

    private static String createSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private static String hashPassword(String password, String salt, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(
                chars,
                Base64.getDecoder().decode(salt),
                iterations,
                KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to hash password.", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }
}
