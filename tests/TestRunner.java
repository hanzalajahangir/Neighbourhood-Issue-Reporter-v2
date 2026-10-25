import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class TestRunner {
    private static int passed = 0;
    private static int failed = 0;
    private static final List<Path> tempDirs = new ArrayList<>();

    public static void main(String[] args) {
        run("seeded admin can log in", TestRunner::testSeededAdminLogin);
        run("registration and duplicate handling", TestRunner::testRegistration);
        run("registration validation", TestRunner::testRegistrationValidation);
        run("issue create/read/update/delete", TestRunner::testIssueCrud);
        run("issue IDs are store-owned and never reused", TestRunner::testIssueIds);
        run("getAllIssues returns a defensive copy", TestRunner::testDefensiveCopy);
        run("issue validation", TestRunner::testIssueValidation);
        run("duplicate active issues are rejected", TestRunner::testDuplicateIssues);

        run("persistence: users survive restart", TestRunner::testPersistenceUsers);
        run("persistence: issues survive restart", TestRunner::testPersistenceIssues);
        run("persistence: issue ids continue after restart", TestRunner::testPersistenceNextId);
        run("persistence: next id rebuilt without meta", TestRunner::testNextIdRebuiltWithoutMeta);
        run("persistence: CSV escaping round-trip", TestRunner::testPersistenceCsvEscaping);
        run("persistence: missing files seed defaults", TestRunner::testMissingDataFiles);
        run("persistence: corrupt users file is backed up", TestRunner::testCorruptUsersFile);
        run("persistence: corrupt issues file is backed up", TestRunner::testCorruptIssuesFile);
        run("persistence: .bak created on second save", TestRunner::testBackupCreatedOnSecondSave);
        run("persistence: admin seeded when no admin exists", TestRunner::testAdminSeededWhenMissing);
        run("persistence: passwords are hashed on disk", TestRunner::testPasswordsAreHashedOnDisk);
        run("persistence: failed user save rolls back", TestRunner::testSaveFailureRollsBack);
        run("persistence: failed issue save rolls back", TestRunner::testIssueSaveFailureRollsBack);
        run("persistence: persisted id counter is never rolled back", TestRunner::testIssueSaveFailureKeepsIdReserved);

        run("filter: text search", TestRunner::testFilterTextSearch);
        run("filter: dropdown fields and combinations", TestRunner::testFilterDropdowns);
        run("filter: time ranges", TestRunner::testFilterTimeRanges);
        run("account: password change rules and persistence", TestRunner::testPasswordChange);
        run("account: stale user objects cannot change passwords", TestRunner::testStaleUserCannotChangePassword);
        run("account: default admin password detection", TestRunner::testDefaultAdminPasswordDetection);
        run("account: admin creation and persistence", TestRunner::testAdminCreation);
        run("account: admin removal rules and persistence", TestRunner::testAdminRemoval);
        run("account: failed password change rolls back", TestRunner::testPasswordChangeSaveFailureRollsBack);
        run("account: failed admin creation rolls back", TestRunner::testAdminCreationSaveFailureRollsBack);
        run("account: failed admin removal rolls back", TestRunner::testAdminRemovalSaveFailureRollsBack);

        cleanupTempDirs();

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ── Core behaviour ───────────────────────────────────────
    private static void testSeededAdminLogin() {
        UserManager users = new UserManager(tempStore());

        User admin = users.login("admin", "admin123");
        check(admin != null, "default admin should log in");
        check("Admin".equals(admin.getRole()), "default user should have Admin role");
        check(users.login("admin", "wrong-password") == null, "wrong password should fail");
        check(users.login("ADMIN", "admin123") != null, "username lookup should be case-insensitive");
    }

    private static void testRegistration() {
        UserManager users = new UserManager(tempStore());

        check(users.register("citizen1", "secret1", "Citizen One"), "registration should succeed");
        User citizen = users.login("citizen1", "secret1");
        check(citizen != null, "registered citizen should log in");
        check("Citizen".equals(citizen.getRole()), "registered user should have Citizen role");
        check("Citizen One".equals(citizen.getName()), "registered name should be stored");
        check(!users.register("CITIZEN1", "secret2", "Other"), "duplicate username should be rejected");
    }

    private static void testRegistrationValidation() {
        UserManager users = new UserManager(tempStore());

        check(users.validateRegistration("ab", "secret1", "Name") != null,
                "short username should be rejected");
        check(users.validateRegistration("bad name", "secret1", "Name") != null,
                "username with spaces should be rejected");
        check(users.validateRegistration("citizen2", "12345", "Name") != null,
                "short password should be rejected");
        check(users.validateRegistration("citizen2", "secret1", "") != null,
                "empty full name should be rejected");
        check(users.validateRegistration("citizen2", "secret1", repeat('x', 61)) != null,
                "overlong full name should be rejected");
        check(users.validateRegistration("citizen2", "secret1", "Valid Name") == null,
                "valid registration should pass");
    }

    private static void testIssueCrud() {
        IssueManager issues = new IssueManager(tempStore());

        Issue first = issue("Pothole", "Deep pothole", "Main St", "Road", "High", "citizen1");
        Issue second = issue("Leak", "Water leak", "Elm St", "Water", "Medium", "citizen1");

        check(issues.addIssue(first), "first issue should be added");
        check(issues.addIssue(second), "second issue should be added");
        check(issues.getById(first.getId()) == first, "getById should find the issue");
        check(issues.getByUser("CITIZEN1").size() == 2, "getByUser should be case-insensitive");

        check(issues.updateStatus(first.getId(), "In Progress"), "status update should succeed");
        check("In Progress".equals(issues.getById(first.getId()).getStatus()),
                "status should be updated");

        check(issues.assignDepartment(first.getId(), "Roads Dept"), "department update should succeed");
        check("Roads Dept".equals(issues.getById(first.getId()).getDepartment()),
                "department should be updated");

        check(issues.updatePriority(first.getId(), "Critical"), "priority update should succeed");
        check("Critical".equals(issues.getById(first.getId()).getPriority()),
                "priority should be updated");

        check(issues.deleteIssue(second.getId()), "delete should succeed");
        check(issues.getById(second.getId()) == null, "deleted issue should be gone");
        check(!issues.deleteIssue(second.getId()), "deleting a missing issue should fail");
    }

    private static void testIssueIds() {
        IssueManager issues = new IssueManager(tempStore());

        Issue first = issue("One", "Description", "Location", "Other", "Low", "citizen1");
        Issue second = issue("Two", "Description", "Location", "Other", "Low", "citizen1");

        check(issues.addIssue(first), "first issue should be added");
        check(first.getId() == 1, "first issue should get id 1");
        check(issues.addIssue(second), "second issue should be added");
        check(second.getId() == 2, "second issue should get id 2");

        check(issues.deleteIssue(first.getId()), "first issue should be deleted");
        Issue third = issue("Three", "Description", "Location", "Other", "Low", "citizen1");
        check(issues.addIssue(third), "third issue should be added");
        check(third.getId() == 3, "ids should not be reused after deletion");
    }

    private static void testDefensiveCopy() {
        IssueManager issues = new IssueManager(tempStore());
        check(issues.addIssue(issue("One", "Description", "Location", "Other", "Low", "citizen1")),
                "issue should be added");

        List<Issue> snapshot = issues.getAllIssues();
        snapshot.clear();
        check(issues.getAllIssues().size() == 1, "mutating the returned list should not affect the store");
    }

    private static void testIssueValidation() {
        IssueManager issues = new IssueManager(tempStore());

        check(issues.validateIssue("", "Description", "Location", "citizen1") != null,
                "empty title should be rejected");
        check(issues.validateIssue("Title", "", "Location", "citizen1") != null,
                "empty description should be rejected");
        check(issues.validateIssue("Title", "Description", "", "citizen1") != null,
                "empty location should be rejected");
        check(issues.validateIssue(repeat('x', 101), "Description", "Location", "citizen1") != null,
                "overlong title should be rejected");
        check(issues.validateIssue("Title", repeat('x', 1001), "Location", "citizen1") != null,
                "overlong description should be rejected");
        check(issues.validateIssue("Title", "Description", repeat('x', 151), "citizen1") != null,
                "overlong location should be rejected");
        check(issues.validateIssue("Title", "Description", "Location", "citizen1") == null,
                "valid issue should pass");

        Issue invalid = issue("", "Description", "Location", "Other", "Low", "citizen1");
        check(!issues.addIssue(invalid), "addIssue should reject invalid input");
    }

    private static void testDuplicateIssues() {
        IssueManager issues = new IssueManager(tempStore());
        Issue original = issue("Leak", "Water leak", "Elm St", "Water", "High", "citizen1");

        check(issues.addIssue(original), "original issue should be added");

        Issue duplicate = issue("Leak", "Water leak again", "Elm St", "Water", "Low", "citizen1");
        check(!issues.addIssue(duplicate), "active duplicate should be rejected");

        Issue otherUser = issue("Leak", "Water leak", "Elm St", "Water", "Low", "citizen2");
        check(issues.addIssue(otherUser), "another citizen may report the same issue");

        check(issues.updateStatus(original.getId(), "Resolved"), "original issue should resolve");
        Issue afterResolution = issue("Leak", "Water leak again", "Elm St", "Water", "Low", "citizen1");
        check(issues.addIssue(afterResolution), "duplicate should be allowed after resolution");
    }

    // ── Persistence ──────────────────────────────────────────
    private static void testPersistenceUsers() {
        DataStore store = tempStore();
        UserManager first = new UserManager(store);
        check(first.register("citizen1", "secret1", "Citizen One"), "registration should succeed");

        UserManager second = new UserManager(store);
        check(second.login("citizen1", "secret1") != null, "citizen should survive a restart");
        check(second.login("admin", "admin123") != null, "seeded admin should survive a restart");
        check(second.login("citizen1", "wrong") == null, "stored password should still be verified");
        check(second.getAllUsers().size() == 2, "users should not be duplicated on reload");
    }

    private static void testPersistenceIssues() {
        DataStore store = tempStore();
        IssueManager first = new IssueManager(store);
        Issue original = issue("Pothole", "Deep pothole", "Main St", "Road", "High", "citizen1");

        check(first.addIssue(original), "issue should be added");
        check(first.updateStatus(original.getId(), "In Progress"), "status should update");
        check(first.assignDepartment(original.getId(), "Roads Dept"), "department should update");
        check(first.updatePriority(original.getId(), "Critical"), "priority should update");

        IssueManager second = new IssueManager(store);
        Issue loaded = second.getById(original.getId());
        check(loaded != null, "issue should survive a restart");
        check("Pothole".equals(loaded.getTitle()), "title should survive");
        check("Deep pothole".equals(loaded.getDescription()), "description should survive");
        check("Main St".equals(loaded.getLocation()), "location should survive");
        check("Road".equals(loaded.getCategory()), "category should survive");
        check("Critical".equals(loaded.getPriority()), "priority should survive");
        check("In Progress".equals(loaded.getStatus()), "status should survive");
        check("Roads Dept".equals(loaded.getDepartment()), "department should survive");
        check("citizen1".equals(loaded.getReportedBy()), "reporter should survive");
        check(original.getDateReported().equals(loaded.getDateReported()), "date should survive");
    }

    private static void testPersistenceNextId() {
        DataStore store = tempStore();
        IssueManager first = new IssueManager(store);
        Issue one = issue("One", "Description", "Location", "Other", "Low", "citizen1");
        Issue two = issue("Two", "Description", "Location", "Other", "Low", "citizen1");

        check(first.addIssue(one), "first issue should be added");
        check(first.addIssue(two), "second issue should be added");
        check(one.getId() == 1 && two.getId() == 2, "ids should be 1 and 2");
        check(first.deleteIssue(two.getId()), "highest id should be deleted");

        IssueManager second = new IssueManager(store);
        Issue three = issue("Three", "Description", "Location", "Other", "Low", "citizen1");
        check(second.addIssue(three), "third issue should be added");
        check(three.getId() == 3, "ids should continue after restart without reuse");
    }

    private static void testNextIdRebuiltWithoutMeta() {
        DataStore store = tempStore();
        IssueManager first = new IssueManager(store);
        check(first.addIssue(issue("One", "Description", "Location", "Other", "Low", "citizen1")),
                "first issue should be added");
        check(first.addIssue(issue("Two", "Description", "Location", "Other", "Low", "citizen1")),
                "second issue should be added");

        try {
            Files.deleteIfExists(store.getDataDir().resolve("meta.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        IssueManager second = new IssueManager(store);
        Issue three = issue("Three", "Description", "Location", "Other", "Low", "citizen1");
        check(second.addIssue(three), "third issue should be added");
        check(three.getId() == 3, "next id should be rebuilt from the existing issues");
    }

    private static void testPersistenceCsvEscaping() {
        DataStore store = tempStore();
        UserManager users = new UserManager(store);
        check(users.register("citizen1", "secret1", "Doe, Jane"),
                "name with a comma should register");

        IssueManager issues = new IssueManager(store);
        String title = "Pothole, \"large\"";
        String description = "Line one\nLine two";
        Issue original = issue(title, description, "Main St, East", "Road", "High", "citizen1");
        check(issues.addIssue(original), "issue with special characters should be added");

        UserManager reloadedUsers = new UserManager(store);
        User reloadedCitizen = reloadedUsers.login("citizen1", "secret1");
        check(reloadedCitizen != null, "citizen should reload");
        check("Doe, Jane".equals(reloadedCitizen.getName()),
                "name with a comma should survive the CSV round-trip");

        IssueManager reloadedIssues = new IssueManager(store);
        Issue loaded = reloadedIssues.getById(original.getId());
        check(loaded != null, "escaped issue should reload");
        check(title.equals(loaded.getTitle()), "quotes and commas should survive");
        check(description.equals(loaded.getDescription()), "multiline description should survive");
        check("Main St, East".equals(loaded.getLocation()), "comma in location should survive");
    }

    private static void testMissingDataFiles() {
        DataStore store = tempStore();
        UserManager users = new UserManager(store);
        IssueManager issues = new IssueManager(store);

        check(users.login("admin", "admin123") != null, "admin should be seeded");
        check(users.getAllUsers().size() == 1, "only the admin should exist");
        check(issues.getAllIssues().isEmpty(), "no issues should exist");
        check(Files.exists(store.getDataDir().resolve("users.csv")), "users.csv should be created");
        check(!Files.exists(store.getDataDir().resolve("issues.csv")),
                "issues.csv should not be created until there is data");
    }

    private static void testCorruptUsersFile() {
        DataStore store = tempStore();
        IssueManager issueManager = new IssueManager(store);
        Issue existing = issue("Existing", "Survives user corruption", "Main St", "Road", "High", "citizen1");
        check(issueManager.addIssue(existing), "setup issue should be added");
        write(store.getDataDir().resolve("users.csv"), "not,a,valid,user,file\n");

        UserManager users = new UserManager(store);
        check(users.login("admin", "admin123") != null, "fresh admin should be seeded");
        check(!store.getWarnings().isEmpty(), "a warning should be recorded");
        check(countCorruptBackups(store.getDataDir(), "users.csv") == 1,
                "corrupt users file should be backed up");

        IssueManager reloadedIssues = new IssueManager(store);
        check(reloadedIssues.getById(existing.getId()) != null,
                "a corrupt users file should not delete issues");
    }

    private static void testCorruptIssuesFile() {
        DataStore store = tempStore();
        new UserManager(store);
        write(store.getDataDir().resolve("issues.csv"), "garbage,row\n");

        IssueManager issues = new IssueManager(store);
        check(issues.getAllIssues().isEmpty(), "issues should start fresh");
        check(store.getWarnings().size() == 1, "a warning should be recorded");
        check(countCorruptBackups(store.getDataDir(), "issues.csv") == 1,
                "corrupt issues file should be backed up");

        UserManager reloadedUsers = new UserManager(store);
        check(reloadedUsers.login("admin", "admin123") != null,
                "a corrupt issues file should not delete users");

        Issue first = issue("One", "Description", "Location", "Other", "Low", "citizen1");
        check(issues.addIssue(first), "new issues should be addable after recovery");
        check(first.getId() == 1, "ids should restart at 1 after a corrupt issues file");
    }

    private static void testBackupCreatedOnSecondSave() {
        DataStore store = tempStore();
        UserManager users = new UserManager(store);
        Path backup = store.getDataDir().resolve("users.csv.bak");

        check(Files.exists(store.getDataDir().resolve("users.csv")), "users.csv should exist");
        check(!Files.exists(backup), "no backup should exist before the second save");

        check(users.register("citizen1", "secret1", "Citizen One"), "registration should succeed");
        check(Files.exists(backup), ".bak should be created on the second save");
    }

    private static void testAdminSeededWhenMissing() {
        DataStore store = tempStore();
        Citizen citizen = new Citizen("bob", "secret1", "Bob");
        String csv = "role,username,name,passwordHash,salt,iterations\n"
                + "Citizen,bob,Bob," + citizen.getPasswordHash() + ","
                + citizen.getSalt() + "," + citizen.getIterations() + "\n";
        write(store.getDataDir().resolve("users.csv"), csv);

        UserManager users = new UserManager(store);
        check(users.getAllUsers().size() == 2, "admin should be seeded alongside existing users");
        check(users.login("admin", "admin123") != null, "seeded admin should log in");
        check(users.login("bob", "secret1") != null, "existing citizen should still log in");
    }

    private static void testPasswordsAreHashedOnDisk() {
        DataStore store = tempStore();
        UserManager users = new UserManager(store);
        check(users.register("citizen1", "secret1", "Citizen One"), "registration should succeed");
        check(users.createAdmin("admin2", "adminSecret2", "Admin Two"),
                "admin creation should succeed");
        User citizen = users.login("citizen1", "secret1");
        check(users.changePassword(citizen, "secret1", "newSecret1") == null,
                "password change should succeed");

        String contents = read(store.getDataDir().resolve("users.csv"));
        check(!contents.contains("secret1"), "plaintext registration password must not be written");
        check(!contents.contains("newSecret1"), "plaintext changed password must not be written");
        check(!contents.contains("adminSecret2"), "plaintext admin password must not be written");
        check(!contents.contains("admin123"), "seeded admin password must not be written to disk");
        check(contents.contains("passwordHash"), "the header should document the hash column");
    }

    private static void testSaveFailureRollsBack() {
        DataStore store = tempStore();
        UserManager users = new UserManager(store);
        int before = users.getAllUsers().size();

        breakDataDir(store);
        boolean threw = false;
        try {
            users.register("citizen1", "secret1", "Citizen One");
        } catch (DataStoreException expected) {
            threw = true;
        }

        check(threw, "a failed save should surface a DataStoreException");
        check(users.getAllUsers().size() == before, "failed registration should be rolled back");
        check(users.login("citizen1", "secret1") == null,
                "rolled-back user should not be able to log in");
    }

    private static void testIssueSaveFailureRollsBack() {
        DataStore store = tempStore();
        IssueManager issues = new IssueManager(store);

        breakDataDir(store);
        boolean threw = false;
        try {
            issues.addIssue(issue("Pothole", "Deep pothole", "Main St", "Road", "High", "citizen1"));
        } catch (DataStoreException expected) {
            threw = true;
        }

        check(threw, "a failed save should surface a DataStoreException");
        check(issues.getAllIssues().isEmpty(), "failed add should be rolled back");

        restoreDataDir(store);
        Issue retry = issue("Pothole", "Deep pothole", "Main St", "Road", "High", "citizen1");
        check(issues.addIssue(retry), "issue should be addable after the directory is restored");
        check(retry.getId() == 1, "a rolled-back id should not be consumed");
    }

    private static void testIssueSaveFailureKeepsIdReserved() {
        DataStore store = tempStore();
        IssueManager issues = new IssueManager(store);
        Path issuesFile = store.getDataDir().resolve("issues.csv");
        try {
            Files.createDirectory(issuesFile);
            Files.write(issuesFile.resolve("blocker"), new byte[]{1});
        } catch (IOException e) {
            throw new RuntimeException("Could not obstruct the issues file.", e);
        }

        boolean threw = false;
        try {
            issues.addIssue(issue("Blocked", "Description", "Location", "Other", "Low", "citizen1"));
        } catch (DataStoreException expected) {
            threw = true;
        }

        check(threw, "a failed issue write after the counter was saved should surface an error");
        check(issues.getAllIssues().isEmpty(), "the failed issue should be rolled back");

        try {
            Files.delete(issuesFile.resolve("blocker"));
            Files.delete(issuesFile);
            Files.deleteIfExists(store.getDataDir().resolve("issues.csv.bak"));
        } catch (IOException e) {
            throw new RuntimeException("Could not remove the issues-file obstruction.", e);
        }

        Issue retry = issue("Retry", "Description", "Location", "Other", "Low", "citizen1");
        check(issues.addIssue(retry), "an issue should be addable after the obstruction is removed");
        check(retry.getId() == 2, "an id whose counter was persisted must not be reused");

        IssueManager reloaded = new IssueManager(store);
        check(reloaded.getAllIssues().size() == 1, "one issue should survive the restart");
        check(reloaded.getAllIssues().get(0).getId() == 2,
                "the skipped id should remain skipped after a restart");
    }

    // ── Filters ──────────────────────────────────────────────
    private static void testFilterTextSearch() {
        Issue pothole = issue("Pothole", "Deep hole in the road", "Main St", "Road", "High", "alice");
        Issue leak = issue("Leak", "Water everywhere", "Elm St", "Water", "Medium", "bob");
        List<Issue> all = Arrays.asList(pothole, leak);
        IssueFilter filter = new IssueFilter();

        check(filter.apply(all).size() == 2, "empty filter should match every issue");

        filter.setSearchText("POTHOLE");
        check(filter.apply(all).size() == 1 && filter.matches(pothole),
                "title search should be case-insensitive");

        filter.setSearchText("everywhere");
        check(filter.matches(leak) && !filter.matches(pothole),
                "description should be searchable");

        filter.setSearchText("elm st");
        check(filter.matches(leak) && !filter.matches(pothole),
                "location should be searchable");

        filter.setSearchText("ALICE");
        check(filter.matches(pothole) && !filter.matches(leak),
                "reporter should be searchable");

        filter.setSearchText("no such issue");
        check(filter.apply(all).isEmpty(), "unmatched text should return no issues");
        check(filter.apply(null).isEmpty(), "a null list should filter to an empty list");
    }

    private static void testFilterDropdowns() {
        Issue resolvedRoad = issue("Pothole", "Deep hole", "Main St", "Road", "Critical", "alice");
        resolvedRoad.setStatus("Resolved");
        resolvedRoad.setDepartment("Roads Dept");

        Issue pendingRoad = issue("Cracked path", "Cracked pavement", "Oak St", "Road", "Medium", "bob");

        Issue pendingWater = issue("Leak", "Water leak", "Elm St", "Water", "High", "carol");
        pendingWater.setDepartment("Water Dept");

        List<Issue> all = Arrays.asList(resolvedRoad, pendingRoad, pendingWater);
        IssueFilter filter = new IssueFilter();

        filter.setStatus("Resolved");
        check(filter.apply(all).size() == 1 && filter.matches(resolvedRoad),
                "status filter should select matching issues");
        filter.setStatus("Pending");
        check(filter.apply(all).size() == 2, "status filter should support Pending");

        filter.setStatus(IssueFilter.ALL);
        filter.setCategory("Road");
        check(filter.apply(all).size() == 2, "category filter should select matching issues");

        filter.setCategory(IssueFilter.ALL);
        filter.setPriority("High");
        check(filter.apply(all).size() == 1 && filter.matches(pendingWater),
                "priority filter should select matching issues");

        filter.setPriority(IssueFilter.ALL);
        filter.setDepartment(IssueFilter.UNASSIGNED);
        check(filter.apply(all).size() == 1 && filter.matches(pendingRoad),
                "Unassigned should be selectable as a department");

        filter.setDepartment("Water Dept");
        check(filter.apply(all).size() == 1 && filter.matches(pendingWater),
                "named departments should filter correctly");

        IssueFilter combined = new IssueFilter(
                "", "Pending", "Road", "Medium", IssueFilter.UNASSIGNED, IssueFilter.ANY_TIME);
        check(combined.apply(all).size() == 1 && combined.matches(pendingRoad),
                "all active filters should be combined with AND");
    }

    private static void testFilterTimeRanges() {
        LocalDate today = LocalDate.now();
        Issue todayIssue = datedIssue(1, "Today", today);
        Issue sixDaysOld = datedIssue(2, "Six days", today.minusDays(6));
        Issue sevenDaysOld = datedIssue(3, "Seven days", today.minusDays(7));
        Issue tenDaysOld = datedIssue(4, "Ten days", today.minusDays(10));
        Issue fortyDaysOld = datedIssue(5, "Forty days", today.minusDays(40));
        List<Issue> all = Arrays.asList(
                todayIssue, sixDaysOld, sevenDaysOld, tenDaysOld, fortyDaysOld);

        IssueFilter filter = new IssueFilter();
        check(filter.apply(all).size() == 5, "Any time should include every issue");

        filter.setTimeRange(IssueFilter.TODAY);
        check(filter.apply(all).size() == 1 && filter.matches(todayIssue),
                "Today should only include today's issues");

        filter.setTimeRange(IssueFilter.LAST_7_DAYS);
        List<Issue> lastSeven = filter.apply(all);
        check(lastSeven.size() == 2 && lastSeven.contains(todayIssue) && lastSeven.contains(sixDaysOld),
                "Last 7 days should include today and the previous six days");

        filter.setTimeRange(IssueFilter.LAST_30_DAYS);
        List<Issue> lastThirty = filter.apply(all);
        check(lastThirty.size() == 4 && lastThirty.contains(tenDaysOld) && !lastThirty.contains(fortyDaysOld),
                "Last 30 days should exclude older issues");

        filter.setTimeRange("Not a real range");
        check(filter.apply(all).size() == 5,
                "an unknown time range should safely fall back to Any time");
    }

    // ── Accounts ─────────────────────────────────────────────
    private static void testPasswordChange() {
        DataStore store = tempStore();
        UserManager users = new UserManager(store);
        check(users.register("citizen1", "secret1", "Citizen One"), "registration should succeed");
        User citizen = users.login("citizen1", "secret1");
        check(citizen != null, "citizen should log in");

        check(users.validatePasswordChange(citizen, "wrong", "newsecret") != null,
                "wrong current password should be rejected");
        check(users.validatePasswordChange(citizen, "secret1", "12345") != null,
                "short new password should be rejected");
        check(users.validatePasswordChange(citizen, "secret1", "secret1") != null,
                "unchanged password should be rejected");
        check(users.changePassword(citizen, "secret1", "newsecret") == null,
                "valid password change should succeed");

        check(users.login("citizen1", "secret1") == null,
                "old password should stop working");
        check(users.login("citizen1", "newsecret") != null,
                "new password should work immediately");

        UserManager reloaded = new UserManager(store);
        check(reloaded.login("citizen1", "newsecret") != null,
                "changed password should survive a restart");
        check(reloaded.login("citizen1", "secret1") == null,
                "old password should remain invalid after restart");
    }

    private static void testStaleUserCannotChangePassword() {
        DataStore store = tempStore();
        UserManager users = new UserManager(store);
        check(users.register("citizen1", "secret1", "Citizen One"), "registration should succeed");
        check(users.login("citizen1", "secret1") != null, "canonical citizen should log in");

        User stale = new Citizen("citizen1", "secret1", "Stale Copy");
        check(users.validatePasswordChange(stale, "secret1", "newsecret") != null,
                "a non-managed user object should be rejected");
        check(users.changePassword(stale, "secret1", "newsecret") != null,
                "stale user object should not report a successful change");
        check(users.login("citizen1", "secret1") != null,
                "canonical account password should be unchanged");
        check(users.login("citizen1", "newsecret") == null,
                "stale object must not change the stored password");
    }

    private static void testDefaultAdminPasswordDetection() {
        DataStore store = tempStore();
        UserManager users = new UserManager(store);
        User admin = users.login("admin", "admin123");
        check(admin != null, "seeded admin should log in");
        check(users.isUsingDefaultAdminPassword(admin),
                "seeded admin should be detected as using the default password");

        check(users.changePassword(admin, "admin123", "stronger1") == null,
                "admin should be able to change the default password");
        check(!users.isUsingDefaultAdminPassword(admin),
                "default-password detection should stop after the change");
        check(users.changePassword(admin, "stronger1", "admin123") != null,
                "admins should not be allowed to set the default password");

        check(users.register("citizen1", "admin123", "Citizen One"),
                "citizen registration should still work");
        User citizen = users.login("citizen1", "admin123");
        check(!users.isUsingDefaultAdminPassword(citizen),
                "default-password detection should only apply to admins");

        UserManager reloaded = new UserManager(store);
        check(reloaded.login("admin", "stronger1") != null,
                "changed admin password should survive a restart");
    }

    private static void testAdminCreation() {
        DataStore store = tempStore();
        UserManager users = new UserManager(store);

        check(users.validateAdminCreation("admin2", "admin123", "Admin Two") != null,
                "new admins should not use the default password");
        check(users.validateAdminCreation("admin", "secret2", "Admin Two") != null,
                "admin creation should enforce username uniqueness");
        check(users.validateAdminCreation("admin2", "secret2", "Admin Two") == null,
                "valid admin details should pass validation");
        check(users.createAdmin("admin2", "secret2", "Admin Two"),
                "admin creation should succeed");
        check(!users.createAdmin("admin2", "secret3", "Duplicate Admin"),
                "duplicate admin username should be rejected");

        User admin2 = users.login("admin2", "secret2");
        check(admin2 != null && "Admin".equals(admin2.getRole()),
                "created account should be able to log in as an admin");

        UserManager reloaded = new UserManager(store);
        check(reloaded.login("admin2", "secret2") != null,
                "created admin should survive a restart");
    }

    private static void testAdminRemoval() {
        DataStore store = tempStore();
        UserManager users = new UserManager(store);
        check(users.createAdmin("admin2", "secret2", "Admin Two"), "second admin should be created");
        check(users.register("citizen1", "secret1", "Citizen One"), "citizen should be created");

        check(users.validateAdminRemoval("admin", "admin") != null,
                "admins should not be able to remove themselves");
        check(users.validateAdminRemoval("citizen1", "admin") != null,
                "citizen accounts should not be removable here");
        check(users.validateAdminRemoval("missing", "admin") != null,
                "missing admin should be rejected");
        check(users.validateAdminRemoval("admin2", "citizen1") != null,
                "non-admin actors should not remove admins");
        check(users.validateAdminRemoval("admin2", "admin") == null,
                "removing another admin should be allowed");

        check(users.removeAdmin("admin2", "admin"), "admin removal should succeed");
        check(users.login("admin2", "secret2") == null, "removed admin should not log in");
        check(users.login("admin", "admin123") != null, "remaining admin should still log in");

        UserManager reloaded = new UserManager(store);
        check(reloaded.login("admin2", "secret2") == null,
                "removed admin should stay removed after restart");
        check(reloaded.login("citizen1", "secret1") != null,
                "removing an admin should not affect citizens");
    }

    private static void testPasswordChangeSaveFailureRollsBack() {
        DataStore store = tempStore();
        UserManager users = new UserManager(store);
        check(users.register("citizen1", "secret1", "Citizen One"), "registration should succeed");
        User citizen = users.login("citizen1", "secret1");

        breakDataDir(store);
        boolean threw = false;
        try {
            users.changePassword(citizen, "secret1", "newsecret");
        } catch (DataStoreException expected) {
            threw = true;
        }
        check(threw, "a failed password save should surface a DataStoreException");
        check(users.login("citizen1", "secret1") != null,
                "failed password change should restore the old password");
        check(users.login("citizen1", "newsecret") == null,
                "failed password change should not activate the new password");

        restoreDataDir(store);
    }

    private static void testAdminCreationSaveFailureRollsBack() {
        DataStore store = tempStore();
        UserManager users = new UserManager(store);
        int before = users.getAllUsers().size();

        breakDataDir(store);
        boolean threw = false;
        try {
            users.createAdmin("admin2", "secret2", "Admin Two");
        } catch (DataStoreException expected) {
            threw = true;
        }
        check(threw, "a failed admin save should surface a DataStoreException");
        check(users.getAllUsers().size() == before, "failed admin creation should roll back");
        check(users.login("admin2", "secret2") == null,
                "rolled-back admin should not be able to log in");

        restoreDataDir(store);
        check(users.createAdmin("admin2", "secret2", "Admin Two"),
                "admin creation should work after the directory is restored");
    }

    private static void testAdminRemovalSaveFailureRollsBack() {
        DataStore store = tempStore();
        UserManager users = new UserManager(store);
        check(users.createAdmin("admin2", "secret2", "Admin Two"), "second admin should be created");
        int before = users.getAllUsers().size();

        breakDataDir(store);
        boolean threw = false;
        try {
            users.removeAdmin("admin2", "admin");
        } catch (DataStoreException expected) {
            threw = true;
        }
        check(threw, "a failed admin removal should surface a DataStoreException");
        check(users.getAllUsers().size() == before, "failed admin removal should roll back");
        check(users.login("admin2", "secret2") != null,
                "rolled-back admin should still be able to log in");

        restoreDataDir(store);
        check(users.removeAdmin("admin2", "admin"),
                "admin removal should work after the directory is restored");
    }

    // ── Helpers ──────────────────────────────────────────────
    private static Issue issue(String title, String description, String location,
                               String category, String priority, String reportedBy) {
        return new Issue(title, description, location, category, priority, reportedBy);
    }

    private static Issue datedIssue(int id, String title, LocalDate date) {
        String timestamp = date.atTime(9, 0).format(
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
        return new Issue(id, title, "Description", "Location", "Other", "Low",
                "Pending", "Unassigned", "citizen1", timestamp);
    }

    private static DataStore tempStore() {
        try {
            Path dir = Files.createTempDirectory("nir-test-");
            tempDirs.add(dir);
            return new DataStore(dir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create a temporary data directory.", e);
        }
    }

    private static void breakDataDir(DataStore store) {
        Path dir = store.getDataDir();
        deleteRecursively(dir);
        try {
            Files.createFile(dir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void restoreDataDir(DataStore store) {
        Path dir = store.getDataDir();
        try {
            Files.deleteIfExists(dir);
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int countCorruptBackups(Path dir, String fileName) {
        try (Stream<Path> files = Files.list(dir)) {
            return (int) files
                    .filter(path -> path.getFileName().toString()
                            .startsWith(fileName + ".corrupt-"))
                    .count();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void write(Path file, String text) {
        try {
            Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) return;
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Best-effort cleanup.
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void cleanupTempDirs() {
        for (Path dir : tempDirs) {
            try {
                deleteRecursively(dir);
            } catch (RuntimeException ignored) {
                // Best-effort cleanup.
            }
        }
    }

    private static void run(String name, Runnable test) {
        try {
            test.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("FAIL  " + name + " -> " + t.getMessage());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }
}
