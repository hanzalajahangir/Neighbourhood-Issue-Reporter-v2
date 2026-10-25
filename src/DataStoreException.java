public class DataStoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DataStoreException(String message) {
        super(message);
    }

    public DataStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
