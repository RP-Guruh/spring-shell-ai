package com.console.demo.commands.ai;

import com.console.demo.design.ColorsDesign;
import com.console.demo.dto.ModelListResponse;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class StatusRunningCommand {
    private final RestClient restClient;
    public StatusRunningCommand(RestClient restClient) {
        this.restClient = restClient;
    }
    @Command(
            name = "status",
            description = "Cek koneksi 9router",
            group = "Router",
            help = "Menampilkan informasi connected/disconnected 9router",
            alias = {"sts"},
            exitStatusExceptionMapper = "myCustomExceptionMapper",
            availabilityProvider = "routerAvailability"
    )
    public String statusRunning() {
        try {
            restClient.get()
                    .uri("/v1/models")
                    .retrieve()
                    .toBodilessEntity();
            return ColorsDesign.gold("Successfully connected to 9Router");
        } catch (HttpClientErrorException.Unauthorized e) {
            return ColorsDesign.red("API key ditolak (401). Periksa router.api-key");
        } catch (HttpStatusCodeException e) {
            return ColorsDesign.red("9Router membalas error " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            return ColorsDesign.red("9Router tidak terjangkau: " + e.getMessage());
        }
    }
}
