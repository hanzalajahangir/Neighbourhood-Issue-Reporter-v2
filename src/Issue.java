import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Issue {
    private int id;
    private String title;
    private String description;
    private String location;
    private String category;
    private String priority;
    private String status;
    private String department;
    private String reportedBy;
    private String dateReported;

    public static final String[] CATEGORIES  = {"Road", "Electricity", "Water", "Sanitation", "Other"};
    public static final String[] PRIORITIES  = {"Low", "Medium", "High", "Critical"};
    public static final String[] STATUSES    = {"Pending", "In Progress", "Resolved"};
    public static final String[] DEPARTMENTS = {"Roads Dept", "Electricity Dept", "Water Dept", "Sanitation Dept", "General Dept"};

    public Issue(String title, String description, String location,
                 String category, String priority, String reportedBy) {
        this.title       = title;
        this.description = description;
        this.location    = location;
        this.category    = category;
        this.priority    = priority;
        this.status      = "Pending";
        this.department  = "Unassigned";
        this.reportedBy  = reportedBy;
        this.dateReported = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
    }

    /**
     * Restores an issue exactly as it was saved, including its ID and timestamp.
     * Used only by {@link DataStore} when loading from disk.
     */
    Issue(int id, String title, String description, String location,
          String category, String priority, String status, String department,
          String reportedBy, String dateReported) {
        this.id           = id;
        this.title        = title;
        this.description  = description;
        this.location     = location;
        this.category     = category;
        this.priority     = priority;
        this.status       = status;
        this.department   = department;
        this.reportedBy   = reportedBy;
        this.dateReported = dateReported;
    }

    public int    getId()           { return id; }
    public String getTitle()        { return title; }
    public String getDescription()  { return description; }
    public String getLocation()     { return location; }
    public String getCategory()     { return category; }
    public String getPriority()     { return priority; }
    public String getStatus()       { return status; }
    public String getDepartment()   { return department; }
    public String getReportedBy()   { return reportedBy; }
    public String getDateReported() { return dateReported; }

    public void setStatus(String status)         { this.status = status; }
    public void setDepartment(String department) { this.department = department; }
    public void setPriority(String priority)     { this.priority = priority; }

    // Assigned by IssueManager when the issue is added to the store.
    void assignId(int id) { this.id = id; }

    public void printSummary() {
        System.out.println("  +-----------------------------------------------+");
        System.out.printf ("  | Issue #%-3d                                    |%n", id);
        System.out.println("  +-----------------------------------------------+");
        System.out.printf ("  | Title      : %-32s|%n", truncate(title, 32));
        System.out.printf ("  | Category   : %-14s Priority : %-8s|%n", category, priority);
        System.out.printf ("  | Location   : %-32s|%n", truncate(location, 32));
        System.out.printf ("  | Status     : %-32s|%n", status);
        System.out.printf ("  | Department : %-32s|%n", truncate(department, 32));
        System.out.printf ("  | Reported By: %-32s|%n", truncate(reportedBy, 32));
        System.out.println("  +-----------------------------------------------+");
    }

    public void printFull() {
        System.out.println("\n  ==========================================");
        System.out.printf ("       ISSUE REPORT  #%d%n", id);
        System.out.println("  ==========================================");
        System.out.printf ("  Title       : %s%n", title);
        System.out.printf ("  Description : %s%n", description);
        System.out.printf ("  Location    : %s%n", location);
        System.out.printf ("  Category    : %s%n", category);
        System.out.printf ("  Priority    : %s%n", priority);
        System.out.printf ("  Status      : %s%n", status);
        System.out.printf ("  Department  : %s%n", department);
        System.out.printf ("  Reported By : %s%n", reportedBy);
        System.out.printf ("  Date        : %s%n", dateReported);
        System.out.println("  ==========================================\n");
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 2) + ".." : s;
    }
}
 