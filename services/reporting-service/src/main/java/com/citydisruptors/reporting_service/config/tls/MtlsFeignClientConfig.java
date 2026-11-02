package com.citydisruptors.reporting_service.config.tls;

import feign.Client;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.loadbalancer.LoadBalancerFeignRequestTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLSocketFactory;

/**
 * Feign calls to other services over mutual TLS.
 * Replaces the default load-balanced Feign client with one that presents this service's
 * certificate and trusts only the internal CA, both taken from the "mtls" SSL bundle.
 * Instances registered in Eureka with a secure port are called over https automatically.
 */
@Configuration
@ConditionalOnProperty(name = "app.mtls.enabled", havingValue = "true")
public class MtlsFeignClientConfig {

    static final String SSL_BUNDLE = "mtls";

    @Bean
    public Client feignClient(
            SslBundles sslBundles,
            LoadBalancerClient loadBalancerClient,
            LoadBalancerClientFactory loadBalancerClientFactory,
            ObjectProvider<LoadBalancerFeignRequestTransformer> transformers
    ) {
        SSLSocketFactory socketFactory = sslBundles.getBundle(SSL_BUNDLE)
                .createSslContext()
                .getSocketFactory();

        return new FeignBlockingLoadBalancerClient(
                new Client.Default(socketFactory, null),
                loadBalancerClient,
                loadBalancerClientFactory,
                transformers.orderedStream().toList()
        );
    }
}
