public class Citizen extends User {

    public Citizen(String username, String password, String name) {
        super(username, password, name);
    }

    Citizen(String username, String name, String passwordHash, String salt, int iterations) {
        super(username, name, passwordHash, salt, iterations);
    }

    @Override
    public String getRole() { return "Citizen"; }
}
