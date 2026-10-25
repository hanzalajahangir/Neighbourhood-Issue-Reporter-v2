import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * File-based persistence for users and issues.
 *
 * Layout:
 *   data/users.csv
 *   data/issues.csv
 *   data/meta.properties
 *
 * Saves are written to a temporary file, the previous version is copied to
 * {@code <name>.bak}, and the temporary file is atomically moved into place.
 */
public final class DataStore {
    private static final String USERS_FILE = "users.csv";
    private static final String ISSUES_FILE = "issues.csv";
    private static final String META_FILE = "meta.properties";
    private static final String BACKUP_SUFFIX = ".bak";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_ITERATIONS = 1_000_000;

    private static final List<String> USER_HEADER = Arrays.asList(
            "role", "username", "name", "passwordHash", "salt", "iterations");
    private static final List<String> ISSUE_HEADER = Arrays.asList(
            "id", "title", "description", "location", "category", "priority",
            "status", "department", "reportedBy", "dateReported");

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter CORRUPT_SUFFIX_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path dataDir;
    private final List<String> warnings = new ArrayList<>();
    private boolean issuesReset;

    public DataStore(Path dataDir) {
        if (dataDir == null) {
            throw new IllegalArgumentException("Data directory is required.");
        }
        this.dataDir = dataDir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.dataDir);
        } catch (IOException e) {
            throw new DataStoreException(
                    "Could not create data directory " + this.dataDir + ": " + e.getMessage(), e);
        }
    }

    public Path getDataDir() {
        return dataDir;
    }

    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    // ── Load users ───────────────────────────────────────────
    public List<User> loadUsers() {
        Path file = dataDir.resolve(USERS_FILE);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }

        try {
            List<List<String>> records = Csv.parse(readFile(file));
            if (records.isEmpty()) {
                throw new IllegalArgumentException("The file is empty.");
            }
            if (!USER_HEADER.equals(records.get(0))) {
                throw new IllegalArgumentException("Unexpected header row.");
            }

            List<User> users = new ArrayList<>();
            Set<String> seenUsernames = new HashSet<>();
            for (int i = 1; i < records.size(); i++) {
                List<String> row = records.get(i);
                int line = i + 1;
                if (row.size() != USER_HEADER.size()) {
                    throw new IllegalArgumentException("Row " + line + " has "
                            + row.size() + " columns; expected " + USER_HEADER.size() + ".");
                }

                String role = row.get(0);
                String username = row.get(1);
                String name = row.get(2);
                String passwordHash = row.get(3);
                String salt = row.get(4);
                int iterations = validateUserRow(
                        line, role, username, name, passwordHash, salt, row.get(5));

                if (!seenUsernames.add(username.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException(
                            "Duplicate username \"" + username + "\" on row " + line + ".");
                }

                User user;
                if ("Admin".equalsIgnoreCase(role)) {
                    user = new Admin(username, name, passwordHash, salt, iterations);
                } else {
                    user = new Citizen(username, name, passwordHash, salt, iterations);
                }
                users.add(user);
            }
            return users;
        } catch (IOException | RuntimeException e) {
            handleCorrupt(file, "users", e);
            return new ArrayList<>();
        }
    }

    private int validateUserRow(int line, String role, String username, String name,
                                String passwordHash, String salt, String iterationsText) {
        if (!"Admin".equalsIgnoreCase(role) && !"Citizen".equalsIgnoreCase(role)) {
            throw new IllegalArgumentException("Unknown role on row " + line + ".");
        }
        if (username.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty username on row " + line + ".");
        }
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty name on row " + line + ".");
        }
        if (passwordHash.trim().isEmpty() || salt.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing credentials on row " + line + ".");
        }
        try {
            Base64.getDecoder().decode(passwordHash);
            Base64.getDecoder().decode(salt);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 credentials on row " + line + ".");
        }

        int iterations;
        try {
            iterations = Integer.parseInt(iterationsText.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid iteration count on row " + line + ".");
        }
        if (iterations <= 0 || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("Iteration count out of range on row " + line + ".");
        }
        return iterations;
    }

    // ── Load issues ──────────────────────────────────────────
    public List<Issue> loadIssues() {
        issuesReset = false;
        Path file = dataDir.resolve(ISSUES_FILE);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }

        try {
            List<List<String>> records = Csv.parse(readFile(file));
            if (records.isEmpty()) {
                throw new IllegalArgumentException("The file is empty.");
            }
            if (!ISSUE_HEADER.equals(records.get(0))) {
                throw new IllegalArgumentException("Unexpected header row.");
            }

            List<Issue> issues = new ArrayList<>();
            Set<Integer> seenIds = new HashSet<>();
            for (int i = 1; i < records.size(); i++) {
                List<String> row = records.get(i);
                int line = i + 1;
                if (row.size() != ISSUE_HEADER.size()) {
                    throw new IllegalArgumentException("Row " + line + " has "
                            + row.size() + " columns; expected " + ISSUE_HEADER.size() + ".");
                }

                int id = parsePositiveInt(row.get(0), line, "id");
                if (!seenIds.add(id)) {
                    throw new IllegalArgumentException("Duplicate issue id " + id + " on row " + line + ".");
                }

                String title = row.get(1);
                String description = row.get(2);
                String location = row.get(3);
                String category = row.get(4);
                String priority = row.get(5);
                String status = row.get(6);
                String department = row.get(7);
                String reportedBy = row.get(8);
                String dateReported = row.get(9);

                validateIssueRow(line, title, description, location, category,
                        priority, status, department, reportedBy, dateReported);

                issues.add(new Issue(id, title, description, location, category,
                        priority, status, department, reportedBy, dateReported));
            }
            return issues;
        } catch (IOException | RuntimeException e) {
            issuesReset = true;
            handleCorrupt(file, "issues", e);
            return new ArrayList<>();
        }
    }

    private void validateIssueRow(int line, String title, String description, String location,
                                  String category, String priority, String status,
                                  String department, String reportedBy, String dateReported) {
        if (title.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty title on row " + line + ".");
        }
        if (description.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty description on row " + line + ".");
        }
        if (location.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty location on row " + line + ".");
        }
        if (reportedBy.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty reporter on row " + line + ".");
        }
        if (!isOneOf(category, Issue.CATEGORIES)) {
            throw new IllegalArgumentException("Invalid category on row " + line + ".");
        }
        if (!isOneOf(priority, Issue.PRIORITIES)) {
            throw new IllegalArgumentException("Invalid priority on row " + line + ".");
        }
        if (!isOneOf(status, Issue.STATUSES)) {
            throw new IllegalArgumentException("Invalid status on row " + line + ".");
        }
        if (!"Unassigned".equals(department) && !isOneOf(department, Issue.DEPARTMENTS)) {
            throw new IllegalArgumentException("Invalid department on row " + line + ".");
        }
        try {
            LocalDateTime.parse(dateReported, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date on row " + line + ".");
        }
    }

    private int parsePositiveInt(String value, int line, String column) {
        int number;
        try {
            number = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + column + " on row " + line + ".");
        }
        if (number <= 0) {
            throw new IllegalArgumentException("Invalid " + column + " on row " + line + ".");
        }
        return number;
    }

    private boolean isOneOf(String value, String[] options) {
        for (String option : options) {
            if (option.equals(value)) {
                return true;
            }
        }
        return false;
    }

    // ── Load next issue id ───────────────────────────────────
    public int loadNextIssueId(List<Issue> issues) {
        if (issuesReset) {
            return 1;
        }

        int derived = 1;
        for (Issue issue : issues) {
            if (issue.getId() >= derived) {
                derived = issue.getId() + 1;
            }
        }

        int stored = 1;
        Path file = dataDir.resolve(META_FILE);
        if (Files.exists(file)) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);

                String version = properties.getProperty("formatVersion");
                if (version != null && !String.valueOf(FORMAT_VERSION).equals(version.trim())) {
                    warnings.add("meta.properties has format version " + version
                            + "; expected " + FORMAT_VERSION
                            + ". Issue IDs were derived from the existing issues.");
                }

                String next = properties.getProperty("nextIssueId");
                if (next != null) {
                    try {
                        stored = Integer.parseInt(next.trim());
                    } catch (NumberFormatException e) {
                        warnings.add("meta.properties has an invalid nextIssueId; "
                                + "issue IDs were derived from the existing issues.");
                    }
                }
            } catch (IOException | IllegalArgumentException e) {
                warnings.add("Could not read meta.properties; "
                        + "issue IDs were derived from the existing issues.");
            }
        }

        return Math.max(Math.max(stored, 1), derived);
    }

    // ── Save users ───────────────────────────────────────────
    public void saveUsers(List<User> users) {
        List<List<String>> records = new ArrayList<>();
        records.add(USER_HEADER);
        for (User user : users) {
            records.add(Arrays.asList(
                    user.getRole(),
                    user.getUsername(),
                    user.getName(),
                    user.getPasswordHash(),
                    user.getSalt(),
                    String.valueOf(user.getIterations())));
        }
        writeAtomically(dataDir.resolve(USERS_FILE), Csv.format(records));
    }

    // ── Save issues ──────────────────────────────────────────
    public void saveIssues(List<Issue> issues) {
        List<List<String>> records = new ArrayList<>();
        records.add(ISSUE_HEADER);
        for (Issue issue : issues) {
            records.add(Arrays.asList(
                    String.valueOf(issue.getId()),
                    issue.getTitle(),
                    issue.getDescription(),
                    issue.getLocation(),
                    issue.getCategory(),
                    issue.getPriority(),
                    issue.getStatus(),
                    issue.getDepartment(),
                    issue.getReportedBy(),
                    issue.getDateReported()));
        }
        writeAtomically(dataDir.resolve(ISSUES_FILE), Csv.format(records));
    }

    // ── Save metadata ────────────────────────────────────────
    public void saveNextIssueId(int nextIssueId) {
        String content = "formatVersion=" + FORMAT_VERSION + "\n"
                + "nextIssueId=" + nextIssueId + "\n";
        writeAtomically(dataDir.resolve(META_FILE), content);
    }

    // ── Internals ────────────────────────────────────────────
    private String readFile(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return text;
    }

    private void writeAtomically(Path target, String content) {
        Path directory = target.getParent();
        Path temp = null;
        try {
            Files.createDirectories(directory);
            temp = Files.createTempFile(
                    directory, target.getFileName().toString() + "-", ".tmp");
            Files.write(temp, content.getBytes(StandardCharsets.UTF_8));

            if (Files.exists(target)) {
                Path backup = directory.resolve(target.getFileName() + BACKUP_SUFFIX);
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                Files.move(temp, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new DataStoreException(
                    "Could not save " + target.getFileName() + ": " + e.getMessage(), e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Best-effort cleanup of the temporary file.
                }
            }
        }
    }

    private void handleCorrupt(Path file, String kind, Exception cause) {
        if (ISSUES_FILE.equals(file.getFileName().toString())) {
            issuesReset = true;
        }

        Path backup = uniqueCorruptPath(file);
        try {
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            warnings.add("The " + kind + " data file could not be read ("
                    + shortMessage(cause) + "). It was backed up as "
                    + backup.getFileName() + " and the app started with fresh "
                    + kind + " data.");
        } catch (IOException e) {
            warnings.add("The " + kind + " data file could not be read ("
                    + shortMessage(cause) + ") and could not be backed up: "
                    + e.getMessage() + ". The app started with fresh " + kind + " data.");
        }
    }

    private Path uniqueCorruptPath(Path file) {
        String base = file.getFileName().toString() + ".corrupt-"
                + LocalDateTime.now().format(CORRUPT_SUFFIX_FORMAT);
        Path parent = file.getParent();
        Path candidate = parent.resolve(base);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(base + "-" + suffix);
            suffix++;
        }
        return candidate;
    }

    private String shortMessage(Exception cause) {
        if (cause.getMessage() == null || cause.getMessage().trim().isEmpty()) {
            return cause.getClass().getSimpleName();
        }
        return cause.getMessage();
    }
}
