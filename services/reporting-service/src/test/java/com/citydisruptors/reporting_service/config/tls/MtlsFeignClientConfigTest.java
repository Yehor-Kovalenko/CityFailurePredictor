package com.citydisruptors.reporting_service.config.tls;

import feign.Client;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import org.springframework.test.util.ReflectionTestUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MtlsFeignClientConfigTest {

    private final SSLContext sslContext = mock(SSLContext.class);
    private final SSLSocketFactory socketFactory = mock(SSLSocketFactory.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MtlsFeignClientConfig.class)
            .withBean(SslBundles.class, this::sslBundles)
            .withBean(LoadBalancerClient.class, () -> mock(LoadBalancerClient.class))
            .withBean(LoadBalancerClientFactory.class, () -> mock(LoadBalancerClientFactory.class));

    private SslBundles sslBundles() {
        SslBundle bundle = mock(SslBundle.class);
        when(bundle.createSslContext()).thenReturn(sslContext);
        when(sslContext.getSocketFactory()).thenReturn(socketFactory);

        SslBundles bundles = mock(SslBundles.class);
        when(bundles.getBundle(MtlsFeignClientConfig.SSL_BUNDLE)).thenReturn(bundle);
        return bundles;
    }

    @Test
    void createsLoadBalancedClientThatUsesTheMtlsBundle() {
        contextRunner
                .withPropertyValues("app.mtls.enabled=true")
                .run(context -> {
                    Client client = context.getBean(Client.class);
                    assertThat(client).isInstanceOf(FeignBlockingLoadBalancerClient.class);

                    Client delegate = ((FeignBlockingLoadBalancerClient) client).getDelegate();
                    assertThat(delegate).isInstanceOf(Client.Default.class);
                    assertThat(ReflectionTestUtils.getField(delegate, "sslContextFactory"))
                            .isSameAs(socketFactory);
                });
    }

    @Test
    void keepsTheDefaultClientWhenMtlsIsDisabled() {
        contextRunner
                .run(context -> assertThat(context).doesNotHaveBean(Client.class));
    }
}
