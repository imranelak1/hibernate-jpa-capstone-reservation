package com.example;

import com.example.model.*;
import com.example.service.ReservationService;
import com.example.util.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.persistence.*;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ReservationTest {
    private EntityManagerFactory factory;private DataInitializer.Seed seed;private ReservationService service;private String url;
    @BeforeEach void init() throws Exception{url="jdbc:h2:mem:test_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";factory=Jpa.open(url);seed=new DataInitializer(factory).initData();service=new ReservationService(factory);}
    @AfterEach void close() throws Exception{if(factory!=null)factory.close();try(Connection c=DriverManager.getConnection(url,"sa","");Statement s=c.createStatement()){s.execute("SHUTDOWN");}}
    @Test void migrationsAreAppliedOnceAndDescriptionExists() throws Exception {
        new MigrationExecutor(url,"sa","").migrate();
        try(Connection c=DriverManager.getConnection(url,"sa","");Statement s=c.createStatement();ResultSet rows=s.executeQuery("SELECT COUNT(*) FROM schema_migrations")){rows.next();assertEquals(2,rows.getInt(1));}
        assertNotNull(Jpa.transaction(factory,em->em.find(Salle.class,seed.salles().get(0)).getDescription()));
    }
    @Test void createModifyAndDeleteReservation(){
        LocalDateTime start=seed.base().plusDays(4);Long id=service.createReservation(seed.salles().get(0),seed.utilisateurs().get(0),start,start.plusHours(1));
        service.modifyReservation(id,0,start.plusHours(1),start.plusHours(2));
        Reservation saved=Jpa.transaction(factory,em->em.find(Reservation.class,id));assertEquals(1,saved.getVersion());assertEquals(start.plusHours(1),saved.getDateDebut());
        service.deleteReservation(id);assertNull(Jpa.transaction(factory,em->em.find(Reservation.class,id)));
    }
    @Test void overlapIsRejectedAndRollbackPreservesData(){
        assertThrows(IllegalStateException.class,()->service.createReservation(seed.salles().get(0),seed.utilisateurs().get(0),seed.base().plusMinutes(15),seed.base().plusHours(2)));
        assertEquals(6,service.findAllWithGraph().size());
    }
    @Test void adjacentReservationIsAllowed(){assertNotNull(service.createReservation(seed.salles().get(0),seed.utilisateurs().get(0),seed.base().plusHours(1),seed.base().plusHours(2)));}
    @Test void invalidDatesAndUnknownUserAreRejected(){
        assertThrows(IllegalArgumentException.class,()->service.createReservation(seed.salles().get(0),seed.utilisateurs().get(0),seed.base(),seed.base()));
        assertThrows(IllegalArgumentException.class,()->service.createReservation(seed.salles().get(0),9999L,seed.base().plusDays(4),seed.base().plusDays(4).plusHours(1)));
        assertEquals(6,service.findAllWithGraph().size());
    }
    @Test void duplicateEmailIsRejectedByDatabase(){
        assertThrows(PersistenceException.class,()->Jpa.transaction(factory,em->{em.persist(new Utilisateur("Duplicata","user0@example.com"));return null;}));
        Long count=Jpa.transaction(factory,em->em.createQuery("SELECT COUNT(u) FROM Utilisateur u",Long.class).getSingleResult());assertEquals(4L,count.longValue());
    }
    @Test void jpqlAndCriteriaAgreeAndGraphLoadsDetachedRelations(){
        List<Long> jpql=service.findBySalleAndDate("Salle A",seed.base().minusHours(1)).stream().map(Reservation::getId).toList();
        List<Long> criteria=service.search("Salle A",seed.base().minusHours(1)).stream().map(Reservation::getId).toList();assertEquals(jpql,criteria);assertEquals(2,jpql.size());
        for(Reservation r:service.findAllWithGraph()){assertNotNull(r.getSalle().getNom());assertNotNull(r.getUtilisateur().getEmail());}
    }
    @Test void secondLevelCacheIsSharedAcrossEntityManagers(){
        Statistics stats=factory.unwrap(SessionFactory.class).getStatistics();factory.getCache().evictAll();stats.clear();
        Jpa.transaction(factory,em->em.find(Salle.class,seed.salles().get(0)));assertEquals(1,stats.getPrepareStatementCount());
        Jpa.transaction(factory,em->em.find(Salle.class,seed.salles().get(0)));assertEquals(1,stats.getPrepareStatementCount());assertTrue(stats.getSecondLevelCacheHitCount()>0);
    }
    @Test void staleDetachedUpdateFailsAndDoesNotOverwriteWinner(){
        EntityManager first=factory.createEntityManager(),second=factory.createEntityManager();
        try {
            first.getTransaction().begin();second.getTransaction().begin();
            Reservation a=first.find(Reservation.class,seed.reservations().get(0)),b=second.find(Reservation.class,seed.reservations().get(0));
            a.setDateFin(a.getDateFin().plusMinutes(15));first.getTransaction().commit();b.setDateFin(b.getDateFin().plusMinutes(30));
            PersistenceException conflict=assertThrows(PersistenceException.class,()->second.getTransaction().commit());
            boolean optimistic=false;for(Throwable cause=conflict;cause!=null;cause=cause.getCause())if(cause instanceof OptimisticLockException)optimistic=true;
            assertTrue(optimistic,"Le conflit doit provenir du verrouillage optimiste");
            Reservation winner=Jpa.transaction(factory,em->em.find(Reservation.class,a.getId()));assertEquals(seed.base().plusMinutes(75),winner.getDateFin());assertEquals(1,winner.getVersion());
        } finally {if(first.getTransaction().isActive())first.getTransaction().rollback();if(second.getTransaction().isActive())second.getTransaction().rollback();first.close();second.close();}
    }
    @Test void queryCacheReusesResultAcrossSessions(){
        Statistics stats=factory.unwrap(SessionFactory.class).getStatistics();stats.clear();factory.unwrap(SessionFactory.class).getCache().evictQueryRegions();
        assertEquals(3,service.cachedRoomIds().size());long first=stats.getPrepareStatementCount();assertEquals(1,first);
        assertEquals(3,service.cachedRoomIds().size());assertEquals(first,stats.getPrepareStatementCount());assertEquals(1,stats.getQueryCacheHitCount());
    }
    @Test void twoConcurrentCreationsCannotBookSameRoom() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);LocalDateTime window=seed.base().plusDays(5);
        Callable<Boolean> create=()->{ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Synchronisation expirée");try{service.createReservation(seed.salles().get(0),seed.utilisateurs().get(0),window,window.plusHours(1));return true;}catch(IllegalStateException expected){return false;}};
        try{Future<Boolean> first=pool.submit(create),second=pool.submit(create);assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();assertNotEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));assertEquals(7,service.findAllWithGraph().size());}finally{start.countDown();pool.shutdownNow();}
    }
    @Test void missingMigrationFailsClearly(){assertThrows(java.io.FileNotFoundException.class,()->new MigrationExecutor(url,"sa","").executeMigration("absent.sql"));}
}
