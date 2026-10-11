package com.example.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import org.h2.tools.RunScript;

/** Migrations explicites avant la validation du schéma par Hibernate. */
public final class MigrationExecutor {
    private final String jdbcUrl,username,password;
    public MigrationExecutor(String jdbcUrl,String username,String password){this.jdbcUrl=jdbcUrl;this.username=username;this.password=password;}
    public void migrate() throws SQLException,IOException {
        executeMigration("db/migration/V1__initial_schema.sql");
        executeMigration("db/migration/V2__add_description.sql");
    }
    public void executeMigration(String scriptPath) throws SQLException,IOException {
        InputStream input=getClass().getClassLoader().getResourceAsStream(scriptPath);
        if(input==null) throw new FileNotFoundException("Migration absente : "+scriptPath);
        try(input; Connection connection=DriverManager.getConnection(jdbcUrl,username,password);Reader reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            try(Statement statement=connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations (script VARCHAR(255) PRIMARY KEY, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
            }
            try(PreparedStatement check=connection.prepareStatement("SELECT COUNT(*) FROM schema_migrations WHERE script=?")) {
                check.setString(1,scriptPath);
                try(ResultSet rows=check.executeQuery()){rows.next();if(rows.getInt(1)>0)return;}
            }
            // RunScript traite réellement chaque instruction, y compris commentaires.
            // H2 valide ses DDL implicitement; les scripts sont donc réexécutables.
            RunScript.execute(connection,reader);
            try(PreparedStatement record=connection.prepareStatement("INSERT INTO schema_migrations(script) VALUES (?)")){record.setString(1,scriptPath);record.executeUpdate();}
        }
    }
}
