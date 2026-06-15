package ru.lukin.edododo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateBeans {

    @Bean(name = "storageRestTemplate")
    public RestTemplate storageRestTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectionRequestTimeout(30000);
        factory.setReadTimeout(120000);
        return new RestTemplate(factory);
    }

    @Bean(name = "sabyRestTemplate")
    public RestTemplate sabyRestTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectionRequestTimeout(30000);
        factory.setReadTimeout(120000);
        return new RestTemplate(factory);
    }
}