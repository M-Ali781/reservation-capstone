package com.example.util;
import javax.persistence.*;
import java.util.*;

public final class JpaUtil {
    private JpaUtil() { }
    public static EntityManagerFactory ouvrir(String url, boolean sql) throws Exception {
        // Migrer avant la validation du modele par Hibernate.
        new MigrationExecutor(url, "sa", "").migrate();
        Map<String, Object> props = new HashMap<>();
        props.put("javax.persistence.jdbc.url", url);
        props.put("hibernate.show_sql", Boolean.toString(sql));
        return Persistence.createEntityManagerFactory("ReservationPU", props);
    }
}
