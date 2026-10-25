import java.util.ArrayList;
import java.util.List;

public class IssueManager {
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_LOCATION_LENGTH = 150;

    private final DataStore store;
    private List<Issue> issues = new ArrayList<>();
    private int nextId = 1;

    public IssueManager(DataStore store) {
        if (store == null) {
            throw new IllegalArgumentException("DataStore is required.");
        }
        this.store = store;
        this.issues = store.loadIssues();
        this.nextId = store.loadNextIssueId(issues);
    }

    // ── Validate issue input ─────────────────────────────────
    public String validateIssue(String title, String description, String location, String reportedBy) {
        String cleanTitle = title == null ? "" : title.trim();
        String cleanDescription = description == null ? "" : description.trim();
        String cleanLocation = location == null ? "" : location.trim();

        if (cleanTitle.isEmpty()) {
            return "Issue title is required.";
        }
        if (cleanTitle.length() > MAX_TITLE_LENGTH) {
            return "Issue title must be at most " + MAX_TITLE_LENGTH + " characters.";
        }
        if (cleanDescription.isEmpty()) {
            return "Issue description is required.";
        }
        if (cleanDescription.length() > MAX_DESCRIPTION_LENGTH) {
            return "Issue description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.";
        }
        if (cleanLocation.isEmpty()) {
            return "Issue location is required.";
        }
        if (cleanLocation.length() > MAX_LOCATION_LENGTH) {
            return "Issue location must be at most " + MAX_LOCATION_LENGTH + " characters.";
        }
        if (hasActiveDuplicate(reportedBy, cleanTitle, cleanLocation)) {
            return "You already reported this issue at this location.";
        }
        return null;
    }

    public String validateIssue(Issue issue) {
        return validateIssue(
                issue.getTitle(),
                issue.getDescription(),
                issue.getLocation(),
                issue.getReportedBy());
    }

    private boolean hasActiveDuplicate(String reportedBy, String title, String location) {
        if (reportedBy == null) return false;
        for (Issue issue : issues) {
            boolean active = issue.getStatus().equalsIgnoreCase("Pending")
                    || issue.getStatus().equalsIgnoreCase("In Progress");
            if (active
                    && issue.getReportedBy().equalsIgnoreCase(reportedBy)
                    && issue.getTitle().equalsIgnoreCase(title)
                    && issue.getLocation().equalsIgnoreCase(location)) {
                return true;
            }
        }
        return false;
    }

    // ── Add ──────────────────────────────────────────────────
    public boolean addIssue(Issue issue) {
        if (validateIssue(issue) != null) {
            return false;
        }

        int assignedId = nextId;
        issue.assignId(assignedId);
        issues.add(issue);
        nextId = assignedId + 1;

        try {
            store.saveNextIssueId(nextId);
        } catch (DataStoreException e) {
            // The counter was not persisted, so this id can safely be reused.
            issues.remove(issue);
            nextId = assignedId;
            throw e;
        }

        try {
            store.saveIssues(issues);
        } catch (DataStoreException e) {
            // The counter may already contain nextId. Keep it advanced so the
            // failed id is never handed out again, even in this session.
            issues.remove(issue);
            throw e;
        }
        return true;
    }

    // ── Get by ID ────────────────────────────────────────────
    public Issue getById(int id) {
        for (Issue i : issues) {
            if (i.getId() == id) return i;
        }
        return null;
    }

    // ── All issues ───────────────────────────────────────────
    public List<Issue> getAllIssues() {
        return new ArrayList<>(issues);
    }

    // ── Issues by citizen ────────────────────────────────────
    public List<Issue> getByUser(String username) {
        List<Issue> result = new ArrayList<>();
        for (Issue i : issues) {
            if (i.getReportedBy().equalsIgnoreCase(username)) {
                result.add(i);
            }
        }
        return result;
    }

    // ── Update status ────────────────────────────────────────
    public boolean updateStatus(int id, String status) {
        if (!isAllowed(status, Issue.STATUSES)) return false;
        Issue issue = getById(id);
        if (issue == null) return false;

        String previous = issue.getStatus();
        if (status.equals(previous)) return true;

        issue.setStatus(status);
        try {
            store.saveIssues(issues);
        } catch (DataStoreException e) {
            issue.setStatus(previous);
            throw e;
        }
        return true;
    }

    // ── Assign department ────────────────────────────────────
    public boolean assignDepartment(int id, String department) {
        if (!isAllowed(department, Issue.DEPARTMENTS)) return false;
        Issue issue = getById(id);
        if (issue == null) return false;

        String previous = issue.getDepartment();
        if (department.equals(previous)) return true;

        issue.setDepartment(department);
        try {
            store.saveIssues(issues);
        } catch (DataStoreException e) {
            issue.setDepartment(previous);
            throw e;
        }
        return true;
    }

    // ── Update priority ──────────────────────────────────────
    public boolean updatePriority(int id, String priority) {
        if (!isAllowed(priority, Issue.PRIORITIES)) return false;
        Issue issue = getById(id);
        if (issue == null) return false;

        String previous = issue.getPriority();
        if (priority.equals(previous)) return true;

        issue.setPriority(priority);
        try {
            store.saveIssues(issues);
        } catch (DataStoreException e) {
            issue.setPriority(previous);
            throw e;
        }
        return true;
    }

    // ── Delete issue ─────────────────────────────────────────
    public boolean deleteIssue(int id) {
        Issue issue = getById(id);
        if (issue == null) return false;

        int index = issues.indexOf(issue);
        issues.remove(index);
        try {
            store.saveIssues(issues);
        } catch (DataStoreException e) {
            issues.add(index, issue);
            throw e;
        }
        return true;
    }

    private boolean isAllowed(String value, String[] allowed) {
        for (String option : allowed) {
            if (option.equals(value)) {
                return true;
            }
        }
        return false;
    }

    // ── Count by status (used for statistics) ────────────────
    private int countByStatus(String status) {
        int count = 0;
        for (Issue i : issues) {
            if (i.getStatus().equalsIgnoreCase(status)) count++;
        }
        return count;
    }

    // ── Count critical issues (used for statistics) ───────────
    private int countCritical() {
        int count = 0;
        for (Issue i : issues) {
            if (i.getPriority().equalsIgnoreCase("Critical")) count++;
        }
        return count;
    }

    // ── Statistics ───────────────────────────────────────────
    public void printStatistics() {
        int total    = issues.size();
        int pending  = countByStatus("Pending");
        int progress = countByStatus("In Progress");
        int resolved = countByStatus("Resolved");
        int critical = countCritical();

        System.out.println("\n  ==============================");
        System.out.println("       ISSUE STATISTICS");
        System.out.println("  ==============================");
        System.out.printf ("  Total Issues   : %d%n", total);
        System.out.printf ("  Pending        : %d%n", pending);
        System.out.printf ("  In Progress    : %d%n", progress);
        System.out.printf ("  Resolved       : %d%n", resolved);
        System.out.printf ("  Critical Issues: %d%n", critical);
        System.out.println("  ==============================\n");
    }

    // ── Print a list of issues ───────────────────────────────
    public void printList(List<Issue> list) {
        if (list.isEmpty()) {
            System.out.println("\n  [!] No issues found.\n");
            return;
        }
        System.out.println();
        for (Issue i : list) i.printSummary();
    }
}
