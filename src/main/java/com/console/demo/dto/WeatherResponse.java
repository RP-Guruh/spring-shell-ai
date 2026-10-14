package com.console.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WeatherResponse(
        CurrentWeather current
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrentWeather(
            double temperature_2m,
            double relative_humidity_2m,
            double wind_speed_10m,
            double precipitation
    ) {}
}