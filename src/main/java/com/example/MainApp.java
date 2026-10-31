package com.example;

import com.example.model.*;
import com.example.service.*;
import com.example.util.*;
import javax.persistence.*;
import java.time.*;
import java.util.logging.*;

public class MainApp {
    private static final Logger LOG=Logger.getLogger(MainApp.class.getName());
    public static void main(String[] args) throws Exception {
        String url="jdbc:h2:mem:capstone;DB_CLOSE_DELAY=-1";
        EntityManagerFactory emf=null;
        try {
            emf=JpaUtil.ouvrir(url,true);
            new DataInitializer(emf).initData();
            ReservationService service=new ReservationService(emf);
            System.out.println("=== Jeu de donnees : 3 utilisateurs, 5 salles, 15 reservations ===");
            service.lister().forEach(System.out::println);
            LocalDateTime debut=LocalDate.now().plusDays(1).atTime(14,0);
            Reservation exemple=service.lister().get(0);
            Long salle=exemple.getSalle().getId(), user=exemple.getUtilisateur().getId();
            System.out.println("=== Creation et detection de chevauchement ===");
            Reservation creee=service.creer(salle,user,debut,debut.plusHours(1));
            try { service.creer(salle,user,debut.plusMinutes(30),debut.plusHours(2)); }
            catch(ConflitReservationException e) { System.out.println("Conflit attendu : "+e.getMessage()); }
            Reservation modifiee=service.modifier(creee.getId(),creee.getVersion(),debut.plusHours(1),debut.plusHours(2));
            try { service.modifier(creee.getId(),creee.getVersion(),debut.plusHours(2),debut.plusHours(3)); }
            catch(OptimisticLockException e) { System.out.println("Conflit optimiste attendu : "+e.getMessage()); }
            service.supprimer(modifiee.getId(),modifiee.getVersion());
            System.out.println("=== Recherche JPQL ===");
            service.rechercherJpql("Atlas",LocalDateTime.now()).forEach(System.out::println);
            System.out.println("=== Recherche Criteria ===");
            service.rechercherCriteria("Atlas",LocalDateTime.now()).forEach(System.out::println);
            System.out.println("Salles libres a 14h : "+service.disponibles(debut,debut.plusHours(1)).size());
            new PerformanceReport(emf).printReport();
            new MigrationExecutor(url,"sa","").migrate();
            LOG.info("Application terminee avec succes");
        } catch(Exception e) { LOG.log(Level.SEVERE,"Echec de l'application",e); throw e; }
        finally { if(emf!=null) emf.close(); }
    }
}
