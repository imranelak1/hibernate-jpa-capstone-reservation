package com.example.util;

import javax.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

public final class PerformanceReport {
    private final Statistics statistics;
    public PerformanceReport(EntityManagerFactory factory){statistics=factory.unwrap(SessionFactory.class).getStatistics();}
    public void printReport(){
        System.out.println("=== RAPPORT DE PERFORMANCE (statistiques réelles) ===");
        System.out.println("Requêtes JPQL/Criteria : "+statistics.getQueryExecutionCount());
        System.out.println("Instructions JDBC préparées : "+statistics.getPrepareStatementCount());
        System.out.println("Cache L2 hits / misses / puts : "+statistics.getSecondLevelCacheHitCount()+" / "+statistics.getSecondLevelCacheMissCount()+" / "+statistics.getSecondLevelCachePutCount());
        System.out.println("Cache requêtes hits : "+statistics.getQueryCacheHitCount());
        System.out.println("Transactions : "+statistics.getTransactionCount());
        long total=java.util.Arrays.stream(statistics.getQueries()).mapToLong(query->statistics.getQueryStatistics(query).getExecutionTotalTime()).sum();
        System.out.println("Durée cumulée/max des requêtes (ms) : "+total+" / "+statistics.getQueryExecutionMaxTime());
    }
}
