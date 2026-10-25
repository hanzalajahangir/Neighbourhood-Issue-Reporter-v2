import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class UserManager {
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 30;
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 100;
    private static final int MAX_NAME_LENGTH = 60;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+$");

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    private static final String DEFAULT_ADMIN_NAME = "System Admin";

    private final DataStore store;
    private List<User> users = new ArrayList<>();

    public UserManager(DataStore store) {
        if (store == null) {
            throw new IllegalArgumentException("DataStore is required.");
        }
        this.store = store;
        this.users = store.loadUsers();
        if (seedDefaultAdminIfNeeded()) {
            store.saveUsers(users);
        }
    }

    // ── Seed the default admin when no admin exists ──────────
    private boolean seedDefaultAdminIfNeeded() {
        for (User user : users) {
            if ("Admin".equalsIgnoreCase(user.getRole())) {
                return false;
            }
        }

        String username = uniqueAdminUsername();
        users.add(new Admin(username, DEFAULT_ADMIN_PASSWORD, DEFAULT_ADMIN_NAME));
        return true;
    }

    private String uniqueAdminUsername() {
        String candidate = DEFAULT_ADMIN_USERNAME;
        int suffix = 2;
        while (findUser(candidate) != null) {
            candidate = DEFAULT_ADMIN_USERNAME + suffix;
            suffix++;
        }
        return candidate;
    }

    // ── Validate registration input ──────────────────────────
    public String validateRegistration(String username, String password, String name) {
        return validateAccountInput(username, password, name);
    }

    /**
     * Validates input for an admin account created from the Users dialog.
     * The public default password is never accepted for a new admin.
     */
    public String validateAdminCreation(String username, String password, String name) {
        String error = validateAccountInput(username, password, name);
        if (error != null) {
            return error;
        }
        if (DEFAULT_ADMIN_PASSWORD.equals(password)) {
            return "The default admin password cannot be used.";
        }
        return null;
    }

    private String validateAccountInput(String username, String password, String name) {
        String cleanUsername = username == null ? "" : username.trim();
        String cleanName = name == null ? "" : name.trim();

        if (cleanName.isEmpty()) {
            return "Full name is required.";
        }
        if (cleanName.length() > MAX_NAME_LENGTH) {
            return "Full name must be at most " + MAX_NAME_LENGTH + " characters.";
        }
        if (cleanUsername.isEmpty()) {
            return "Username is required.";
        }
        if (cleanUsername.length() < MIN_USERNAME_LENGTH
                || cleanUsername.length() > MAX_USERNAME_LENGTH) {
            return "Username must be " + MIN_USERNAME_LENGTH + "-"
                    + MAX_USERNAME_LENGTH + " characters.";
        }
        if (!USERNAME_PATTERN.matcher(cleanUsername).matches()) {
            return "Username may only contain letters, numbers, dots, underscores, and hyphens.";
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return "Password must be at most " + MAX_PASSWORD_LENGTH + " characters.";
        }
        if (findByUsername(cleanUsername) != null) {
            return "That username is already taken.";
        }
        return null;
    }

    // ── Register citizen ─────────────────────────────────────
    public boolean register(String username, String password, String name) {
        if (validateRegistration(username, password, name) != null) {
            return false;
        }

        Citizen citizen = new Citizen(username.trim(), password, name.trim());
        users.add(citizen);
        try {
            store.saveUsers(users);
        } catch (DataStoreException e) {
            users.remove(citizen);
            throw e;
        }
        return true;
    }

    // ── Create an admin from the Users dialog ────────────────
    public boolean createAdmin(String username, String password, String name) {
        if (validateAdminCreation(username, password, name) != null) {
            return false;
        }

        Admin admin = new Admin(username.trim(), password, name.trim());
        users.add(admin);
        try {
            store.saveUsers(users);
        } catch (DataStoreException e) {
            users.remove(admin);
            throw e;
        }
        return true;
    }

    // ── Remove an admin (with safety rules) ──────────────────
    public String validateAdminRemoval(String username, String requestingUsername) {
        User actor = findUser(requestingUsername);
        if (actor == null || !"Admin".equalsIgnoreCase(actor.getRole())) {
            return "Only admins can remove admin accounts.";
        }

        User target = findUser(username);
        if (target == null) {
            return "That admin account no longer exists.";
        }
        if (!"Admin".equalsIgnoreCase(target.getRole())) {
            return "Only admin accounts can be removed here.";
        }
        if (target.getUsername().equalsIgnoreCase(actor.getUsername())) {
            return "You cannot remove your own account.";
        }
        if (countAdmins() <= 1) {
            return "At least one admin account must remain.";
        }
        return null;
    }

    public boolean removeAdmin(String username, String requestingUsername) {
        if (validateAdminRemoval(username, requestingUsername) != null) {
            return false;
        }

        User target = findUser(username);
        int index = users.indexOf(target);
        users.remove(index);
        try {
            store.saveUsers(users);
        } catch (DataStoreException e) {
            users.add(index, target);
            throw e;
        }
        return true;
    }

    private int countAdmins() {
        int count = 0;
        for (User user : users) {
            if ("Admin".equalsIgnoreCase(user.getRole())) {
                count++;
            }
        }
        return count;
    }

    // ── Password changes ─────────────────────────────────────
    public String validatePasswordChange(User user, String currentPassword, String newPassword) {
        if (user == null || findUser(user.getUsername()) != user) {
            return "Your session is no longer valid. Please log in again.";
        }
        if (!user.checkPassword(currentPassword)) {
            return "Current password is incorrect.";
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            return "New password must be at least " + MIN_PASSWORD_LENGTH + " characters.";
        }
        if (newPassword.length() > MAX_PASSWORD_LENGTH) {
            return "New password must be at most " + MAX_PASSWORD_LENGTH + " characters.";
        }
        if (newPassword.equals(currentPassword)) {
            return "New password must be different from the current password.";
        }
        if ("Admin".equalsIgnoreCase(user.getRole())
                && DEFAULT_ADMIN_PASSWORD.equals(newPassword)) {
            return "The default admin password cannot be used.";
        }
        return null;
    }

    /**
     * Changes a user's password and saves immediately.
     *
     * @return {@code null} on success, or a human-readable validation error
     * @throws DataStoreException if the new credentials cannot be saved
     */
    public String changePassword(User user, String currentPassword, String newPassword) {
        String error = validatePasswordChange(user, currentPassword, newPassword);
        if (error != null) {
            return error;
        }

        String oldHash = user.getPasswordHash();
        String oldSalt = user.getSalt();
        int oldIterations = user.getIterations();
        user.updatePassword(newPassword);
        try {
            store.saveUsers(users);
        } catch (DataStoreException e) {
            user.restoreCredentials(oldHash, oldSalt, oldIterations);
            throw e;
        }
        return null;
    }

    /**
     * True when an admin still signs in with the seeded demo password. The UI
     * uses this right after login to offer an immediate password change.
     */
    public boolean isUsingDefaultAdminPassword(User user) {
        return user != null
                && "Admin".equalsIgnoreCase(user.getRole())
                && user.checkPassword(DEFAULT_ADMIN_PASSWORD);
    }

    // ── Login ────────────────────────────────────────────────
    public User login(String username, String password) {
        User u = findByUsername(username);
        if (u != null && u.checkPassword(password)) {
            return u;
        }
        return null;
    }

    // ── Find by username ─────────────────────────────────────
    public User findByUsername(String username) {
        return findUser(username);
    }

    private User findUser(String username) {
        for (User u : users) {
            if (u.getUsername().equalsIgnoreCase(username)) {
                return u;
            }
        }
        return null;
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(users);
    }

    // ── List all users ───────────────────────────────────────
    public void printAllUsers() {
        System.out.println("\n  Registered Users:");
        System.out.println("  ---------------------------------");
        for (User u : users) {
            System.out.printf("  %-15s %-10s %s%n", u.getUsername(), "[" + u.getRole() + "]", u.getName());
        }
        System.out.println();
    }
}
