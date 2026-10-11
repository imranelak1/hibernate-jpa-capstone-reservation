package com.example;

import com.example.model.*;
import com.example.service.ReservationService;
import com.example.util.*;
import java.sql.*;
import java.time.LocalDateTime;
import javax.persistence.*;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

public final class MainApp {
    public static void main(String[] args) throws Exception {
        String url="jdbc:h2:mem:capstone_demo;DB_CLOSE_DELAY=-1";
        EntityManagerFactory factory=Jpa.open(url);
        try {
            DataInitializer.Seed seed=new DataInitializer(factory).initData();ReservationService service=new ReservationService(factory);
            System.out.println("CAPSTONE — Réservation de salles / Java 17 / JPA 2.2");
            try(Connection connection=DriverManager.getConnection(url,"sa","");Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("SELECT COUNT(*) FROM schema_migrations")){rows.next();System.out.println("Migrations appliquées : "+rows.getInt(1)+" (V1 + V2, schéma validé)");}
            System.out.println("Données : "+seed.utilisateurs().size()+" utilisateurs, "+seed.salles().size()+" salles, "+seed.reservations().size()+" réservations");
            LocalDateTime start=seed.base().plusDays(3);Long id=service.createReservation(seed.salles().get(0),seed.utilisateurs().get(0),start,start.plusHours(1));
            service.modifyReservation(id,0,start.plusHours(1),start.plusHours(2));
            System.out.println("CRUD : création "+id+", modification version 0 -> 1");
            try{service.modifyReservation(id,0,start.plusHours(2),start.plusHours(3));throw new AssertionError("Version périmée acceptée");}catch(OptimisticLockException expected){System.out.println("Verrouillage optimiste : version périmée rejetée");}
            try{service.createReservation(seed.salles().get(0),seed.utilisateurs().get(1),start.plusHours(1),start.plusHours(2));throw new AssertionError("Chevauchement accepté");}catch(IllegalStateException expected){System.out.println("Réservation en conflit : rejetée, transaction annulée");}
            int jpql=service.findBySalleAndDate("Salle A",seed.base().minusHours(1)).size();int criteria=service.search("Salle A",seed.base().minusHours(1)).size();
            if(jpql!=criteria)throw new AssertionError("Résultats des requêtes différents");System.out.println("JPQL / Criteria : "+jpql+" / "+criteria+" résultats identiques");
            service.deleteReservation(id);System.out.println("CRUD : suppression vérifiée, reste "+service.findAllWithGraph().size()+" réservations");
            Statistics stats=factory.unwrap(SessionFactory.class).getStatistics();factory.getCache().evictAll();stats.clear();
            Jpa.transaction(factory,em->em.find(Salle.class,seed.salles().get(0)));long cold=stats.getPrepareStatementCount();
            Jpa.transaction(factory,em->em.find(Salle.class,seed.salles().get(0)));long hot=stats.getPrepareStatementCount()-cold;
            if(cold!=1||hot!=0||stats.getSecondLevelCacheHitCount()<1)throw new AssertionError("Cache L2 non utilisé");
            System.out.println("Cache L2 : première session "+cold+" SQL, seconde session "+hot+" SQL, hits="+stats.getSecondLevelCacheHitCount());
            service.cachedRoomIds();long before=stats.getPrepareStatementCount();service.cachedRoomIds();long cached=stats.getPrepareStatementCount()-before;
            if(cached!=0||stats.getQueryCacheHitCount()<1)throw new AssertionError("Cache de requêtes non utilisé");
            System.out.println("Cache de requêtes : deuxième appel "+cached+" SQL, hits="+stats.getQueryCacheHitCount());
            service.findAllWithGraph();new PerformanceReport(factory).printReport();
            System.out.println("RÉSULTAT : tous les scénarios de la démonstration réussis");
        } finally {factory.close();}
    }
}
