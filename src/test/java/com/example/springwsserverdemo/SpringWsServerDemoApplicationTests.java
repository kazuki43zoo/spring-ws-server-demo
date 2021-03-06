package com.example.springwsserverdemo;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.InterceptingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StreamUtils;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.transport.FaultAwareWebServiceConnection;
import org.springframework.ws.transport.HeadersAwareSenderWebServiceConnection;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.WebServiceMessageSender;
import org.springframework.ws.transport.http.ClientHttpRequestMessageSender;
import services.country.Country;
import services.country.Currency;
import services.country.GetCountryRequest;
import services.country.GetCountryResponse;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {SpringWsServerDemoApplication.class, SpringWsServerDemoApplicationTests.ClientConfig.class})
public class SpringWsServerDemoApplicationTests {

    @Configuration
    static class ClientConfig {

        @Bean
        public Jaxb2Marshaller marshaller() {
            Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
            marshaller.setPackagesToScan("services");
            return marshaller;
        }

        @Bean
        public CountryClient client(Environment environment, WebServiceMessageSender messageSender) {
            CountryClient client = new CountryClient(environment);
            client.setMessageSender(messageSender);
            client.setMarshaller(marshaller());
            client.setUnmarshaller(marshaller());
            return client;
        }

        @Bean
        public WebServiceMessageSender messageSender(Environment environment) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(
                    environment.getProperty("ws.client.connect-timeout", Integer.class, 60000));
            requestFactory.setReadTimeout(
                    environment.getProperty("ws.client.read-timeout", Integer.class, 60000));
            return new ClientHttpRequestMessageSender(
                    new InterceptingClientHttpRequestFactory(
                            new BufferingClientHttpRequestFactory(requestFactory),
                            Collections.singletonList(new MessageLoggingInterceptor(true))
                    )
            );
        }

        @Bean
        public WebServiceMessageSenderTimeoutAspect webServiceMessageSenderTimeoutAspect(Environment environment) {
            return new WebServiceMessageSenderTimeoutAspect();
        }

    }

    @Autowired
    private CountryClient client;

    @Test
    public void getCountry() {

        Country country = client.getCountry("Spain");

        Assertions.assertThat(country).isNotNull();
        Assertions.assertThat(country.getName()).isEqualTo("Spain");
        Assertions.assertThat(country.getCapital()).isEqualTo("Madrid");
        Assertions.assertThat(country.getCurrency()).isEqualTo(Currency.EUR);
        Assertions.assertThat(country.getPopulation()).isEqualTo(46704314);

    }

    public static class CountryClient extends WebServiceGatewaySupport {

        private static final Logger logger = LoggerFactory.getLogger(CountryClient.class);

        private final Environment environment;

        private CountryClient(Environment environment) {
            this.environment = environment;
        }

        private Country getCountry(String name) {

            GetCountryRequest request = new GetCountryRequest();
            request.setName(name);

            logger.info("Requesting name for " + name);

            GetCountryResponse response = (GetCountryResponse) getWebServiceTemplate()
                    .marshalSendAndReceive(
                            "http://localhost:" + this.environment.getProperty("local.server.port", "8080") + "/services",
                            request);

            return response.getCountry();
        }

    }

    static class MessageLoggingInterceptor implements ClientHttpRequestInterceptor {

        private static final String PACKAGE_NAME = MessageLoggingInterceptor.class.getPackage().getName();
        private static final Logger reqMessageLogger = LoggerFactory.getLogger(PACKAGE_NAME + ".clientReqLog");
        private static final Logger resMessageLogger = LoggerFactory.getLogger(PACKAGE_NAME + ".clientResLog");

        private final boolean loggingResponseBody;

        private MessageLoggingInterceptor(boolean loggingResponseBody) {
            this.loggingResponseBody = loggingResponseBody;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

            if (reqMessageLogger.isInfoEnabled()) {
                String requestBody = new String(body, StandardCharsets.UTF_8);
                reqMessageLogger.info("Request to {} {}", request.getMethod(), request.getURI());
                reqMessageLogger.info("Request Header {}", request.getHeaders());
                reqMessageLogger.info("Request Body {}", requestBody);
            }

            ClientHttpResponse response = execution.execute(request, body);

            if (resMessageLogger.isInfoEnabled()) {
                resMessageLogger.info("Response Status {}", response.getStatusCode());
                resMessageLogger.info("Response Header {}", response.getHeaders());
                if (this.loggingResponseBody) {
                    resMessageLogger.info("Response Body {}", StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8));
                }
            }

            return response;
        }

    }

    @Aspect
    public static class WebServiceMessageSenderTimeoutAspect {

        @Around(value = "execution(org.springframework.ws.transport.WebServiceConnection org.springframework.ws.transport.WebServiceMessageSender.createConnection(..))")
        public Object configureTimeoutHandling(ProceedingJoinPoint jp) throws Throwable {
            Object result = jp.proceed();
            return Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{HeadersAwareSenderWebServiceConnection.class, WebServiceConnection.class, FaultAwareWebServiceConnection.class},
                    (proxy, method, args) -> {
                        try {
                            return method.invoke(result, args);
                        } catch (InvocationTargetException e) {
                            if (e.getCause() instanceof SocketTimeoutException) {
                                if (method.getName().equals("send")) {
                                    throw new ConnectTimeoutException(e.getCause());
                                } else {
                                    throw new ReadTimeoutException(e.getCause());
                                }
                            }
                            throw e;
                        }
                    });
        }

    }

    static class ConnectTimeoutException extends RuntimeException {
        private ConnectTimeoutException(Throwable e) {
            super(e);
        }
    }

    static class ReadTimeoutException extends RuntimeException {
        private ReadTimeoutException(Throwable e) {
            super(e);
        }
    }

}

