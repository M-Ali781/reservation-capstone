package com.example.util;

import com.example.model.*;
import org.hibernate.CacheMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import javax.persistence.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public class PerformanceReport {
    private final EntityManagerFactory emf;
    private final SessionFactory sf;
    private final List<String> lignes=new ArrayList<>();
    public PerformanceReport(EntityManagerFactory emf) { this.emf=emf; this.sf=emf.unwrap(SessionFactory.class); }
    private void mesurer(String nom,Consumer<EntityManager> action) {
        Statistics stats=sf.getStatistics(); stats.clear();
        long start=System.nanoTime();
        Transactions.executer(emf,em -> { action.accept(em); return null; });
        double ms=(System.nanoTime()-start)/1_000_000.0;
        String ligne=String.format(Locale.ROOT,"%s;%d;%d;%d;%d;%d;%d;%d;%.3f",nom,stats.getPrepareStatementCount(),stats.getQueryExecutionCount(),stats.getSecondLevelCacheHitCount(),stats.getSecondLevelCacheMissCount(),stats.getSecondLevelCachePutCount(),stats.getQueryCacheHitCount(),stats.getTransactionCount(),ms);
        lignes.add(ligne); System.out.println(ligne);
    }
    public void printReport() throws java.io.IOException {
        lignes.clear();
        String header="Scenario;SQL;Requetes JPQL;L2 hits;L2 misses;L2 puts;Query hits;Transactions;Duree ms";
        lignes.add(header); System.out.println("=== RAPPORT DE PERFORMANCE ===\n"+header);
        for(String mode:Arrays.asList("N+1 sans cache","JOIN FETCH","Entity Graph")) {
            mesurer(mode,em -> {
                em.unwrap(Session.class).setCacheMode(CacheMode.IGNORE);
                String jpql=mode.equals("JOIN FETCH")?"SELECT r FROM Reservation r JOIN FETCH r.salle JOIN FETCH r.utilisateur":"SELECT r FROM Reservation r";
                TypedQuery<Reservation> query=em.createQuery(jpql,Reservation.class);
                if(mode.equals("Entity Graph")) query.setHint("javax.persistence.fetchgraph",em.getEntityGraph("Reservation.details"));
                for(Reservation r:query.getResultList()) { r.getSalle().getNom(); r.getUtilisateur().getNom(); }
            });
        }
        sf.getCache().evictAllRegions();
        Long salleId=Transactions.executer(emf,em -> em.createQuery("SELECT MIN(s.id) FROM Salle s",Long.class).getSingleResult());
        mesurer("L2 lecture froide",em -> em.find(Salle.class,salleId).getNom());
        mesurer("L2 lecture chaude",em -> em.find(Salle.class,salleId).getNom());
        sf.getCache().evictAllRegions();
        for(String mode:Arrays.asList("JPQL cache froid","JPQL cache chaud")) mesurer(mode,em -> em.createQuery("SELECT s FROM Salle s ORDER BY s.id",Salle.class).setHint("org.hibernate.cacheable",true).getResultList());
        Path rapport=Paths.get("target","rapport-performance.csv");
        Files.createDirectories(rapport.getParent()); Files.write(rapport,lignes,StandardCharsets.UTF_8);
        System.out.println("Rapport : "+rapport.toAbsolutePath());
        System.out.println("Durees indicatives, mesures sequentielles dans des EntityManager distincts.");
    }
}
