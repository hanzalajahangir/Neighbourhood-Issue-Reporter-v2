public class Admin extends User {

    public Admin(String username, String password, String name) {
        super(username, password, name);
    }

    Admin(String username, String name, String passwordHash, String salt, int iterations) {
        super(username, name, passwordHash, salt, iterations);
    }

    @Override
    public String getRole() { return "Admin"; }
}
