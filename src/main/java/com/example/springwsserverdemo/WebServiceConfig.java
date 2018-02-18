package com.example.springwsserverdemo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.wsdl.WsdlDefinition;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

@Configuration
public class WebServiceConfig {

    @Bean(name = "countries")
    public WsdlDefinition countriesWsdlDefinition(XsdSchema countriesSchema) {
        DefaultWsdl11Definition wsdlDefinition = new DefaultWsdl11Definition();
        wsdlDefinition.setPortTypeName("CountriesPort");
        wsdlDefinition.setLocationUri("/services");
        wsdlDefinition.setTargetNamespace(CountryEndpoint.NAMESPACE_URI);
        wsdlDefinition.setSchema(countriesSchema);
        return wsdlDefinition;
    }

    @Bean
    public XsdSchema countriesSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/countries.xsd"));
    }

}
