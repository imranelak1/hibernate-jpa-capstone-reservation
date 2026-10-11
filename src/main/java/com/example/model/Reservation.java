package com.example.model;

import java.time.LocalDateTime;
import javax.persistence.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity @Table(name="reservations") @Cacheable @Cache(usage=CacheConcurrencyStrategy.READ_WRITE)
@NamedEntityGraph(name="Reservation.details",attributeNodes={@NamedAttributeNode("salle"),@NamedAttributeNode("utilisateur")})
public class Reservation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="salle_id",nullable=false) private Salle salle;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="utilisateur_id",nullable=false) private Utilisateur utilisateur;
    @Column(name="date_debut",nullable=false) private LocalDateTime dateDebut;
    @Column(name="date_fin",nullable=false) private LocalDateTime dateFin;
    @Version private int version;
    public Reservation() {}
    public Reservation(Salle salle,Utilisateur utilisateur,LocalDateTime debut,LocalDateTime fin){this.salle=salle;this.utilisateur=utilisateur;this.dateDebut=debut;this.dateFin=fin;}
    public Long getId(){return id;} public Salle getSalle(){return salle;} public Utilisateur getUtilisateur(){return utilisateur;} public LocalDateTime getDateDebut(){return dateDebut;} public LocalDateTime getDateFin(){return dateFin;} public int getVersion(){return version;}
    public void setDateDebut(LocalDateTime value){dateDebut=value;} public void setDateFin(LocalDateTime value){dateFin=value;}
}
