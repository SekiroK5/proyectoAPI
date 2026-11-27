package com.proyecto.servicios;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class App implements CommandLineRunner {

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ObjectProvider<BuildProperties> buildProperties;

    public App(ObjectProvider<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    /** Al arrancar registra la version del artefacto (si el build genero build-info). */
    @Override
    public void run(String... args) {
        buildProperties.ifAvailable(info -> log.info("Artefacto {}:{} version {} compilado {}",
                info.getGroup(), info.getArtifact(), info.getVersion(),
                info.getTime() != null ? FORMATO_FECHA.format(info.getTime()) : "sin fecha"));
    }
}
