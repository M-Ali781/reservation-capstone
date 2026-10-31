package com.example;

import com.example.model.*;
import com.example.service.*;
import com.example.util.*;
import org.junit.jupiter.api.*;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import javax.persistence.*;
import java.time.*;
import java.sql.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class ReservationTest {
    private EntityManagerFactory emf;
    private ReservationService service;
    private String url;
    private Long salle,user;
    private final LocalDateTime debut=LocalDateTime.of(2026,11,12,14,0);
    @BeforeEach void init() throws Exception {
        url="jdbc:h2:mem:test_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
        emf=JpaUtil.ouvrir(url,false);
        new DataInitializer(emf,Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"),ZoneOffset.UTC)).initData();
        service=new ReservationService(emf);
        Reservation premiere=service.lister().get(0);
        salle=premiere.getSalle().getId(); user=premiere.getUtilisateur().getId();
    }
    @AfterEach void close() { if(emf!=null) emf.close(); }
    @Test void crudEtVersion() {
        Reservation r=service.creer(salle,user,debut,debut.plusHours(1));
        assertNotNull(r.getId()); assertEquals(0,r.getVersion());
        Reservation modifiee=service.modifier(r.getId(),0,debut.plusHours(1),debut.plusHours(2));
        assertEquals(1,modifiee.getVersion());
        assertThrows(OptimisticLockException.class,()->service.modifier(r.getId(),0,debut,debut.plusHours(1)));
        assertThrows(OptimisticLockException.class,()->service.supprimer(r.getId(),0));
        service.supprimer(r.getId(),1);
        assertEquals(15,service.lister().size());
    }
    @Test void chevauchementBornesEtModificationRefusee() {
        Reservation r=service.creer(salle,user,debut,debut.plusHours(1));
        assertThrows(ConflitReservationException.class,()->service.creer(salle,user,debut.plusMinutes(30),debut.plusHours(2)));
        Reservation suivante=service.creer(salle,user,debut.plusHours(1),debut.plusHours(2));
        assertThrows(ConflitReservationException.class,()->service.modifier(suivante.getId(),0,debut.plusMinutes(45),debut.plusHours(2)));
        Reservation relue=service.lister().stream().filter(x->x.getId().equals(suivante.getId())).findFirst().get();
        assertEquals(debut.plusHours(1),relue.getDateDebut()); assertEquals(0,relue.getVersion());
        assertFalse(service.disponibles(debut,debut.plusHours(1)).stream().anyMatch(s->s.getId().equals(salle)));
        assertNotNull(r.getId());
    }
    @Test void parametresInvalidesSansEcriture() {
        assertThrows(IllegalArgumentException.class,()->service.creer(salle,user,debut,debut));
        assertThrows(IllegalArgumentException.class,()->service.creer(999L,user,debut,debut.plusHours(1)));
        assertThrows(IllegalArgumentException.class,()->service.creer(salle,999L,debut,debut.plusHours(1)));
        assertEquals(15,service.lister().size());
    }
    @Test void jpqlEtCriteriaRetournentLesMemesReservations() {
        List<Long> jpql=service.rechercherJpql("Atlas",debut.minusDays(1)).stream().map(Reservation::getId).collect(Collectors.toList());
        List<Reservation> criteria=service.rechercherCriteria("Atlas",debut.minusDays(1));
        assertEquals(3,jpql.size());
        assertEquals(jpql,criteria.stream().map(Reservation::getId).collect(Collectors.toList()));
        assertEquals("Atlas",criteria.get(0).getSalle().getNom());
        assertNotNull(criteria.get(0).getUtilisateur().getEmail());
        assertEquals(15,service.rechercherCriteria(null,null).size());
    }
    @Test void migrationsRejouablesEtDonneesConservees() throws Exception {
        new MigrationExecutor(url,"sa","").migrate();
        assertEquals(15,service.lister().size());
        try(Connection c=DriverManager.getConnection(url,"sa",""); Statement s=c.createStatement()) {
            try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM schema_history")) { r.next(); assertEquals(2,r.getInt(1)); }
            try(ResultSet r=s.executeQuery("SELECT description FROM salles")) { assertTrue(r.next()); assertNotNull(r.getString(1)); }
        }
        assertThrows(java.io.FileNotFoundException.class,()->new MigrationExecutor(url,"sa","").executeMigration("absent.sql"));
        assertThrows(PersistenceException.class,()->Transactions.executer(emf,em->{em.persist(new Utilisateur("Doublon","ali@example.com"));return null;}));
        long nombre=Transactions.executer(emf,em->em.createQuery("SELECT COUNT(u) FROM Utilisateur u",Long.class).getSingleResult());
        assertEquals(3L,nombre);
    }
    @Test void creationConcurrenteUnSeulCreneauAccepte() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        Callable<Boolean> tache=()->{
            ready.countDown();
            if(!start.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Delai de synchronisation");
            try { service.creer(salle,user,debut,debut.plusHours(1)); return true; }
            catch(ConflitReservationException attendu) { return false; }
        };
        try {
            Future<Boolean> a=pool.submit(tache), b=pool.submit(tache);
            assertTrue(ready.await(5,TimeUnit.SECONDS)); start.countDown();
            int succes=(a.get(15,TimeUnit.SECONDS)?1:0)+(b.get(15,TimeUnit.SECONDS)?1:0);
            assertEquals(1,succes); assertEquals(16,service.lister().size());
        } finally { start.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS)); }
    }
    @Test void versionDetecteAussiUnConflitAuCommitJpa() {
        Reservation r=service.creer(salle,user,debut,debut.plusHours(1));
        EntityManager a=emf.createEntityManager(), b=emf.createEntityManager();
        try {
            a.getTransaction().begin(); b.getTransaction().begin();
            Reservation ra=a.find(Reservation.class,r.getId()), rb=b.find(Reservation.class,r.getId());
            ra.setDateFin(debut.plusHours(2)); rb.setDateFin(debut.plusHours(3));
            a.getTransaction().commit();
            RuntimeException erreur=assertThrows(RuntimeException.class,()->b.getTransaction().commit());
            Throwable cause=erreur;
            while(cause!=null && !(cause instanceof OptimisticLockException)) cause=cause.getCause();
            assertNotNull(cause);
        } finally {
            if(a.getTransaction().isActive()) a.getTransaction().rollback();
            if(b.getTransaction().isActive()) b.getTransaction().rollback();
            a.close(); b.close();
        }
    }
    @Test void cachePartageEtCoherentApresModification() {
        SessionFactory sf=emf.unwrap(SessionFactory.class); sf.getCache().evictAllRegions();
        Transactions.executer(emf,em->em.find(Salle.class,salle).getNom());
        Statistics stats=sf.getStatistics(); stats.clear();
        assertEquals("Atlas",Transactions.executer(emf,em->em.find(Salle.class,salle).getNom()));
        assertEquals(0,stats.getPrepareStatementCount()); assertEquals(1,stats.getSecondLevelCacheHitCount());
        Transactions.executer(emf,em->{em.find(Salle.class,salle).setDescription("Description modifiee");return null;});
        assertEquals("Description modifiee",Transactions.executer(emf,em->em.find(Salle.class,salle).getDescription()));
    }
    @Test void rapportCompteSqlEtCacheDansDesContextesDistincts() throws Exception {
        new PerformanceReport(emf).printReport();
        List<String> lignes=Files.readAllLines(Paths.get("target","rapport-performance.csv"));
        assertEquals(8,lignes.size());
        assertEquals("9",lignes.get(1).split(";")[1]); // 1 + 5 salles + 3 utilisateurs.
        assertEquals("1",lignes.get(2).split(";")[1]);
        assertEquals("1",lignes.get(3).split(";")[1]);
        assertEquals("0",lignes.get(5).split(";")[1]);
        assertEquals("0",lignes.get(7).split(";")[1]);
    }
}
