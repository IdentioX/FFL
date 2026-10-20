import java.sql.*;
import java.util.Properties;
public final class DriverProbe {
  public static void main(String[] args) {
    System.setProperty("mariadb.logging.disable", "true");
    for (String name : new String[]{"org.mariadb.jdbc.Driver", "com.mysql.cj.jdbc.Driver"}) {
      try {
        Driver d = (Driver) Class.forName(name).getDeclaredConstructor().newInstance();
        Properties p = new Properties();
        p.setProperty("user", "fixture_unused");
        p.setProperty("password", "fixture_unused");
        p.setProperty("connectTimeout", "500");
        p.setProperty("socketTimeout", "500");
        p.setProperty(name.startsWith("org.") ? "sslMode" : "sslMode", name.startsWith("org.") ? "verify-full" : "VERIFY_IDENTITY");
        p.setProperty("logger", "com.mysql.cj.log.NullLogger");
        String url = name.startsWith("org.") ? "jdbc:mariadb://127.0.0.1:1/fixture" : "jdbc:mysql://127.0.0.1:1/fixture";
        try (Connection c = d.connect(url, p)) {
          System.out.println(name + ": UNEXPECTED_CONNECTION");
        } catch (SQLException e) {
          System.out.println(name + ": LOADED; refusal handled as SQLException; state=" + e.getSQLState());
        }
      } catch (Throwable e) {
        System.out.println(name + ": FAILURE " + e.getClass().getName());
        e.printStackTrace(System.out);
      }
    }
  }
}
