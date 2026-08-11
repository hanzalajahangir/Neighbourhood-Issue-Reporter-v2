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
- **Admins** can see every issue in the system, update its status/priority/department, delete issues, and view the full list of registered users.

All data (users and issues) is held **in memory** for the lifetime of the running application — there is no database or file persistence layer.

## Features

- 🔐 User registration and login (Citizen role by default; one seeded Admin account)
- 📝 Issue reporting with title, description, location, category, and priority
- 📊 Live dashboard with a sortable table view and summary statistics (Total / Pending / In Progress / Resolved)
- 🔍 Issue detail view via a dialog popup
- 🛠️ Admin-only controls:
  - Update issue status
  - Assign a department
  - Update priority
  - Delete an issue
  - View all registered users
- 🎨 Custom-styled Swing UI (card-style forms, consistent color palette, tabbed auth screen)

## Tech Stack

| Component | Details |
|---|---|
| Language | Java (uses `java.time`, so **Java 8+** required) |
| UI Toolkit | Java Swing (`javax.swing`, `java.awt`) |
| Build | Plain `javac` / any IDE — no build tool (Maven/Gradle) included |
| Persistence | In-memory (`ArrayList`) — no database |

## Project Structure

```
.
├── Main.java            # Application entry point
├── SwingApp.java         # Main JFrame: auth screens + dashboard UI
├── User.java             # Abstract base class for all user types
├── Citizen.java           # Citizen role (extends User)
├── Admin.java             # Admin role (extends User)
├── UserManager.java       # In-memory user store, registration & login logic
├── Issue.java             # Issue data model + console-print helpers
└── IssueManager.java      # In-memory issue store, CRUD & statistics logic
```

## Architecture

The project follows a light **MVC-like separation**:

- **Model**: `User` (and subclasses `Citizen`, `Admin`), `Issue`
- **"Service" / data layer**: `UserManager`, `IssueManager` — encapsulate all business logic (registration, login, CRUD, filtering, statistics) so the UI layer never manipulates raw lists directly
- **View / Controller**: `SwingApp` — builds all Swing components, wires up `ActionListener`s, and calls into the managers

```
Main
 └── SwingApp (JFrame)
      ├── UserManager  ──> List<User>  (Admin, Citizen)
      └── IssueManager ──> List<Issue>
```

Role-based behavior is achieved through **polymorphism**: `User` is abstract with an abstract `getRole()` method, overridden by `Citizen` ("Citizen") and `Admin` ("Admin"). `SwingApp` checks `currentUser.getRole().equals("Admin")` (via the `isAdmin()` helper) to conditionally render admin-only controls and to decide whether the dashboard shows all issues or just the current user's issues.

## Class Reference

### `Main`
Application entry point. Launches `SwingApp` on the Event Dispatch Thread via `SwingUtilities.invokeLater`.

### `User` (abstract)
Base class holding `username`, `password`, and `name`. Provides `checkPassword(String)` and the abstract `getRole()` that subclasses must implement.

### `Citizen` / `Admin`
Concrete `User` subclasses. Each simply returns its role string (`"Citizen"` / `"Admin"`).

### `UserManager`
In-memory user repository.
- Seeds one default `Admin` on construction.
- `register(username, password, name)` — creates a new `Citizen`; fails if the username is taken.
- `login(username, password)` — validates credentials and returns the matching `User`, or `null`.
- `findByUsername(username)` — case-insensitive lookup.
- `getAllUsers()` — returns a defensive copy of the user list.
- `printAllUsers()` — console debug helper.

### `Issue`
Data model for a reported issue.
- Auto-incrementing static `id` counter (starts at 1, shared across all instances).
- Fields: `title`, `description`, `location`, `category`, `priority`, `status`, `department`, `reportedBy`, `dateReported` (auto-stamped `dd-MM-yyyy HH:mm` at creation).
- New issues default to `status = "Pending"` and `department = "Unassigned"`.
- Static option lists used to populate UI dropdowns/combo boxes:
  - `CATEGORIES`: Road, Electricity, Water, Sanitation, Other
  - `PRIORITIES`: Low, Medium, High, Critical
  - `STATUSES`: Pending, In Progress, Resolved
  - `DEPARTMENTS`: Roads Dept, Electricity Dept, Water Dept, Sanitation Dept, General Dept
