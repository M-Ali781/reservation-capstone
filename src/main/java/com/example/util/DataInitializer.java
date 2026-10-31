package com.example.util;
import com.example.model.*;
import javax.persistence.EntityManagerFactory;
import java.time.*;

public class DataInitializer {
    private final EntityManagerFactory emf;
    private final Clock clock;
    public DataInitializer(EntityManagerFactory emf) { this(emf,Clock.systemDefaultZone()); }
    public DataInitializer(EntityManagerFactory emf,Clock clock) { this.emf=emf; this.clock=clock; }
    public void initData() {
        Transactions.executer(emf,em -> {
            if(em.createQuery("SELECT COUNT(s) FROM Salle s",Long.class).getSingleResult()>0) return null;
            String[] noms={"Atlas","Menara","Toubkal","Oasis","Medina"};
            Utilisateur[] users={new Utilisateur("Ali Mahraoui","ali@example.com"),new Utilisateur("Salma Amrani","salma@example.com"),new Utilisateur("Youssef Alami","youssef@example.com")};
            for(Utilisateur user:users) em.persist(user);
            LocalDateTime demain=LocalDate.now(clock).plusDays(1).atTime(9,0);
            for(int i=0;i<noms.length;i++) {
                Salle salle=new Salle(noms[i],10+i*10,"Salle equipee : projecteur et connexion Wi-Fi"); em.persist(salle);
                for(int jour=0;jour<3;jour++) em.persist(new Reservation(salle,users[(i+jour)%users.length],demain.plusDays(jour),demain.plusDays(jour).plusHours(2)));
            }
            return null;
        });
    }
}
