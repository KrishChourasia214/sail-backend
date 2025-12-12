package com.sail.aws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to configure database settings for Lambda deployment.
 * Converts various database types to H2 in-memory to stay within AWS Free Tier.
 */
@Service
public class LambdaDatabaseConfigurationService {
    
    private static final Logger log = LoggerFactory.getLogger(LambdaDatabaseConfigurationService.class);
    
    public enum DatabaseType {
        H2, MYSQL, POSTGRESQL, MONGODB, MARIADB, ORACLE, SQLSERVER, NONE
    }
    
    /**
     * Returns environment variables for database configuration optimized for free tier.
     * All non-H2 databases are converted to H2 in-memory to avoid RDS costs.
     */
    public Map<String, String> getFreeTierDatabaseConfig(DatabaseType detectedType) {
        Map<String, String> envVars = new HashMap<>();
        
        switch (detectedType) {
            case H2:
                log.info("Project already uses H2 database - using in-memory mode");
                envVars.put("SPRING_DATASOURCE_URL", "jdbc:h2:mem:testdb");
                envVars.put("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.h2.Driver");
                envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "create");
                envVars.put("SPRING_H2_CONSOLE_ENABLED", "false");
                break;
                
            case MYSQL:
                log.info("Converting MySQL to H2 in-memory to stay within AWS free tier");
                envVars.put("SPRING_DATASOURCE_URL", "jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
                envVars.put("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.h2.Driver");
                envVars.put("SPRING_JPA_DATABASE_PLATFORM", "org.hibernate.dialect.H2Dialect");
                envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "create");
                envVars.put("SPRING_H2_CONSOLE_ENABLED", "false");
                envVars.put("SAIL_DB_WARNING", "Database converted from MySQL to H2 for free tier. Data won't persist between Lambda invocations.");
                break;
                
            case POSTGRESQL:
                log.info("Converting PostgreSQL to H2 in-memory to stay within AWS free tier");
                envVars.put("SPRING_DATASOURCE_URL", "jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
                envVars.put("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.h2.Driver");
                envVars.put("SPRING_JPA_DATABASE_PLATFORM", "org.hibernate.dialect.H2Dialect");
                envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "create");
                envVars.put("SPRING_H2_CONSOLE_ENABLED", "false");
                envVars.put("SAIL_DB_WARNING", "Database converted from PostgreSQL to H2 for free tier. Data won't persist between Lambda invocations.");
                break;
                
            case MARIADB:
                log.info("Converting MariaDB to H2 in-memory to stay within AWS free tier");
                envVars.put("SPRING_DATASOURCE_URL", "jdbc:h2:mem:testdb;MODE=MySQL");
                envVars.put("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.h2.Driver");
                envVars.put("SPRING_JPA_DATABASE_PLATFORM", "org.hibernate.dialect.H2Dialect");
                envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "create");
                envVars.put("SAIL_DB_WARNING", "Database converted from MariaDB to H2 for free tier. Data won't persist between Lambda invocations.");
                break;
                
            case MONGODB:
                log.warn("MongoDB detected - cannot convert to H2. Using H2 with warning.");
                envVars.put("SPRING_DATASOURCE_URL", "jdbc:h2:mem:testdb");
                envVars.put("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.h2.Driver");
                envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "create");
                envVars.put("SAIL_DB_WARNING", "MongoDB not supported in free tier demo. Using H2 as fallback.");
                break;
                
            case ORACLE:
            case SQLSERVER:
                log.warn("{} detected - converting to H2 for free tier", detectedType);
                envVars.put("SPRING_DATASOURCE_URL", "jdbc:h2:mem:testdb");
                envVars.put("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.h2.Driver");
                envVars.put("SPRING_JPA_DATABASE_PLATFORM", "org.hibernate.dialect.H2Dialect");
                envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "create");
                envVars.put("SAIL_DB_WARNING", "Database converted from " + detectedType + " to H2 for free tier.");
                break;
                
            case NONE:
            default:
                log.info("No database detected or using default H2 configuration");
                envVars.put("SPRING_DATASOURCE_URL", "jdbc:h2:mem:testdb");
                envVars.put("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.h2.Driver");
                envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "create");
                envVars.put("SPRING_H2_CONSOLE_ENABLED", "false");
        }
        
        return envVars;
    }
}