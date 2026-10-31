package com.example.util;
import javax.persistence.*;
import java.util.function.Function;
import java.util.logging.Logger;

public final class Transactions {
    private static final Logger LOG = Logger.getLogger(Transactions.class.getName());
    private Transactions() { }
    public static <T> T executer(EntityManagerFactory emf, Function<EntityManager,T> action) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            T result = action.apply(em);
            em.getTransaction().commit();
            return result;
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            LOG.warning("Transaction annulee : " + e.getClass().getSimpleName() + " - " + e.getMessage());
            for (Throwable cause = e; cause != null; cause = cause.getCause())
                if (cause instanceof OptimisticLockException) throw (OptimisticLockException) cause;
            throw e;
        } finally { em.close(); }
    }
}
