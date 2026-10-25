import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure filtering rules for the issue table. Keeping this class independent of
 * Swing makes the search, dropdown, and time-range behaviour easy to test.
 */
public final class IssueFilter {
    public static final String ALL = "All";
    public static final String UNASSIGNED = "Unassigned";

    public static final String ANY_TIME = "Any time";
    public static final String TODAY = "Today";
    public static final String LAST_7_DAYS = "Last 7 days";
    public static final String LAST_30_DAYS = "Last 30 days";
    public static final String[] TIME_RANGES = {
            ANY_TIME, TODAY, LAST_7_DAYS, LAST_30_DAYS
    };

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private String searchText = "";
    private String status = ALL;
    private String category = ALL;
    private String priority = ALL;
    private String department = ALL;
    private String timeRange = ANY_TIME;

    public IssueFilter() {
    }

    public IssueFilter(String searchText, String status, String category,
                       String priority, String department, String timeRange) {
        setSearchText(searchText);
        setStatus(status);
        setCategory(category);
        setPriority(priority);
        setDepartment(department);
        setTimeRange(timeRange);
    }

    public String getSearchText() { return searchText; }
    public String getStatus()     { return status; }
    public String getCategory()   { return category; }
    public String getPriority()   { return priority; }
    public String getDepartment() { return department; }
    public String getTimeRange()  { return timeRange; }

    public void setSearchText(String searchText) {
        this.searchText = searchText == null ? "" : searchText.trim();
    }

    public void setStatus(String status) {
        this.status = choiceOrDefault(status);
    }

    public void setCategory(String category) {
        this.category = choiceOrDefault(category);
    }

    public void setPriority(String priority) {
        this.priority = choiceOrDefault(priority);
    }

    public void setDepartment(String department) {
        this.department = choiceOrDefault(department);
    }

    public void setTimeRange(String timeRange) {
        if (timeRange != null) {
            String candidate = timeRange.trim();
            for (String option : TIME_RANGES) {
                if (option.equals(candidate)) {
                    this.timeRange = option;
                    return;
                }
            }
        }
        this.timeRange = ANY_TIME;
    }

    private String choiceOrDefault(String value) {
        if (value == null || value.trim().isEmpty()) {
            return ALL;
        }
        return value;
    }

    /**
     * Applies every active condition to the supplied issues, preserving their
     * original order. The returned list is always a new list.
     */
    public List<Issue> apply(List<Issue> issues) {
        List<Issue> result = new ArrayList<>();
        if (issues == null) {
            return result;
        }
        for (Issue issue : issues) {
            if (matches(issue)) {
                result.add(issue);
            }
        }
        return result;
    }

    public boolean matches(Issue issue) {
        if (issue == null) {
            return false;
        }
        return matchesSearch(issue)
                && matchesChoice(status, issue.getStatus())
                && matchesChoice(category, issue.getCategory())
                && matchesChoice(priority, issue.getPriority())
                && matchesChoice(department, issue.getDepartment())
                && matchesTimeRange(issue);
    }

    private boolean matchesSearch(Issue issue) {
        if (searchText.isEmpty()) {
            return true;
        }
        String query = searchText.toLowerCase(Locale.ROOT);
        return contains(issue.getTitle(), query)
                || contains(issue.getDescription(), query)
                || contains(issue.getLocation(), query)
                || contains(issue.getReportedBy(), query);
    }

    private boolean contains(String value, String lowerCaseQuery) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(lowerCaseQuery);
    }

    private boolean matchesChoice(String selected, String actual) {
        return ALL.equalsIgnoreCase(selected)
                || (actual != null && selected.equalsIgnoreCase(actual));
    }

    private boolean matchesTimeRange(Issue issue) {
        if (ANY_TIME.equals(timeRange)) {
            return true;
        }
        if (issue.getDateReported() == null) {
            return false;
        }

        LocalDate issueDate;
        try {
            issueDate = LocalDateTime.parse(issue.getDateReported(), DATE_FORMAT).toLocalDate();
        } catch (DateTimeParseException e) {
            return false;
        }

        LocalDate today = LocalDate.now();
        if (TODAY.equals(timeRange)) {
            return issueDate.isEqual(today);
        }
        if (LAST_7_DAYS.equals(timeRange)) {
            return !issueDate.isBefore(today.minusDays(6)) && !issueDate.isAfter(today);
        }
        if (LAST_30_DAYS.equals(timeRange)) {
            return !issueDate.isBefore(today.minusDays(29)) && !issueDate.isAfter(today);
        }
        return false;
    }
}
