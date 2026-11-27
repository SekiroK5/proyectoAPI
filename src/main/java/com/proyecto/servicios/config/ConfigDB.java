package com.proyecto.servicios.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Conexion a PostgreSQL (pool Hikari), JPA y transacciones. Si la configuracion falla, el arranque se
 * detiene con el error: no se devuelve un bean nulo que fallaria despues sin una causa clara.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = {
                "com.proyecto.servicios.repository.sf",
                "com.proyecto.servicios.repository.gestopago"
        },
        transactionManagerRef = "sfTransactionManager",
        entityManagerFactoryRef = "sfEntityManagerFactory"
)
public class ConfigDB {

    @Bean(name = "sfDatasource")
    public DataSource sfDatasource(Environment env) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(env.getProperty("spring.datasource.url"));
        config.setPassword(env.getProperty("spring.datasource.password"));
        config.setUsername(env.getProperty("spring.datasource.username"));
        config.setMaximumPoolSize(10);
        config.setMaxLifetime(1_800_000); // 30 min; Hikari ignora valores menores a 30 s
        config.setConnectionTimeout(5000);
        config.setValidationTimeout(5000);
        config.setMinimumIdle(2);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("sfDatasource");
        return new HikariDataSource(config);
    }

    @Bean(name = "sfEntityManagerFactory")
    @DependsOn("flyway")
    public LocalContainerEntityManagerFactoryBean sfEntityManagerFactory(@Qualifier("sfDatasource") DataSource sfDatasource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(sfDatasource);
        em.setPackagesToScan(
                "com.proyecto.servicios.entity.sf",
                "com.proyecto.servicios.entity.gestopago"
        );
        em.setPersistenceUnitName("sfDatasource");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.show_sql", false);
        properties.put("jakarta.persistence.query.timeout", 600_000);
        em.setJpaPropertyMap(properties);
        return em;
    }

    @Bean(name = "sfTransactionManager")
    public PlatformTransactionManager sfTransactionManager(@Qualifier("sfEntityManagerFactory") EntityManagerFactory sfEntityManagerFactory) {
        return new JpaTransactionManager(sfEntityManagerFactory);
    }
}
