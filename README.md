# Neighbourhood Issue Reporter

A desktop application built with **Java Swing** that lets citizens report local community issues (potholes, water leaks, power outages, sanitation problems, etc.) and lets administrators track, prioritize, assign, and resolve them.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Architecture](#architecture)
- [Class Reference](#class-reference)
- [Getting Started](#getting-started)
- [Usage Guide](#usage-guide)
- [Default Admin Account](#default-admin-account)
- [Data Persistence](#data-persistence)
- [Known Limitations](#known-limitations)
- [Possible Improvements](#possible-improvements)

---

## Overview

The app models two kinds of users — **Citizens** and **Admins** — who share a single login/registration screen and are then routed into a role-aware dashboard:

- **Citizens** can register, log in, submit new issues, and view/track only the issues they personally reported.
- **Admins** can see every issue in the system, update its status/priority/department, delete issues, view registered accounts, and create/remove admin accounts.
- Both roles can search and filter the dashboard, and change their own password.
- Admins can create and remove other admin accounts. The seeded default password is detected at login and the admin is prompted to change it.

Users, password hashes, and issues are **saved to plain-text files under `data/`** in the working directory, so they survive restarts. Every change is written immediately, and writes are atomic with a `.bak` copy of the previous version.

## Features

- 🔐 User registration and login (Citizen role by default; one seeded Admin account)
- 🔒 Salted **PBKDF2-HMAC-SHA256** password hashing (no plaintext passwords)
- 💾 **File persistence** to `data/` (CSV for users/issues, properties for metadata); changes are saved immediately
- 🛟 Atomic writes with a `.bak` copy, plus timestamped backups of corrupt files
- 📝 Issue reporting with title, description, location, category, and priority
- ✅ Input validation: required fields, length limits, and duplicate active-issue detection
- 📊 Live dashboard with a sortable table view (dates sort chronologically) and summary statistics (Showing X of Y / Pending / In Progress / Resolved)
- 🔎 Live search and filters above the table: free text, status, category, priority, department, and reported-date ranges
- ⌨️ Keyboard shortcuts: `Ctrl+F` focuses search, `Esc` clears all filters
- 🔍 Issue detail view via a dialog popup
- 🔑 Account management:
  - Change your own password (with current-password verification and confirmation)
  - Admins can create additional admin accounts
  - Admins can remove other admins, but not themselves or the last remaining admin
  - Default admin password is detected at login and the admin is prompted to change it
- 🛠️ Admin-only controls:
  - Update issue status
  - Assign a department
  - Update priority
  - Delete an issue
  - View all registered users
  - Create or remove admin accounts
- 🎨 Custom-styled Swing UI (card-style forms, consistent color palette, tabbed auth screen)

## Tech Stack

| Component | Details |
|---|---|
| Language | Java (uses `java.time`, so **Java 8+** required) |
| UI Toolkit | Java Swing (`javax.swing`, `java.awt`) |
| Build | Plain `javac` / any IDE — no build tool (Maven/Gradle) included |
| Persistence | Plain-text files under `data/` (RFC 4180 CSV + `properties`); no database |
| Write safety | Temp file + atomic replace, keeping the previous version as `<file>.bak` |
| Security | Salted PBKDF2-HMAC-SHA256 (120,000 iterations) via the JDK's `SecretKeyFactory` |
| Tests | Plain-Java runner in `tests/TestRunner.java` — no external test dependency |

## Project Structure

```
.
├── src/
│   ├── Main.java              # Application entry point
│   ├── SwingApp.java          # Main JFrame: auth screens + dashboard UI
│   ├── User.java              # Abstract base class (password hashing lives here)
│   ├── Citizen.java           # Citizen role (extends User)
│   ├── Admin.java             # Admin role (extends User)
│   ├── UserManager.java       # User store: registration, account management, login, persistence
│   ├── Issue.java             # Issue data model + console-print helpers
│   ├── IssueManager.java      # Issue store: validation, CRUD, statistics, persistence
│   ├── IssueFilter.java       # Swing-free search/filter rules for the issue table
│   ├── DataStore.java         # File persistence: load/save, backups, corruption handling
│   ├── Csv.java               # Minimal RFC 4180 CSV reader/writer
│   └── DataStoreException.java # Unchecked error raised when data cannot be loaded/saved
├── tests/
│   └── TestRunner.java        # Plain-Java test suite (uses temporary data directories)
├── data/                      # Created at runtime; not committed (see .gitignore)
│   ├── users.csv
│   ├── issues.csv
│   ├── meta.properties
│   └── *.bak                  # Previous version of each file
├── Project.iml                # IntelliJ module definition
└── README.md
```

## Architecture

The project follows a light **MVC-like separation**:

- **Model**: `User` (and subclasses `Citizen`, `Admin`), `Issue`
- **"Service" / data layer**: `UserManager`, `IssueManager` — encapsulate business logic (validation, registration/login, account management, CRUD, statistics, persistence) so the UI layer never manipulates raw lists directly
- **Filtering**: `IssueFilter` — pure, Swing-free matching rules for text, status, category, priority, department, and time range
- **Persistence layer**: `DataStore` (with `Csv` for encoding) — owns `data/`, loads records on construction, and writes after each change
- **View / Controller**: `SwingApp` — builds all Swing components, wires up `ActionListener`s, and calls into the managers

```
Main
 └── DataStore (data/*.csv, data/meta.properties)
      └── SwingApp (JFrame)
           ├── UserManager  ──> List<User>  (Admin, Citizen)
           └── IssueManager ──> List<Issue>
```

Role-based behavior is achieved through **polymorphism**: `User` is abstract with an abstract `getRole()` method, overridden by `Citizen` ("Citizen") and `Admin` ("Admin"). `SwingApp` checks `currentUser.getRole().equals("Admin")` (via the `isAdmin()` helper) to conditionally render admin-only controls and to decide whether the dashboard shows all issues or just the current user's issues.

## Class Reference

### `Main`
Application entry point. Creates the shared `DataStore` for `data/`, constructs `SwingApp`, shows it on the Event Dispatch Thread, and displays any startup data warnings. If the data directory cannot be created or the app cannot start, it shows an error dialog and exits.

### `User` (abstract)
Base class holding `username`, `name`, and a salted PBKDF2-HMAC-SHA256 hash of the password (never the plaintext), plus the salt and iteration count needed to verify it. `checkPassword(String)` re-hashes the candidate with the stored salt and iteration count and compares the results in constant time. A second constructor restores those values directly from disk without re-hashing. Also declares the abstract `getRole()` that subclasses must implement.

### `Citizen` / `Admin`
Concrete `User` subclasses. Each simply returns its role string (`"Citizen"` / `"Admin"`) and provides a package-private constructor for restoring stored credentials.

### `UserManager`
User repository backed by `DataStore`.
- Loads `data/users.csv` on construction.
- Seeds one default `Admin` (`admin` / `admin123`) only when no admin account exists; if the username `admin` is already taken, it uses `admin2`, `admin3`, etc. Fresh installations therefore always have exactly one admin.
- `validateRegistration(username, password, name)` — returns a human-readable error message, or `null` if the input is valid. Enforces a 3–30 character username (letters, numbers, `.`, `_`, `-`), a 6–100 character password, a non-empty name of at most 60 characters, and username uniqueness.
- `register(username, password, name)` — validates, creates a new `Citizen`, and saves immediately. If the save fails, the in-memory addition is rolled back and a `DataStoreException` is thrown.
- `validateAdminCreation` / `createAdmin` — apply the same account rules, reject the public default password, create an `Admin`, and save immediately (rolling back on failure).
- `validateAdminRemoval` / `removeAdmin` — enforce that the actor is an admin, the target is an admin, the actor is not removing themselves, and at least one admin remains.
- `validatePasswordChange` / `changePassword` — verify the current password, enforce the 6–100 character rule and a changed value, reject the default admin password for admins, re-hash with a fresh salt, save immediately, and restore the old credentials if the save fails.
- `isUsingDefaultAdminPassword(user)` — detects an admin still using the seeded demo password so the UI can prompt for a change.
- `login(username, password)` — validates credentials and returns the matching `User`, or `null`.
- `findByUsername(username)` — case-insensitive lookup.
- `getAllUsers()` — returns a defensive copy of the user list.
- `printAllUsers()` — console debug helper.

### `Issue`
Data model for a reported issue.
- The `id` is assigned by `IssueManager` when the issue is added. IDs start at 1, continue across restarts, and are never reused.
- Fields: `title`, `description`, `location`, `category`, `priority`, `status`, `department`, `reportedBy`, `dateReported` (auto-stamped `dd-MM-yyyy HH:mm` at creation).
- New issues default to `status = "Pending"` and `department = "Unassigned"`.
- Static option lists used to populate UI dropdowns/combo boxes:
  - `CATEGORIES`: Road, Electricity, Water, Sanitation, Other
  - `PRIORITIES`: Low, Medium, High, Critical
  - `STATUSES`: Pending, In Progress, Resolved
  - `DEPARTMENTS`: Roads Dept, Electricity Dept, Water Dept, Sanitation Dept, General Dept
- `printSummary()` / `printFull()` — formatted console debug helpers (not used by the Swing UI).

### `IssueManager`
Issue repository backed by `DataStore`.
- Loads `data/issues.csv` and the next issue ID on construction. The next ID is `max(stored next ID, highest loaded ID + 1)`, so a missing/stale `meta.properties` cannot cause ID reuse.
- `validateIssue(title, description, location, reportedBy)` — returns a human-readable error message, or `null` if valid. Requires all three text fields, caps title/description/location at 100/1000/150 characters, and rejects a duplicate *active* issue (same reporter, title, and location while status is Pending or In Progress).
- `addIssue(Issue)` — validates, assigns the next ID, and saves immediately; returns `false` if validation fails. The counter is written first: if that write fails, the issue and counter are rolled back; if the issue write then fails, the issue is rolled back but the persisted ID stays consumed, so IDs are never reused.
- `getById(id)`, `getAllIssues()` (defensive copy), `getByUser(username)`
- `updateStatus`, `assignDepartment`, `updatePriority`, `deleteIssue` — validate the new value, save immediately, and roll back the affected issue if the save fails. All return `boolean` success/failure based on whether the issue was found.
- `printStatistics()` — console summary (total / pending / in progress / resolved / critical)
- `printList(List<Issue>)` — console listing helper

### `IssueFilter`
Swing-free filtering rules used by the dashboard.
- Free-text search is case-insensitive and covers title, description, location, and reporter.
- Dropdown filters for status, category, priority, and department (including `Unassigned`).
- Time presets: Any time, Today, Last 7 days (today plus the previous six), Last 30 days.
- `matches(issue)` evaluates all active conditions with AND; `apply(issues)` returns a new filtered list in the original order.
- `All` / `Any time` values disable the corresponding condition.

### `DataStore`
Owns the `data/` directory and all file I/O.
- `loadUsers()` / `loadIssues()` — parse and validate `users.csv` / `issues.csv`, restoring stored credentials and issue fields exactly.
- `loadNextIssueId(issues)` — reads `meta.properties` (`formatVersion`, `nextIssueId`) and repairs inconsistencies from the loaded issues.
- `saveUsers(users)` / `saveIssues(issues)` / `saveNextIssueId(id)` — write UTF-8 content to a temporary file in `data/`, copy the current file to `<file>.bak`, then atomically replace the original (falling back to a normal replace where atomic moves are unsupported).
- **Corrupt file handling** — renames an unreadable `users.csv` / `issues.csv` to `<file>.corrupt-<yyyyMMdd-HHmmss>` (adding `-1`, `-2`, … on collision), records a warning, and starts that dataset fresh. An unreadable `meta.properties` produces a warning instead, and the ID counter is rebuilt from the loaded issues.
- `getWarnings()` — warnings collected during loading, shown once at startup.
- Throws `DataStoreException` when the data directory cannot be created or a save cannot be completed; unreadable CSV files are quarantined and reported through warnings instead.

### `Csv`
Small RFC 4180 reader/writer used by `DataStore`. Handles quoted fields, escaped quotes (`""`), commas, multiline values, CRLF/LF, UTF-8 BOMs, and blank lines. The writer quotes any value containing a comma, quote, newline, or leading/trailing whitespace.

### `DataStoreException`
Unchecked exception raised when persisted data cannot be loaded or saved, so the UI can report the failure without forcing `throws` clauses through every manager method.

### `SwingApp`
The single `JFrame` that drives the entire UI.
- Receives the shared `DataStore` and injects it into `UserManager` and `IssueManager`.
- `showStartupWarnings()` — displays any data warnings (for example, a corrupt file that was backed up) after the window opens.
- **Auth screen** (`showAuthPanel`): tabbed pane with `Login` and `Register` forms, built with reusable `createFormPanel` / `addAuthField` / `addAuthButton` helpers for consistent styling.
- **Dashboard** (`showDashboard`): header with welcome message + live stats, a filter bar, a `JTable` of issues (columns: ID, Title, Category, Priority, Status, Department, Location, Reported By, Date), and an action bar.
- **Filter bar** (`createFilterBar`): search field, status/category/priority/department dropdowns, time-range presets, and *Clear Filters*. Filtering is applied live to the role-scoped issue list, and the stats show filtered counts plus `Showing X of Y`.
- **Keyboard shortcuts**: `Ctrl+F` focuses the search box; `Esc` clears all filters.
- **Account controls**: *Change Password* is available to both roles next to *Logout*. Admins see *Users*, which opens a dialog with *Create Admin* and *Remove Admin* actions.
- **Action bar** (`createActionPanel`): shows *View Details* and *Refresh* for everyone; *Report Issue* only for citizens; and *Update Status*, *Assign Department*, *Update Priority*, *Delete*, and *Users* for admins (when `isAdmin()` is true).
- Issue creation, detail viewing, status/priority/department updates, deletion, account management, and password changes are all handled via `JOptionPane` dialogs built from small `JTextField`/`JComboBox`/`JTextArea` forms.
- If a save fails, the action is rolled back and a **Save Error** dialog explains that the change was not applied.
- After login, an admin still using the seeded default password is offered an immediate password change.

## Getting Started

### Prerequisites
- JDK 8 or later installed and on your `PATH` (verify with `java -version` / `javac -version`)

### Build & Run (command line)

```bash
# From the project root, compile all sources into an output directory
javac -d out src/*.java

# Run the compiled application
java -cp out Main

# Compile and run the test suite
javac -cp out -d out tests/*.java
java -cp out TestRunner
```

> **Windows PowerShell:** native commands don't expand `*.java`, so pass the files explicitly, e.g. `javac -d out (Get-ChildItem src -Filter *.java | ForEach-Object FullName)`.

On first run the app creates a `data/` directory **in the directory you launch it from** (the project root if you use the commands above). Run the app from the same directory each time to keep using the same data. The test suite always uses temporary directories, so it never touches `data/`.

### Build & Run (IDE)
1. Import the folder as a plain Java project (no Maven/Gradle config needed) in IntelliJ IDEA, Eclipse, or VS Code + Java Extension Pack.
2. Mark `src` as a source root (all `.java` files there are in the default package). The `tests` folder can be marked as a test source root.
3. Run `Main.java`.

## Usage Guide

1. **Launch** the app — you'll land on the Login/Register screen.
2. **Register** as a citizen (full name, a 3–30 character username, and a password of at least 6 characters), or **log in** with the seeded admin account.
3. **Filter the dashboard**:
   - Type in **Search** to match title, description, location, or reporter.
   - Use the **Status**, **Category**, **Priority**, and **Department** dropdowns to narrow the table. Department includes **Unassigned**.
   - Use **Reported** for Any time / Today / Last 7 days / Last 30 days.
   - Filters combine with AND and update the table live. **Clear Filters** (or `Esc`) resets them; `Ctrl+F` jumps to search.
4. **As a Citizen**:
   - Click **Report Issue**, fill in the title, description, location, category, and priority, and submit. All three text fields are required, and a duplicate active issue (same title and location) is rejected.
   - Your dashboard table and summary counts only show issues *you* reported; filters apply within that scope.
   - Select a row and click **View Details** to see the full record.
5. **As an Admin**:
   - The dashboard shows *every* issue reported by any citizen, plus live counts.
   - Admins cannot report issues; they manage the ones citizens submit.
   - Select an issue and use **Update Status**, **Assign Department**, or **Update Priority** to manage it.
   - Use **Delete** to remove an issue (with confirmation).
   - Use **Users** to view every registered account and its role. From that dialog, **Create Admin** adds another admin, and **Remove Admin** deletes another admin account (not yourself or the last admin).
6. **Change your password** with the **Change Password** button next to **Logout**. Enter the current password, the new password, and the confirmation. New passwords must be 6–100 characters and different from the current one. Admins cannot use the default `admin123`.
7. Click **Logout** at any time to return to the auth screen.

Every successful registration, report, update, delete, admin change, or password change is written to `data/` immediately. Closing the app and relaunching from the same directory restores the same users, password hashes, issues, and issue-ID counter.

## Default Admin Account

Seeded automatically **only when no admin account exists** (for example, on the very first run):

| Username | Password |
|---|---|
| `admin` | `admin123` |

If the `admin` username is already taken by a non-admin account (possible only if the data files were edited by hand), the app seeds `admin2`, `admin3`, etc. instead.

When an admin logs in with the seeded password, the app detects it and offers an immediate **Change Password** dialog. Admins cannot set their password back to `admin123`, and new admin accounts cannot be created with it.

> ⚠️ The seeded credential is still hardcoded and public; the login prompt is a mitigation, not a replacement for changing the password before any real deployment. See [Known Limitations](#known-limitations).

## Data Persistence

State is stored as **plain-text files under `data/`** and is written after **every change**:

| File | Contents |
|---|---|
| `data/users.csv` | Role, username, name, PBKDF2 hash, salt, iteration count |
| `data/issues.csv` | ID, title, description, location, category, priority, status, department, reporter, original date |
| `data/meta.properties` | `formatVersion` and `nextIssueId` |

### File formats

`data/users.csv` — one row per account (hash and salt are Base64-encoded):

```csv
role,username,name,passwordHash,salt,iterations
Admin,admin,System Admin,<base64 hash>,<base64 salt>,120000
Citizen,alice,Alice Smith,<base64 hash>,<base64 salt>,120000
```

`data/issues.csv` — one row per issue; commas, quotes, and newlines are quoted:

```csv
id,title,description,location,category,priority,status,department,reportedBy,dateReported
1,"Pothole, large","Deep pothole
near the school","Main St",Road,High,Pending,Unassigned,alice,25-10-2026 14:30
```

`data/meta.properties`:

```properties
formatVersion=1
nextIssueId=2
```

Files are written as UTF-8. Previous versions appear as `users.csv.bak`, `issues.csv.bak`, and `meta.properties.bak`; unreadable user/issue files are preserved as `<file>.corrupt-<timestamp>`.

- **What survives:** registered users (including added admins and changed passwords), password hashes, issues and all their fields, and the issue-ID counter.
- **What does not survive:** the login session (the app always starts at the Login/Register screen) and window size/position.
- **Passwords:** only salted PBKDF2-HMAC-SHA256 hashes are written — never plaintext.
- **CSV format:** RFC 4180 quoting, so commas, quotes, and multiline descriptions round-trip correctly. Files are UTF-8.
- **Write safety:** each save writes a temporary file, copies the previous version to `<file>.bak`, then atomically replaces the original. The `.bak` file is for **manual** recovery only; it is never restored automatically.
- **Corrupt data:** if `users.csv` or `issues.csv` cannot be parsed, it is renamed to `<file>.corrupt-<timestamp>` and that dataset starts fresh. A corrupt or invalid `meta.properties` is ignored with a warning, and issue IDs are rebuilt from the existing issues.
- **Missing files:** a missing `users.csv` seeds the default admin; a missing `issues.csv` starts with no issues.
- **Save failures:** the change is rolled back in memory and a **Save Error** dialog is shown, so the UI never claims success for data that was not written.

> The app assumes **only one instance runs at a time**. There is no file locking; if two copies run against the same `data/` directory, the last save wins.

## Known Limitations

- The seeded admin credential is hardcoded and public; it must be changed or removed before any real deployment.
- Passwords use the JDK's PBKDF2 implementation with a per-user salt; a dedicated library (Argon2id/bcrypt) and full password policy/rotation are preferable for production.
- Single-user desktop app — no networking/multi-client support.
- Single process at a time — there is no file locking, so two concurrent instances can overwrite each other's changes.
- No encryption at rest: issues, names, usernames, and password hashes/salts are stored in plain-text CSV, protected only by OS file permissions.
- Account management is intentionally limited: users can change their own password, and admins can create/remove admins. There is no forgotten-password reset, citizen deletion, or additional role type.
- There is no audit trail for account changes or issue updates, and no comments/timeline on issues.
- Duplicate detection is intentionally narrow: same reporter + exact title + exact location while the issue is still active.
- When a save fails after the ID counter has been written, one issue ID may be skipped (never reused).
- Data lives in the working directory's `data/` folder; launching from a different directory starts with a different dataset.
- `.bak` files are never restored automatically — recovery is manual.
- Tests cover the CSV/store/manager/filter/account-rule layers, not the Swing UI.

## Possible Improvements

- Move to a real database (SQLite/H2) or JSON with schema migration if the dataset grows
- Use a dedicated password-hashing library (Argon2id/bcrypt) and add a forgotten-password reset flow
- Add a first-run setup wizard instead of seeding a known admin credential
- Add file locking (or a single-instance guard) to protect against concurrent runs
- Provide an in-app "restore from .bak" action instead of manual file recovery
- Encrypt issue data at rest if it may contain sensitive details
- Add issue comments and an audit trail of status/priority/department changes
- Add CSV export and in-app backups
- Expand the test suite to cover Swing UI flows
- Migrate to a build tool (Maven/Gradle) for dependency and packaging management
- Add email/SMS notifications when an issue's status changes
- Add additional roles (for example, department staff) and citizen account deletion
