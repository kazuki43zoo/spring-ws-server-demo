package com.example.springwsserverdemo;

import com.example.springwsserverdemo.services.country.GetCountryRequest;
import com.example.springwsserverdemo.services.country.GetCountryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
public class CountryEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(CountryEndpoint.class);

    public static final String NAMESPACE_URI = "http://example.com/springwsserverdemo/services/country";

    private final CountryRepository countryRepository;

    public CountryEndpoint(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getCountryRequest")
    @ResponsePayload
    public GetCountryResponse getCountry(@RequestPayload GetCountryRequest request) {
        GetCountryResponse response = new GetCountryResponse();
        logger.info("Received country name : " + request.getName());
        response.setCountry(countryRepository.findCountry(request.getName()));
        return response;
    }

}