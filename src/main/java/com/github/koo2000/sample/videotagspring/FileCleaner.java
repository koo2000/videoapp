package com.github.koo2000.sample.videotagspring;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class FileCleaner {

    @PostConstruct
    @PreDestroy
    public void deleteAll() {
        BlobUtil.cleanTmpFiles();
    }
}
