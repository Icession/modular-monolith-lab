package edu.cit.carcueva.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.cit.carcueva.AppInstance;
import edu.cit.carcueva.inventory.InventoryItemView;
import edu.cit.carcueva.inventory.InventoryService;
import edu.cit.carcueva.supplier.SupplierGateway;

@Component
class GoLive {
    private static final Logger log = LoggerFactory.getLogger(GoLive.class);

    private final TianggeClient client;
    private final ChannelState state;
    private final StockPublisher stockPublisher;
    private final ListedProducts listed;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final AppInstance appInstance;

    GoLive(TianggeClient client, ChannelState state, StockPublisher stockPublisher, ListedProducts listed,
           InventoryService inventoryService, SupplierGateway supplierGateway, AppInstance appInstance) {
        this.client = client;
        this.state = state;
        this.stockPublisher = stockPublisher;
        this.listed = listed;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.appInstance = appInstance;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void goLive() {
        log.info("[Channel] Going live as instance {}", appInstance.id());
        while (!state.isLive() && !Thread.currentThread().isInterrupted()) {
            try {
                client.heartbeat();
                state.heartbeatOk();
                log.info("[Channel] First heartbeat accepted");

                List<TianggeClient.Listing> listings = buildListings();
                client.publishListings(listings);
                state.markListingsPublished();
                log.info("[Channel] Published {} listings: {}", listings.size(), listings);

                stockPublisher.publishAll();
                state.markLive();
                log.info("[Channel] Shop is LIVE - reading the order feed now");
            } catch (TianggeException e) {
                log.warn("[Channel] Go-live failed, retrying in 10s: {}", e.describe());
                sleep(10000);
            } catch (RuntimeException e) {
                log.error("[Channel] Go-live failed, retrying in 10s", e);
                sleep(10000);
            }
        }
    }

    @Scheduled(fixedRateString = "${channel.heartbeat-interval-ms:30000}", initialDelay = 30000)
    public void heartbeat() {
        if (!state.firstHeartbeatSent()) {
            return;
        }
        try {
            client.heartbeat();
            state.heartbeatOk();
        } catch (TianggeException e) {
            log.warn("[Channel] Heartbeat failed: {}", e.describe());
        }
    }

    private List<TianggeClient.Listing> buildListings() {
        List<TianggeClient.Listing> listings = new ArrayList<>();
        for (String productId : listed.all()) {
            Optional<InventoryItemView> item = inventoryService.getItem(productId);
            Optional<String> supplierSku = supplierGateway.supplierSkuFor(productId);
            if (item.isEmpty() || supplierSku.isEmpty()) {
                log.warn("[Channel] Not listing {} (inventory item or supplier mapping missing)", productId);
                continue;
            }
            listings.add(new TianggeClient.Listing(productId, item.get().name(), supplierSku.get()));
        }
        return listings;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
