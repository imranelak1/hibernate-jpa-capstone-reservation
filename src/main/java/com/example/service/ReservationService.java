package com.example.service;

import com.example.model.*;
import com.example.util.Jpa;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;
import javax.persistence.*;
import javax.persistence.criteria.*;

public final class ReservationService {
    private static final Logger LOGGER=Logger.getLogger(ReservationService.class.getName());
    private final EntityManagerFactory factory;
    public ReservationService(EntityManagerFactory factory){this.factory=factory;}
    public Long createReservation(Long salleId,Long utilisateurId,LocalDateTime debut,LocalDateTime fin) {
        try {
            validateWindow(debut,fin);
            Long id=Jpa.transaction(factory,em->{
                // Une ligne Salle sérialise les créations sur cette salle; @Version
                // seul ne détecterait pas deux INSERT de réservations distinctes.
                Salle room=require(em.find(Salle.class,salleId,LockModeType.PESSIMISTIC_WRITE),"Salle inconnue");
                Utilisateur user=require(em.find(Utilisateur.class,utilisateurId),"Utilisateur inconnu");
                checkAvailable(em,salleId,debut,fin,null);
                Reservation reservation=new Reservation(room,user,debut,fin);em.persist(reservation);return reservation.getId();
            });
            LOGGER.info("Réservation créée : "+id);return id;
        } catch(RuntimeException error){LOGGER.warning("Création refusée : "+error.getMessage());throw error;}
    }
    public void modifyReservation(Long id,int expectedVersion,LocalDateTime debut,LocalDateTime fin) {
        try {
            validateWindow(debut,fin);
            Jpa.transaction(factory,em->{
                Reservation reservation=require(em.find(Reservation.class,id),"Réservation inconnue");
                if(reservation.getVersion()!=expectedVersion)throw new OptimisticLockException("Version périmée : "+expectedVersion);
                em.lock(reservation.getSalle(),LockModeType.PESSIMISTIC_WRITE);
                checkAvailable(em,reservation.getSalle().getId(),debut,fin,id);
                reservation.setDateDebut(debut);reservation.setDateFin(fin);return null;
            });
            LOGGER.info("Réservation modifiée : "+id);
        } catch(RuntimeException error){LOGGER.warning("Modification refusée : "+error.getMessage());throw error;}
    }
    public void deleteReservation(Long id) {
        try {
            Jpa.transaction(factory,em->{Reservation reservation=require(em.find(Reservation.class,id),"Réservation inconnue");em.remove(reservation);return null;});
            LOGGER.info("Réservation supprimée : "+id);
        } catch(RuntimeException error){LOGGER.warning("Suppression refusée : "+error.getMessage());throw error;}
    }
    public List<Reservation> findBySalleAndDate(String nom,LocalDateTime after) {
        return Jpa.transaction(factory,em->em.createQuery("SELECT r FROM Reservation r JOIN FETCH r.salle JOIN FETCH r.utilisateur WHERE r.salle.nom=:nom AND r.dateDebut>:date ORDER BY r.dateDebut,r.id",Reservation.class).setParameter("nom",nom).setParameter("date",after).getResultList());
    }
    public List<Reservation> search(String nom,LocalDateTime after) {
        return Jpa.transaction(factory,em->{
            CriteriaBuilder cb=em.getCriteriaBuilder();CriteriaQuery<Reservation> query=cb.createQuery(Reservation.class);Root<Reservation> root=query.from(Reservation.class);
            root.fetch("salle");root.fetch("utilisateur");List<Predicate> filters=new ArrayList<>();
            if(nom!=null&&!nom.isBlank())filters.add(cb.equal(root.get("salle").get("nom"),nom));
            if(after!=null)filters.add(cb.greaterThan(root.get("dateDebut"),after));
            query.select(root).where(filters.toArray(Predicate[]::new)).orderBy(cb.asc(root.get("dateDebut")),cb.asc(root.get("id")));
            return em.createQuery(query).getResultList();
        });
    }
    public List<Reservation> findAllWithGraph() {
        return Jpa.transaction(factory,em->em.createQuery("SELECT r FROM Reservation r ORDER BY r.id",Reservation.class).setHint("javax.persistence.fetchgraph",em.getEntityGraph("Reservation.details")).getResultList());
    }
    public List<Long> cachedRoomIds() {
        return Jpa.transaction(factory,em->em.createQuery("SELECT s.id FROM Salle s ORDER BY s.id",Long.class).setHint("org.hibernate.cacheable",true).getResultList());
    }
    private static void checkAvailable(EntityManager em,Long salleId,LocalDateTime debut,LocalDateTime fin,Long exclude) {
        Long overlap=em.createQuery("SELECT COUNT(r) FROM Reservation r WHERE r.salle.id=:salle AND r.dateDebut<:fin AND r.dateFin>:debut AND (:excl IS NULL OR r.id<>:excl)",Long.class).setParameter("salle",salleId).setParameter("debut",debut).setParameter("fin",fin).setParameter("excl",exclude).getSingleResult();
        if(overlap>0)throw new IllegalStateException("Créneau déjà réservé");
    }
    private static void validateWindow(LocalDateTime debut,LocalDateTime fin){if(debut==null||fin==null||!fin.isAfter(debut))throw new IllegalArgumentException("La fin doit suivre le début");}
    private static <T> T require(T value,String message){if(value==null)throw new IllegalArgumentException(message);return value;}
}
