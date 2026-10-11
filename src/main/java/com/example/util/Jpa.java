package com.example.util;

import java.util.*;
import java.util.function.Function;
import javax.persistence.*;

public final class Jpa {
    private Jpa() {}
    public static EntityManagerFactory open(String jdbcUrl) throws Exception {
        new MigrationExecutor(jdbcUrl,"sa","").migrate();
        Map<String,Object> options=new HashMap<>();
        options.put("javax.persistence.jdbc.url",jdbcUrl);
        options.put("hibernate.javax.cache.uri",Objects.requireNonNull(Jpa.class.getResource("/ehcache.xml")).toURI().toString());
        return Persistence.createEntityManagerFactory("ReservationPU",options);
    }
    public static <T> T transaction(EntityManagerFactory factory,Function<EntityManager,T> work) {
        EntityManager em=factory.createEntityManager();
        try {
            em.getTransaction().begin(); T result=work.apply(em);em.getTransaction().commit();return result;
        } catch(RuntimeException error) {
            if(em.getTransaction().isActive())em.getTransaction().rollback();throw error;
        } finally {em.close();}
    }
}
