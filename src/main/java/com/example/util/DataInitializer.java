package com.example.util;

import com.example.model.*;
import java.time.*;
import java.util.*;
import javax.persistence.EntityManagerFactory;

public final class DataInitializer {
    public record Seed(List<Long> utilisateurs,List<Long> salles,List<Long> reservations,LocalDateTime base) {}
    private final EntityManagerFactory factory;
    public DataInitializer(EntityManagerFactory factory){this.factory=factory;}
    public Seed initData() {
        return Jpa.transaction(factory,em->{
            List<Utilisateur> users=new ArrayList<>();List<Salle> rooms=new ArrayList<>();List<Long> reservations=new ArrayList<>();
            for(int i=0;i<4;i++){Utilisateur user=new Utilisateur(new String[]{"Alice Dupont","Karim Amrani","Sara Benali","Youssef Idrissi"}[i],"user"+i+"@example.com");em.persist(user);users.add(user);}
            for(int i=0;i<3;i++){Salle salle=new Salle("Salle "+(char)('A'+i),20+i*10,"Réunion, formation et visioconférence");em.persist(salle);rooms.add(salle);}
            LocalDateTime base=LocalDate.now().plusDays(1).atTime(9,0);
            for(int i=0;i<6;i++){Reservation reservation=new Reservation(rooms.get(i%3),users.get(i%4),base.plusDays(i/3),base.plusDays(i/3).plusHours(1));em.persist(reservation);reservations.add(reservation.getId());}
            return new Seed(users.stream().map(Utilisateur::getId).toList(),rooms.stream().map(Salle::getId).toList(),List.copyOf(reservations),base);
        });
    }
}