- `printSummary()` / `printFull()` — formatted console output (used for CLI-style debugging; not called by the Swing UI directly).

### `IssueManager`
In-memory issue repository.
- `addIssue(Issue)`, `getById(id)`, `getAllIssues()`, `getByUser(username)`
- `updateStatus`, `assignDepartment`, `updatePriority`, `deleteIssue` — all return `boolean` success/failure based on whether the issue was found
- `printStatistics()` — console summary (total / pending / in progress / resolved / critical)
- `printList(List<Issue>)` — console listing helper

### `SwingApp`
The single `JFrame` that drives the entire UI.
- **Auth screen** (`showAuthPanel`): tabbed pane with `Login` and `Register` forms, built with reusable `createFormPanel` / `addAuthField` / `addAuthButton` helpers for consistent styling.
- **Dashboard** (`showDashboard`): header with welcome message + live stats, a `JTable` of issues (columns: ID, Title, Category, Priority, Status, Department, Location, Reported By, Date), and an action bar.
- **Action bar** (`createActionPanel`): always shows *Report Issue*, *View Details*, *Refresh*; additionally shows *Update Status*, *Assign Department*, *Update Priority*, *Delete*, and *Users* when `isAdmin()` is true.
- Issue creation, detail viewing, status/priority/department updates, deletion, and user listing are all handled via `JOptionPane` dialogs built from small `JTextField`/`JComboBox`/`JTextArea` forms.

## Getting Started

### Prerequisites
- JDK 8 or later installed and on your `PATH` (verify with `java -version` / `javac -version`)

### Build & Run (command line)

```bash
# From the project root, compile all sources into an output directory
javac -d out *.java

# Run the compiled application
java -cp out Main
```

### Build & Run (IDE)
1. Import the folder as a plain Java project (no Maven/Gradle config needed) in IntelliJ IDEA, Eclipse, or VS Code + Java Extension Pack.
2. Mark the project source root appropriately (all `.java` files are in the default/root package).
3. Run `Main.java`.

## Usage Guide

1. **Launch** the app — you'll land on the Login/Register screen.
2. **Register** as a citizen (full name, username, password), or **log in** with the seeded admin account.
3. **As a Citizen**:
   - Click **Report Issue**, fill in the title, description, location, category, and priority, and submit.
   - Your dashboard table only shows issues *you* reported.
   - Select a row and click **View Details** to see the full record.
4. **As an Admin**:
   - The dashboard shows *every* issue reported by any citizen, plus live counts.
   - Select an issue and use **Update Status**, **Assign Department**, or **Update Priority** to manage it.
   - Use **Delete** to remove an issue (with confirmation).
   - Use **Users** to view every registered account and its role.
5. Click **Logout** at any time to return to the auth screen.

## Default Admin Account

Seeded automatically each time the app starts (see `UserManager` constructor):

| Username | Password |
|---|---|
| `admin` | `admin123` |

> ⚠️ This is a hardcoded demo credential with a plaintext password check — see [Known Limitations](#known-limitations).

## Data Persistence

**None.** All users and issues live in `ArrayList`s inside `UserManager` and `IssueManager`. Everything is lost when the application is closed; every run starts fresh with only the default admin account and zero issues.

## Known Limitations

- No persistence layer (database, file, or serialization) — data doesn't survive a restart.
- Passwords are stored and compared in **plain text** (`User.checkPassword`) — not suitable for any real deployment.
- No input validation beyond "required field" checks (e.g., no length limits, no duplicate-issue detection).
- Single-user desktop app — no networking/multi-client support.
- Issue `id` counter is a `static int` on `Issue`, so IDs are not guaranteed unique across separate runs/imports if persistence were later added naively.

## Possible Improvements

- Persist users/issues to a file (JSON/CSV) or embedded database (SQLite/H2)
- Hash passwords (e.g., BCrypt) instead of storing them in plain text
- Add search/filter/sort controls to the issue table
- Add unit tests for `UserManager` and `IssueManager`
- Migrate to a build tool (Maven/Gradle) for dependency and packaging management
- Add email/SMS notifications when an issue's status changes
