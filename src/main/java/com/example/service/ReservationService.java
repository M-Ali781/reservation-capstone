package com.example.service;

import com.example.model.*;
import com.example.util.Transactions;
import javax.persistence.*;
import javax.persistence.criteria.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

public class ReservationService {
    private static final Logger LOG = Logger.getLogger(ReservationService.class.getName());
    private final EntityManagerFactory emf;
    public ReservationService(EntityManagerFactory emf) { this.emf=emf; }
    private static void intervalle(LocalDateTime debut,LocalDateTime fin) {
        if (debut==null || fin==null || !debut.isBefore(fin)) throw new IllegalArgumentException("Le debut doit preceder la fin");
    }
    private Salle verrouillerSalle(EntityManager em, Long id) {
        // Toutes les creations/modifications passent par ce verrou, meme si aucune reservation n'existe encore.
        Salle salle=em.find(Salle.class, Objects.requireNonNull(id), LockModeType.PESSIMISTIC_WRITE);
        if(salle==null) throw new IllegalArgumentException("Salle introuvable");
        return salle;
    }
    private void verifierDisponibilite(EntityManager em,Long salle,LocalDateTime debut,LocalDateTime fin,Long exclure) {
        String jpql="SELECT COUNT(r) FROM Reservation r WHERE r.salle.id=:salle AND r.dateDebut<:fin AND r.dateFin>:debut";
        if(exclure!=null) jpql+=" AND r.id<>:id";
        TypedQuery<Long> query=em.createQuery(jpql,Long.class).setParameter("salle",salle).setParameter("debut",debut).setParameter("fin",fin);
        if(exclure!=null) query.setParameter("id",exclure);
        if(query.getSingleResult()>0) throw new ConflitReservationException();
    }
    public Reservation creer(Long salleId,Long utilisateurId,LocalDateTime debut,LocalDateTime fin) {
        Reservation result=Transactions.executer(emf,em -> {
            intervalle(debut,fin);
            Salle salle=verrouillerSalle(em,salleId);
            Utilisateur user=em.find(Utilisateur.class,Objects.requireNonNull(utilisateurId));
            if(user==null) throw new IllegalArgumentException("Utilisateur introuvable");
            verifierDisponibilite(em,salleId,debut,fin,null);
            Reservation r=new Reservation(salle,user,debut,fin);
            em.persist(r);
            return r;
        });
        LOG.info("Reservation creee : " + result.getId());
        return result;
    }
    public Reservation modifier(Long id,int versionAttendue,LocalDateTime debut,LocalDateTime fin) {
        Reservation result=Transactions.executer(emf,em -> {
            intervalle(debut,fin);
            Long salleId=em.createQuery("SELECT r.salle.id FROM Reservation r WHERE r.id=:id",Long.class).setParameter("id",id).getResultStream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Reservation introuvable"));
            verrouillerSalle(em,salleId);
            Reservation r=em.find(Reservation.class,id);
            if(r==null) throw new IllegalArgumentException("Reservation supprimee");
            if(r.getVersion()!=versionAttendue) throw new OptimisticLockException("Version obsolete : rechargez la reservation");
            verifierDisponibilite(em,salleId,debut,fin,id);
            r.setDateDebut(debut); r.setDateFin(fin);
            r.getUtilisateur().getNom();
            return r;
        });
        LOG.info("Reservation modifiee : " + id);
        return result;
    }
    public void supprimer(Long id,int versionAttendue) {
        Transactions.executer(emf,em -> {
            Reservation r=em.find(Reservation.class,id);
            if(r==null) throw new IllegalArgumentException("Reservation introuvable");
            if(r.getVersion()!=versionAttendue) throw new OptimisticLockException("Version obsolete");
            em.remove(r); return null;
        });
        LOG.info("Reservation supprimee : " + id);
    }
    public List<Reservation> lister() {
        return Transactions.executer(emf,em -> em.createQuery("SELECT r FROM Reservation r JOIN FETCH r.salle JOIN FETCH r.utilisateur ORDER BY r.id",Reservation.class).getResultList());
    }
    public List<Reservation> rechercherJpql(String nomSalle,LocalDateTime apres) {
        return Transactions.executer(emf,em -> em.createQuery("SELECT r FROM Reservation r JOIN FETCH r.salle s JOIN FETCH r.utilisateur WHERE s.nom=:nom AND r.dateDebut>:date ORDER BY r.dateDebut, r.id",Reservation.class)
                .setParameter("nom",nomSalle).setParameter("date",apres).getResultList());
    }
    public List<Reservation> rechercherCriteria(String nomSalle,LocalDateTime apres) {
        return Transactions.executer(emf,em -> {
            CriteriaBuilder cb=em.getCriteriaBuilder();
            CriteriaQuery<Reservation> cq=cb.createQuery(Reservation.class);
            Root<Reservation> r=cq.from(Reservation.class);
            r.fetch("salle"); r.fetch("utilisateur");
            List<Predicate> predicates=new ArrayList<>();
            if(nomSalle!=null && !nomSalle.trim().isEmpty()) predicates.add(cb.equal(r.get("salle").get("nom"),nomSalle));
            if(apres!=null) predicates.add(cb.greaterThan(r.get("dateDebut"),apres));
            cq.select(r).where(predicates.toArray(new Predicate[0])).orderBy(cb.asc(r.get("dateDebut")),cb.asc(r.get("id")));
            return em.createQuery(cq).getResultList();
        });
    }
    public List<Salle> disponibles(LocalDateTime debut,LocalDateTime fin) {
        intervalle(debut,fin);
        return Transactions.executer(emf,em -> em.createQuery("SELECT s FROM Salle s WHERE NOT EXISTS (SELECT r.id FROM Reservation r WHERE r.salle=s AND r.dateDebut<:fin AND r.dateFin>:debut) ORDER BY s.nom",Salle.class)
                .setParameter("debut",debut).setParameter("fin",fin).getResultList());
    }
}
